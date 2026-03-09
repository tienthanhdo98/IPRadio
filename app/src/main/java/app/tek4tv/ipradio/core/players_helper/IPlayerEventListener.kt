package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaFile


/**
 * Event listener for IMediaPlayer
 * **/
interface IPlayerEventListener {
    fun onPlayerError(e: String? = null, mediaItem: MediaFile?)
    fun onPlayMediaSuccess(mediaItem: MediaFile?)
    fun onMediaEndReached(mediaItem: MediaFile?)
    fun onMediaForceEndReached(mediaItem: MediaFile?)
}
