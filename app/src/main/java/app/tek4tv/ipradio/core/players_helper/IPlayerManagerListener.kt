package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItemModel

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onMediaError(error: String, mediaItem: MediaItemModel)
    fun onEndForceMedia()
    fun onPlayMediaSuccess(mediaItem: MediaItemModel?)
    fun onMediaStopPlaying(mediaItem: MediaItemModel, duration: Long)

}