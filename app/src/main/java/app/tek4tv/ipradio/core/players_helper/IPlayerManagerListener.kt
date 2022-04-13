package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onStartTimeFrame(frame: String)
    fun onEndTimeFrame(frame: String)
    fun onPlayTuner(mediaItem: MediaItem)
    fun onLiveError()
    fun onEndForceMedia(mediaItem: MediaItem?)
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long)
}