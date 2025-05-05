package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.utils.ConfigUtil
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*


@RequiresApi(Build.VERSION_CODES.N)
class MQTTManager(
    context: Context,
    private val listMQTTInfo: List<NoiDungMQTT>,
    private val hubToken: String,
    private val connectEventListener: IConnectionServer,
) {
    private val TAG = "MQTTLOG"
    var clientID = ""
    private var publishTopicTinh = ""
    private val serverURI = ConfigUtil.getStringEncrypt(context, ConfigUtil.URL_MQTT)

    private val client: Mqtt5AsyncClient = MqttClient.builder()
        .useMqttVersion5()
        .serverHost(serverURI)
        .serverPort(30236)
        .identifier(UUID.randomUUID().toString())
        .addDisconnectedListener { context ->
            val error = context.cause.message ?: "Không có lỗi cụ thể"

            Log.e(TAG, "🚫 MQTT bị ngắt kết nối:")
            Log.e(TAG, "⚠️Throwable: $error")
            reconnectWithDelay()
        }
        .buildAsync()

    private fun reconnectWithDelay() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            Log.d(TAG, "Thử reconnect lại MQTT sau 5s...")
            connectToMQTT()
        }
    }

    fun connectToMQTT() {
        clientID = listMQTTInfo.find { it.topic?.startsWith("thietbi/") == true }
            ?.topic?.substringAfter("/") ?: ""

        if (clientID.isEmpty()) {
            Log.e(TAG, "Không tìm thấy clientID hợp lệ từ topic 'thietbi/...'")
            connectEventListener.onConnectFailure("Không có clientID từ topic thietbi")
            return
        }

        // publishTopicTinh = "tinh/$clientID"
        publishTopicTinh = "thietbi/$clientID"
        Log.d(TAG, "clientID: $clientID | topic publish: $publishTopicTinh")

        client.connectWith()
            .cleanStart(false)
            .keepAlive(60)
            .sessionExpiryInterval(900)
            .simpleAuth()
            .username(clientID)
            .password(hubToken.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { connAck, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Kết nối thất bại", throwable)
                    connectEventListener.onConnectFailure(throwable.message)
                } else {
                    Log.d(TAG, "✅ Kết nối thành công")
                    Log.d(TAG, "${client.state.isConnected}")
                    connectEventListener.onConnectSuccess()

                    listMQTTInfo[0].topic?.let { subscribe(it) }
//                    publishTopicTinh = listMQTTInfo[0].topic ?: ""
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
                    //👉 Subscribe theo từng topic cấu hình
//                    GlobalScope.launch(Dispatchers.IO) {
//                        repeat(10) {
//                            if (client.state.isConnected) {
//                                subAll()
//                                return@launch
//                            }
//                            delay(5000) // đợi 100ms rồi kiểm tra lại
//                        }
//
//                        // Nếu sau 1s vẫn chưa kết nối
//                        Log.e(TAG, "❌ client.state vẫn chưa CONNECTED sau khi connect thành công")
//                    }

                    // 👉 Nhận publish
                    client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
                        val message = String(publish.payloadAsBytes)
                        Log.d(TAG, "Tin nhận từ [${publish.topic}]: $message")
                        connectEventListener.onReceiveMessageSuccess(message)
                    }
                }
            }


    }

    private fun subscribe(topic: String) {
//        if (!client.state.isConnected) {
//            Log.w(TAG, "Bỏ qua subscribe vì topic rỗng")
//            return
//        }
        client.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi khi subscribe [$topic]: ${error.message}")
                } else {
                    Log.d(TAG, "Đã subscribe: $topic")
                }
            }
    }

    fun publishToMQTT(payload: String) {
//        if (!client.state.isConnected)  {
//            Log.d(TAG, "📤 Publish tới $publishTopicTinh")
//            Log.e(TAG, "Không thể publish: topic tinh rỗng")
//            return
//        }
        Log.d(TAG, "📤 Publish tới $publishTopicTinh")
        client.publishWith()
            .topic(publishTopicTinh)
            .payload(payload.toByteArray())
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Gửi tin lỗi: ${error.message}")
                } else {
                    Log.d(TAG, "Gửi thành công đến $publishTopicTinh")
                }
            }
    }

    fun disconnectMqtt() {
        client.disconnect()
            .whenComplete { _, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi khi disconnect:", error)
                } else {
                    Log.i(TAG, "Đã ngắt kết nối MQTT broker")
                }
            }
    }
}

