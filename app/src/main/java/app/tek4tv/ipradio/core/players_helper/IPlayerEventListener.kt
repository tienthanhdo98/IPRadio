package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

interface IPlayerEventListener {
    fun requestTurnOnPaLive()
    fun onPlayerError(e: Exception? = null, isLiveError : Boolean = false)
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaEndReached(mediaItem: MediaItem?)
}