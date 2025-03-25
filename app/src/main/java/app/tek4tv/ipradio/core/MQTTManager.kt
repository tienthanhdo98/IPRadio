package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.NoiDungMQTT
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTManager(
    private var hubToken: String?,
    private val connectEventListener: IConnectionServer,
    private val listMQTTInfo : List<NoiDungMQTT>
) {
    val TAG = "MQTTLOG"
    var clientID = ""
    private var publishTopicTinh = ""

    private var mqttClient: MqttAndroidClient? = null

    fun getSubscribeTopic() {
        listMQTTInfo.forEach {
            if(it.topic.lowercase().contains("thietbi"))
            {
                subscribe(it.topic)
            }
            if(it.topic.lowercase().contains("tinh"))
            {
                subscribe(it.topic)
            }
            if(it.topic.lowercase().contains("huyen"))
            {
                subscribe(it.topic)
            }
            if(it.topic.lowercase().contains("xa"))
            {
                subscribe(it.topic)
            }
        }

    }

    fun connectToMQTT(context: Context) {
        listMQTTInfo.forEach {
            if(it.topic.contains("thietbi"))
            {
                clientID = it.topic.substringAfter("/")
            }
        }
        publishTopicTinh = "tinh/$clientID"

        val serverURI = "tcp://digital-mqtt-thongtinnguon.tek4tv.vn:1887"
        mqttClient = MqttAndroidClient(context, serverURI, clientID)
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
        if(hubToken != null) {
            options.userName = clientID
            options.password = hubToken?.toCharArray()
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                    getSubscribeTopic()
                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception!!.message}")
                    connectEventListener.onConnectFailure(exception.message)
                }
            })
        } catch (e: MqttException) {
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
                    Log.d(TAG, "Failed to publish to $exception?.message")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun subscribe(subscribeTopic: String, qos: Int = 1) {
        try {
            mqttClient?. subscribe(subscribeTopic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to $subscribeTopic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to subscribe $subscribeTopic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnectMqtt() {
        try {
            if(mqttClient != null) {
                mqttClient?.disconnect(null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Disconnected")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to disconnect")
                    }
                })
            }
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}