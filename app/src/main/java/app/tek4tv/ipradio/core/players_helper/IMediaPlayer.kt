package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

/**
 * Represent a media player
 * **/
interface IMediaPlayer {
    fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1)
    fun reset()
    fun stop()
    fun pause()
    fun resume()
    fun release()

}