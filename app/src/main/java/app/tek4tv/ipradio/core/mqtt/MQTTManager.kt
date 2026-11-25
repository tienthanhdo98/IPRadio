package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.NoiDungMQTT
import app.tek4tv.ipradio.utils.ConfigUtil
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

class MQTTManager(
    private val hubToken: String?,
    private val context: Context,
    private val connectEventListener: IConnectionServer,
    private val listMQTTInfo: List<NoiDungMQTT>,
    val destinationId: String
) {
    val TAG = "MQTTLOG"
    var clientID = ""
    private var publishTopicTinh = ""


    private var mqttClient: MqttAndroidClient? = null

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
                        Log.d(TAG, "Connection failure ${exception}")
                        connectEventListener.onConnectFailure(exception?.message)
                    }
                })
            }
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


}



