package app.tek4tv.ipradio.core

import app.tek4tv.ipradio.model.MediaItem

/**
 * Represent a media player
 * **/
interface IConnectionServer {
    fun onSuccess()
    fun onFailure(message : String?)
    fun onSendMessageSuccess(responseMessage : String)
    fun onSendMessageFailure(responseMessage : String)
    fun onDisconnectFailure(message : String?)
    fun onReceiveMessageSuccess(message : String)
}