package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.utils.ConfigUtil
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.android.service.MqttTraceHandler
import org.eclipse.paho.client.mqttv3.*

class MQTTManager(
    private var hubToken: String?,
    private val connectEventListener: IConnectionServer,
    private val listMQTTInfo: List<NoiDungMQTT>
) {
    val TAG = "MQTTLOG"
    var clientID = ""
    private var publishTopicTinh = ""


    var mqttClient: MqttAndroidClient? = null


    fun connectToMQTT(context: Context) {
        listMQTTInfo.forEach {
            if (it.topic?.contains("thietbi") == true) {
                clientID = it.topic.substringAfter("/")

            }
        }
        publishTopicTinh = "tinh/$clientID"

        //val serverURI = "tcp://mqtt-ttn.longan.gov.vn:1884"
        //     val serverURI = "tcp://digital-mqtt-staging.thongtinnguon.vn:1884"
        val serverURI = ConfigUtil.getString(context, ConfigUtil.URL_MQTT, "")
        mqttClient = MqttAndroidClient(context, serverURI, clientID)
        mqttClient?.setTraceEnabled(true)

        mqttClient?.setTraceCallback(object : MqttTraceHandler {
            override fun traceDebug(tag: String?, message: String) {
                Log.d("MQTTINFO", "TRACE DEBUG  [$tag]: $message")
            }

            override fun traceError(tag: String?, message: String) {
                Log.e("MQTTINFO", "TRACE ERROR  [$tag]: $message")
            }

            override fun traceException(
                tag: String?, message: String, e: Exception
            ) {
                Log.e("MQTTINFO", "TRACE EXCEPTION  [$tag]: $message", e)
            }
        })
        mqttClient?.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
                connectEventListener.onReceiveMessageSuccess(message.toString())
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost ${cause.toString()}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })
        val options = MqttConnectOptions()
        if (hubToken != null) {
            options.userName = clientID
            options.password = hubToken?.toCharArray()
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
//                    getSubscribeTopic(destinationId)
                    listMQTTInfo.forEach {
                        subscribe(it.topic!!)
                    }
                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception!!.message}")

                    connectEventListener.onConnectFailure(exception.message)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publishToMQTT(strMessage: String, qos: Int = 1, retained: Boolean = false) {
        try {
            val message = MqttMessage()
            message.payload = strMessage.toByteArray()
            message.qos = qos
            message.isRetained = retained
            mqttClient?.publish(publishTopicTinh, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "$strMessage = published to $clientID")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to publish to $publishTopicTinh")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun subscribe(subscribeTopic: String, qos: Int = 1) {
        try {
            mqttClient?.subscribe(subscribeTopic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to $subscribeTopic")
//                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to subscribe $subscribeTopic")
//                    if (exception != null) {
//                        connectEventListener.onConnectFailure(exception.message)
//                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
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
            e.printStackTrace()
        }
    }
}