package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.utils.ConfigUtil
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTManager(
    private val hubToken: String?,
    private val context: Context,
    private val connectEventListener: IConnectionServer,
    private val listMQTTInfo: List<NoiDungMQTT>,
    val destinationId : String
) {
    val TAG = "MQTTLOG"
    var clientID = ""
    private var publishTopicTinh = ""


    private var mqttClient: MqttAndroidClient? = null

    fun getSubscribeTopic(destinationId: String) {
        subscribe("thietbi/$clientID")
        val listDesId = destinationId.split(".")
        Log.d(TAG, "Subscribed: ${listDesId.count()}")
        if (listDesId.isNotEmpty()) {

            subscribe("tinh/${listDesId[0]}".toLowerCase())
        }
        if (listDesId.count() >= 2) {
            subscribe("huyen/${listDesId[0]}.${listDesId[1]}".toLowerCase())
        }
        if (listDesId.count() >= 3) {
            subscribe("xa/${listDesId[0]}.${listDesId[1]}.${listDesId[2]}".toLowerCase())
        } else {
            subscribe("thon/$destinationId".toLowerCase())
        }
    }

    fun connectToMQTT() {
        listMQTTInfo.forEach {
            if (it.topic?.contains("thietbi") == true) {
                clientID = it.topic.substringAfter("/")
            }
        }
        publishTopicTinh = "tinh/$clientID"
        Log.d(TAG, "clientID ${clientID}")
        val serverURI = ConfigUtil.getStringEncrypt(context, ConfigUtil.URL_MQTT)
        mqttClient = MqttAndroidClient(context, serverURI, clientID)
        mqttClient?.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
                connectEventListener.onReceiveMessageSuccess(message.toString())
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost ${cause}")
                cause?.printStackTrace();
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })
        val options = MqttConnectOptions()
//        options.setMqttVersion(MqttConnectOptions.);
        if (hubToken != null) {
            options.userName = clientID
            options.password = hubToken.toCharArray()
        }
        try {
            if (mqttClient != null) {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                    listMQTTInfo.forEach {
                        subscribe(it.topic!!)
                    }
                    //getSubscribeTopic(destinationId)
                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception!!}")
                    exception.printStackTrace()
                    connectEventListener.onConnectFailure(exception.message)
                }
            })}
        } catch (e: Exception) {
            Log.d(TAG, "connectToMQTT: ${e.message}")
        }
    }

    fun publishToMQTT(strMessage: String, qos: Int = 1, retained: Boolean = false) {
        try {
            val message = MqttMessage()
            message.payload = strMessage.toByteArray()
            message.qos = qos
            message.isRetained = retained
            if (mqttClient != null) {
                mqttClient?.publish(publishTopicTinh, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "$strMessage = published to $clientID")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to publish to $publishTopicTinh - ${exception?.message}")
                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "publishToMQTT: ${e.message}")
        }
    }

    private fun subscribe(subscribeTopic: String, qos: Int = 1) {
        try {
            if (mqttClient != null) {
                mqttClient?.subscribe(subscribeTopic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Subscribed to $subscribeTopic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to subscribe $subscribeTopic - ${exception?.message}")

                        connectEventListener.onConnectFailure(exception?.message)

                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "subscribe: ${e.message}")
        }
    }

    fun disconnectMqtt() {
        try {
            if (mqttClient != null) {
                mqttClient?.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Disconnected")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to disconnect")
                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "disconnectMqtt: ${e.message}")
        }
    }
}


//@RequiresApi(Build.VERSION_CODES.N)
//class Mqtt5Helper(
//    context: Context,
//    private val listMQTTInfo: List<NoiDungMQTT>,
//    private val hubToken: String,
//    private val connectEventListener: IConnectionServer,
//) {
//    private val TAG = "MQTTLOG"
//    var clientID = ""
//    private var publishTopicTinh = ""
//    private val serverURI = ConfigUtil.getStringEncrypt(context, ConfigUtil.URL_MQTT)
//
//    private val client: Mqtt5AsyncClient = MqttClient.builder()
//        .useMqttVersion5()
//        .serverHost(serverURI)
//        .serverPort(1883)
//        .identifier(UUID.randomUUID().toString())
//        .addDisconnectedListener { context ->
//            val error = context.cause.message ?: "Không có lỗi cụ thể"
//
//            Log.e(TAG, "🚫 MQTT bị ngắt kết nối:")
//            Log.e(TAG, "⚠️Throwable: $error")
//            reconnectWithDelay()
//        }
//        .buildAsync()
//
//    private fun reconnectWithDelay() {
//        CoroutineScope(Dispatchers.IO).launch {
//            delay(5000)
//            Log.d(TAG, "🔁 Thử reconnect lại MQTT sau 5s...")
//            connectToMQTT()
//        }
//    }
//
//    fun connectToMQTT() {
//        clientID = listMQTTInfo.find { it.topic?.startsWith("thietbi/") == true }
//            ?.topic?.substringAfter("/") ?: ""
//
//        if (clientID.isEmpty()) {
//            Log.e(TAG, "Không tìm thấy clientID hợp lệ từ topic 'thietbi/...'")
//            connectEventListener.onConnectFailure("Không có clientID từ topic thietbi")
//            return
//        }
//
//        publishTopicTinh = "tinh/$clientID"
//        Log.d(TAG, "clientID: $clientID | topic publish: $publishTopicTinh")
//
//        client.connectWith()
//            .cleanStart(true)
//            .keepAlive(120)
//            .sessionExpiryInterval(900)
//            .simpleAuth()
//            .username(clientID)
//            .password(hubToken.toByteArray())
//            .applySimpleAuth()
//            .send()
//            .whenComplete { connAck, throwable ->
//                if (throwable != null) {
//                    Log.e(TAG, "Kết nối thất bại", throwable)
//                    connectEventListener.onConnectFailure(throwable.message)
//                } else {
//                    Log.d(TAG, "✅ Kết nối thành công")
//                    Log.d(TAG, "${client.state.isConnected}")
//                    connectEventListener.onConnectSuccess()
//
//                    listMQTTInfo[0].topic?.let { subscribe(it) }
////                    publishTopicTinh = listMQTTInfo[0].topic ?: ""
//                    listMQTTInfo.forEach { info ->
//                        info.topic?.let { topic ->
//                            when {
//                                topic.startsWith("thietbi/") -> {
//                                    subscribe(topic)
//                                }
//                                topic.startsWith("xa/") || topic.startsWith("thon/") -> {
//                                    val rawTopic = topic.removePrefix("xa/").removePrefix("thon/")
//                                    val parts = rawTopic.split(".")
//                                    if (parts.isNotEmpty()) subscribe("tinh/${parts[0]}")
//                                    if (parts.size >= 2) subscribe("huyen/${parts[0]}.${parts[1]}")
//                                    if (parts.size >= 3) subscribe("xa/${parts[0]}.${parts[1]}.${parts[2]}")
//                                    if (parts.size >= 4) subscribe("thon/${parts[0]}.${parts[1]}.${parts[2]}.${parts[3]}")
//                                }
//                            }
//                        }
//                    }
//                    //👉 Subscribe theo từng topic cấu hình
////                    GlobalScope.launch(Dispatchers.IO) {
////                        repeat(10) {
////                            if (client.state.isConnected) {
////                                subAll()
////                                return@launch
////                            }
////                            delay(5000) // đợi 100ms rồi kiểm tra lại
////                        }
////
////                        // Nếu sau 1s vẫn chưa kết nối
////                        Log.e(TAG, "❌ client.state vẫn chưa CONNECTED sau khi connect thành công")
////                    }
//
//                    // 👉 Nhận publish
//                    client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
//                        val message = String(publish.payloadAsBytes)
//                        Log.d(TAG, "Tin nhận từ [${publish.topic}]: $message")
//                        connectEventListener.onReceiveMessageSuccess(message)
//                    }
//                }
//            }
//
//
//    }
//
//    private fun subscribe(topic: String) {
////        if (!client.state.isConnected) {
////            Log.w(TAG, "Bỏ qua subscribe vì topic rỗng")
////            return
////        }
//        client.subscribeWith()
//            .topicFilter(topic)
//            .qos(MqttQos.AT_LEAST_ONCE)
//            .send()
//            .whenComplete { _, error ->
//                if (error != null) {
//                    Log.e(TAG, "Lỗi khi subscribe [$topic]: ${error.message}")
//                } else {
//                    Log.d(TAG, "Đã subscribe: $topic")
//                }
//            }
//    }
//
//    fun publishMessage(payload: String) {
////        if (!client.state.isConnected)  {
////            Log.d(TAG, "📤 Publish tới $publishTopicTinh")
////            Log.e(TAG, "Không thể publish: topic tinh rỗng")
////            return
////        }
//        Log.d(TAG, "📤 Publish tới $publishTopicTinh")
//        client.publishWith()
//            .topic(publishTopicTinh)
//            .payload(payload.toByteArray())
//            .qos(MqttQos.AT_LEAST_ONCE)
//            .send()
//            .whenComplete { _, error ->
//                if (error != null) {
//                    Log.e(TAG, "Gửi tin lỗi: ${error.message}")
//                } else {
//                    Log.d(TAG, "Gửi thành công đến $publishTopicTinh")
//                }
//            }
//    }
//
//    fun disconnect() {
//        client.disconnect()
//            .whenComplete { _, error ->
//                if (error != null) {
//                    Log.e(TAG, "Lỗi khi disconnect:", error)
//                } else {
//                    Log.i(TAG, "Đã ngắt kết nối MQTT broker")
//                }
//            }
//    }
//}

