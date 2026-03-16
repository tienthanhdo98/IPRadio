package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.Channel
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

class MQTTManager(
    private val context: Context,
    private val connectEventListener: IConnectionServer,
    private val thingId: String,
    private val thingKey: String,
    private val channels: List<Channel>,
    private val serverURI: String,

    ) {
    val TAG = "MQTTLOG"
    private val clientID = "$thingId"
    private val channelId = channels.first().id

    private var mqttClient: MqttAndroidClient? = null

    // Bước 1: Khởi tạo Client và Callback ngay khi class được tạo (Chỉ 1 lần duy nhất)
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
                // Lưu ý: Không cần báo fail về HubManager ở đây
                // vì thư viện sẽ tự động reconnect ngầm nhờ isAutomaticReconnect = true.
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "Connection connectComplete. Reconnect: $reconnect")
                channels.forEach { subscribe(it.id!!) } // resubscribe
            }
        })
    }

    fun connectToMQTT() {
        // Bước 2: Kiểm tra nếu client đã kết nối rồi thì không thực hiện connect nữa
        if (mqttClient?.isConnected == true) {
            Log.d(TAG, "MQTT is already connected")
            return
        }

        Log.d(TAG, "Attempting to connect with clientID: $clientID")

        val options = MqttConnectOptions()
        options.userName = thingId
        options.password = thingKey.toCharArray()
        options.isAutomaticReconnect =
            true // Thư viện sẽ tự quản lý reconnect bằng cơ chế backoff thông minh
        options.isCleanSession = true
        options.connectionTimeout = 60
        options.keepAliveInterval = 60

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                    // Do connectComplete đã xử lý subscribe, nhưng gọi thêm ở đây cho lượt connect đầu cũng không sao
                    channels.forEach {
                        subscribe(it.id!!)
                    }
                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception?.message}")
                    exception?.printStackTrace()
                    connectEventListener.onConnectFailure(exception?.message)
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "connectToMQTT Exception: ${e.message}")
        }
    }

    fun publishToMQTT(
        strMessage: String, qos: Int = 0, retained: Boolean = false
    ) {
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

                        connectEventListener.onConnectFailure(exception?.message)

                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "subscribe: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
            // Giải phóng tài nguyên client
            mqttClient?.unregisterResources()
            mqttClient?.close()
            Log.d(TAG, "MQTT Disconnected gracefully")
        } catch (e: Exception) {
            Log.d(TAG, "MQTT Disconnect error: ${e.message}")
        }
    }

}



