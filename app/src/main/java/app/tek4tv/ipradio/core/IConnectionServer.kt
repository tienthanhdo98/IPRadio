package app.tek4tv.ipradio.core

/**
 * Represent a media player
 * **/
interface IConnectionServer {
    fun onConnectSuccess()
    fun onConnectFailure(message : String?)
    fun onSendMessageSuccess(responseMessage : String)
    fun onSendMessageFailure(responseMessage : String)
    fun onDisconnectFailure(message : String?)
    fun onReceiveMessageSuccess(message : String)
}