package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.BootstrapResp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class MQTTManager(
    private val context: Context,
    private val connectEventListener: IConnectionServer,
    private val serverURI: String,
    private val bootstrapResp: BootstrapResp,
) {

    val TAG = "MQTTLOG"
    private val clientID = "${bootstrapResp.id}"
    private val channels = bootstrapResp.channels
    private val channelId = bootstrapResp.channels?.first()?.id
    private var mqttClient: MqttAndroidClient? = null

    // Track trạng thái đang kết nối để tránh gọi connect() khi đã in-progress (lỗi 32110)
    @Volatile
    private var isConnecting = false
    private var reconnectJob: Job? = null
    private var currentReconnectDelay = 2000L
    private val MAX_RECONNECT_DELAY = 60000L

    init {
        mqttClient = MqttAndroidClient(context, serverURI, clientID)

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
                connectEventListener.onReceiveMessageSuccess(message.toString())
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost: ${cause?.message}")
                cause?.printStackTrace()
                isConnecting = false
                startReconnectLogic()
                // Báo HubManager để trigger reconnect thủ công (tạo lại client mới trên network mới)
                connectEventListener.onConnectFailure(cause?.message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "Connection connectComplete. Reconnect: $reconnect")

                isConnecting = false
                reconnectJob?.cancel()
                currentReconnectDelay = 2000L

                channels?.forEach { subscribe(it.id!!) }
                connectEventListener.onConnectSuccess()
            }
        })
    }

    fun connectToMQTT() {
        if (mqttClient?.isConnected == true) {
            Log.d(TAG, "MQTT is already connected")
            return
        }
        // Guard: nếu đang trong quá trình connect thì bỏ qua, tránh lỗi 32110
        if (isConnecting) {
            Log.d(TAG, "connectToMQTT: already connecting, skip")
            return
        }

        Log.d(TAG, "Attempting to connect with clientID: $clientID")
        isConnecting = true

        val options = MqttConnectOptions()
        options.userName = bootstrapResp.id
        options.password = bootstrapResp.key?.toCharArray()
        options.isAutomaticReconnect = false  // Tắt — tự xử lý để hỗ trợ đổi network interface
        options.isCleanSession = false
        options.connectionTimeout = 30
        options.keepAliveInterval = 30

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // connectComplete callback sẽ xử lý subscribe & notify
                    // isConnecting sẽ được clear trong connectComplete
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception?.message}")
                    exception?.printStackTrace()
                    isConnecting = false
                    startReconnectLogic()
                    connectEventListener.onConnectFailure(exception?.message)
                }
            })
        } catch (e: MqttException) {
            isConnecting = false
            if (e.reasonCode == MqttException.REASON_CODE_CONNECT_IN_PROGRESS.toInt()) {
                // Không thể xảy ra nữa nhờ guard isConnecting ở trên, nhưng để an toàn
                Log.d(TAG, "connectToMQTT: connect already in progress (32110), ignored")
                isConnecting = true // vẫn đang connect

            } else {
                Log.d(TAG, "connectToMQTT MqttException (${e.reasonCode}): ${e.message}")
                startReconnectLogic()
                connectEventListener.onConnectFailure(e.message)
            }
        } catch (e: Exception) {
            isConnecting = false
            startReconnectLogic()
            Log.d(TAG, "connectToMQTT Exception: ${e.message}")
            connectEventListener.onConnectFailure(e.message)
        }
    }

    private fun startReconnectLogic() {
        // Nếu đang chạy vòng lặp reconnect rồi thì bỏ qua, không tạo thêm
        if (reconnectJob?.isActive == true) return

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            // Vòng lặp: Cứ mất kết nối là còn thử lại
            while (mqttClient?.isConnected == false) {
                Log.d(
                    TAG,
                    "Mất kết nối. Đang thử kết nối lại sau ${currentReconnectDelay / 1000} giây..."
                )

                // Tạm dừng trong nền (không block app)
                delay(currentReconnectDelay)

                // Tiến hành gọi lại hàm connect của bạn
                connectToMQTT()

                // Tăng thời gian chờ cho lần sau gấp đôi (Exponential Backoff)
                // Lần lượt sẽ là: 2s -> 4s -> 8s -> 16s -> 32s -> 60s -> 60s...
                currentReconnectDelay =
                    (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            }
        }
    }

    fun publishToMQTT(
        strMessage: String, qos: Int = 0, retained: Boolean = false
    ) {
        if (mqttClient?.isConnected != true) {
            Log.d(TAG, "Cannot publish: MQTT not connected")
            return
        }
        try {
            val message = MqttMessage()
            message.payload = strMessage.toByteArray()
            message.qos = qos
            message.isRetained = retained
            if (mqttClient != null) {
                val publishToServerChannel = "channels/$channelId/messages/mirav3/s"
                mqttClient?.publish(
                    publishToServerChannel, message, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(TAG, "$strMessage = published to $clientID")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?, exception: Throwable?
                        ) {
                            Log.d(
                                TAG,
                                "Failed to publish to $publishToServerChannel - ${exception?.message}"
                            )
                        }
                    })
            }
        } catch (e: Exception) {
            Log.d(TAG, "publishToMQTT: ${e.message}")
        }
    }

    private fun subscribe(channelId: String, qos: Int = 0) {
        try {
            if (mqttClient != null) {
                val downTopic = "channels/$channelId/messages/mirav3/d"
                mqttClient?.subscribe(downTopic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Subscribed to $downTopic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to subscribe $downTopic - ${exception?.message}")
                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "subscribe: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            isConnecting = false
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            Log.d(TAG, "MQTT Disconnected")
        } catch (e: Exception) {
            Log.d(TAG, "MQTT Disconnect error: ${e.message}")
        }
    }

    fun disconnectAll() {

        try {
            isConnecting = false
            reconnectJob?.cancel()
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
        } catch (e: Exception) {
            Log.d(TAG, "MQTT disconnect error (ignored): ${e.message}")
        }
        try {
            // unregisterResources ngắt bind tới MqttService nhưng KHÔNG gọi close().
            // close() đánh dấu connection trong service là closed vĩnh viễn:
            // khi client mới (cùng clientHandle = serverURI+clientID) bind lại
            // sẽ gặp lỗi "Client is closed (32111)".
            mqttClient?.unregisterResources()
            Log.d(TAG, "MQTT Disconnected and unregistered")
        } catch (e: Exception) {
            Log.d(TAG, "MQTT unregisterResources error: ${e.message}")
        }
    }

}
