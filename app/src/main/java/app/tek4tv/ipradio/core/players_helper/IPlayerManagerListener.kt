package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onPlayTuner(mediaItem: MediaItem)
    fun onLiveError()
    fun onEndForceMedia()
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long)
}