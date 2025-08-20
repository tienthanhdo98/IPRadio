package app.tek4tv.ipradio.core.MqttManager

/**
 * Represent a media player
 * **/
interface IConnectionServer {
    fun onConnectSuccess()
    fun onConnectFailure(message: String?)
    fun onReceiveMessageSuccess(message: String)
}