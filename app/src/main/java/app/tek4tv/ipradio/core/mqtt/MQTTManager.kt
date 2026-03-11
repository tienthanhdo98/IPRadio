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
    private val channels: List<Channel>
) {
    val TAG = "MQTTLOG"
    private val clientID = "$thingId"
    private val channelId = channels.first().id

    private var mqttClient: MqttAndroidClient? = null


    fun connectToMQTT() {
        Log.d(TAG, "clientID ${clientID}")

        val serverURI = "ssl://aiot.mobifone.vn:6668"

        mqttClient = MqttAndroidClient(context, serverURI, clientID)

        mqttClient?.setCallback(object : MqttCallbackExtended {

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
                connectEventListener.onReceiveMessageSuccess(message.toString())
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost ${cause}")
                cause?.printStackTrace()
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }

            //resubscribe sau khi tu reconnect cua thu vien( chua test)
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "Connection connectComplete")
                channels.forEach { subscribe(it.id!!) } // resubscribe
            }
        })
        val options = MqttConnectOptions()

        options.userName = thingId
        options.password = thingKey.toCharArray()
        options.isAutomaticReconnect = true
        options.isCleanSession = true
        options.connectionTimeout = 60
        options.keepAliveInterval = 60

        try {
            if (mqttClient != null) {
                mqttClient?.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Connection success")
                        channels.forEach {
                            subscribe(it.id!!)
                        }
                        connectEventListener.onConnectSuccess()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "Connection failure ${exception!!}")
                        exception.printStackTrace()
                        connectEventListener.onConnectFailure(exception.message)
                    }
                })
            }
        } catch (e: Exception) {
            Log.d(TAG, "connectToMQTT: ${e.message}")
        }
    }

    fun publishToMQTT(
        strMessage: String,
        qos: Int = 0,
        retained: Boolean = false
    ) {
        try {
            val message = MqttMessage()
            message.payload = strMessage.toByteArray()
            message.qos = qos
            message.isRetained = retained
            if (mqttClient != null) {
                val publishToServerChannel = "channels/$channelId/messages/mirav3/s"
                mqttClient?.publish(
                    publishToServerChannel,
                    message,
                    null,
                    object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(TAG, "$strMessage = published to $clientID")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken?,
                            exception: Throwable?
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


}



