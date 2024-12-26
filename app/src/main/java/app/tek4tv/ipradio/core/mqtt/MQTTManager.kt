package app.tek4tv.ipradio.core.mqtt

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.IConnectionServer
import app.tek4tv.ipradio.model.NoiDungMQTT
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTManager(
    private var hubToken: String?,
    private val connectEventListener: IConnectionServer,
    private val destinationId: String,
    private val listMQTTInfo : List<NoiDungMQTT>
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

    fun connectToMQTT(context: Context) {
        listMQTTInfo.forEach {
            if(it.topic.contains("thietbi"))
            {
                clientID = it.topic.substringAfter("/")
            }
        }
        publishTopicTinh = "tinh/$clientID"
        Log.d(TAG, "clientID ${clientID}")
        //val serverURI = "tcp://mqtt-ttn.longan.gov.vn:1884"
        val serverURI = "tcp://ipradio-mqtt-thongtinnguon.tek4tv.vn:1886"
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
                    getSubscribeTopic(destinationId)
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