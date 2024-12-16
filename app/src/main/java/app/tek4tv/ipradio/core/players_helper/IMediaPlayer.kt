package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItemModel

/**
 * Represent a media player
 * **/
interface IMediaPlayer {
    var disconnectedHub : Boolean
    fun playMedia(mediaItem: MediaItemModel, isForce : Boolean, timeStamp: Int = -1)
    fun reset()
    fun stop()
    fun pause()
    fun resume()
    fun release()
    fun isPlaying() : Boolean
}