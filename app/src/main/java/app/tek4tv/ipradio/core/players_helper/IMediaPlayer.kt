package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaFile

/**
 * Represent a media player
 * **/
interface IMediaPlayer {
    fun playMedia(mediaItem: MediaFile, isForce: Boolean, timeStamp: Int = -1)
    fun stop()
    fun pause()
    fun resume()
    fun release()
    fun getCurrentPositionMs(): Long
    fun getCurrentDurationMs(): Long
}