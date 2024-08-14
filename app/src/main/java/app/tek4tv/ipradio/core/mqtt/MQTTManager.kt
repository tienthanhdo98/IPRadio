package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.utils.Utils
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTManager(
    private var hubToken: String?,
    private val connectEventListener: IConnectionServer,
    private val destinationId: String
) {
    val TAG = "MQTTLOG"
   // var clientID = Utils.getDeviceId(appContext).lowercase()
    var clientID = "4a013991-793f-4afb-86f5-7505c1590676"
    private var publishTopicTinh = "tinh/$clientID"

    private var mqttClient: MqttAndroidClient? = null

    fun getSubscribeTopic(destinationId: String) {
        subscribe("thietbi/$clientID")
        val listDesId = destinationId.split(".")
        Log.d(TAG, "Subscribed: ${listDesId.count()}")
        if (listDesId.isNotEmpty()) {

            subscribe("tinh/${listDesId[0]}".lowercase())
        }
        if (listDesId.count() >= 2) {
            subscribe("huyen/${listDesId[0]}.${listDesId[1]}".lowercase())
        }
        if (listDesId.count() >= 3) {
            subscribe("xa/${listDesId[0]}.${listDesId[1]}.${listDesId[2]}".lowercase())
        } else {

            subscribe("thon/$destinationId".lowercase())
        }
    }

    fun connectToMQTT(context: Context) {
        val serverURI = "tcp://mqtt-staging.thongtinnguon.vn:1883"
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
                    getSubscribeTopic(destinationId = destinationId)
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
                    connectEventListener.onSendMessageSuccess("onSuccess: $strMessage to $clientID")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to publish to $publishTopicTinh")
                    connectEventListener.onSendMessageFailure("onFailure: ${exception?.message}")
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
//                    connectEventListener.onConnectSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to subscribe $subscribeTopic")
//                    if (exception != null) {
//                        connectEventListener.onConnectFailure(exception.message)
//                    }
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
                        connectEventListener.onDisconnectFailure("")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Failed to disconnect")
                        if (exception != null) {
                            connectEventListener.onDisconnectFailure(exception.message)
                        }
                    }
                })
            }
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}