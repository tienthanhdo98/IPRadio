package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

interface IMediaPlayer {
    var disconnectedHub : Boolean
    fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1)
    fun reset()
    fun stop()
    fun release()
    fun isPlaying() : Boolean
    fun onLostHubConnection()
    fun onHubReconnected()
}