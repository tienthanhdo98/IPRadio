package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.utils.Utils
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTManager( private var hubToken : String?, private val connectEventListener : IConnectionServer)  {
    val TAG = "MQTTLOG"
    var clientID = Utils.getDeviceId(appContext)
    private var subscribeTopic = "thietbi/$clientID"
    private  var publishTopic = "tinh/$clientID"
    private lateinit var mqttClient: MqttAndroidClient


    fun connectToMQTT(context: Context) {
        val serverURI = "tcp://mqtt.tek4tv.vn:1883"
        mqttClient = MqttAndroidClient(context, serverURI, clientID)
        mqttClient.setCallback(object : MqttCallback {
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
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connection success")
                    subscribe()

                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure ${exception!!.message}")
//                    hubToken = null
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
            mqttClient.publish(publishTopic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "$strMessage = published to $clientID")
                    connectEventListener.onSendMessageSuccess("onSuccess: $strMessage to $clientID")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to publish $strMessage = to $clientID")
                    if (exception != null) {
                        connectEventListener.onSendMessageFailure("onFailure: ${exception.message}")
                    }
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun subscribe(qos: Int = 1) {
        try {
            mqttClient.subscribe(subscribeTopic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to $clientID")
                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to subscribe $clientID")
                    if (exception != null) {
                        connectEventListener.onConnectFailure(exception.message)
                    }
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
    fun disconnectMqtt() {
        try {
            mqttClient.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Disconnected")

                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to disconnect")
                    if (exception != null) {
                        connectEventListener.onDisconnectFailure(exception.message)
                    }
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}