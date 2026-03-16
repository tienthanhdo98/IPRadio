package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaItem

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onPlayTuner(mediaItem: MediaFile)
    fun onLiveError(mediaItem: MediaFile?)
    fun onEndForceMedia()
    fun onPlayMediaSuccess(mediaItem: MediaFile?)

}