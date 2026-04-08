package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaInfo

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onPlayTuner(mediaItem: MediaFile)
    fun onPlayError(mediaItem: MediaFile?)
    fun onPlayMediaSuccess(mediaItem: MediaFile?)
    fun onEndMedia(mediaItem: MediaFile?)
    fun onEndForceMedia(mediaInfo: MediaInfo?)
    fun onForceMediaExpired(mediaInfo: MediaInfo?)
}