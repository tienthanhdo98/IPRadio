package app.tek4tv.ipradio.core.players_helper

import android.os.Handler
import app.tek4tv.ipradio.model.MediaItem

abstract class BaseMediaPlayer(
    protected val eventListener: IPlayerEventListener
) : IMediaPlayer {
    protected var currentMedia: MediaItem? = null
    protected var handler: Handler? = null


    override fun pause() {

    }

    override fun release() {

    }

    abstract override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int)
    abstract override fun stop()
    abstract override fun resume()
}