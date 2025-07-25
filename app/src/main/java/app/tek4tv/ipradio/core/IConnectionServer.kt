package app.tek4tv.ipradio.core

/**
 * Represent a media player
 * **/
interface IConnectionServer {
    fun onConnectSuccess()
    fun onConnectFailure(message: String?)
    fun onReceiveMessageSuccess(command: String? = "", message: String)
}
