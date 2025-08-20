package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItemModel

/**
 * Event listener for IMediaPlayer
 * **/
interface IPlayerEventListener {
    fun onPlayerError(
        error: Exception? = null,
        isLiveError: Boolean = false,
        mediaItem: MediaItemModel
    )

    fun onPlayMediaSuccess(mediaItem: MediaItemModel?)
    fun onMediaEndReached(mediaItem: MediaItemModel?)
    fun onMediaForceEndReached(mediaItem: MediaItemModel?)
}