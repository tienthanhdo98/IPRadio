package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

/**
 * Event listener for IMediaPlayer
 * **/
interface IPlayerEventListener {
    fun onPlayerError(e: Exception? = null, mediaItem: MediaItem)
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaEndReached(mediaItem: MediaItem?)
    fun onMediaForceEndReached(mediaItem: MediaItem?)
}
