package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

interface PlayerCallback {
    fun onTimeOver()
    fun onStartTimeFrame(frame: String)
    fun onEndTimeFrame(frame: String)
    fun onPlayTuner(mediaItem: MediaItem)
    fun onLiveError()
    fun onEndForceMedia()
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaStopPlaying(mediaItem: MediaItem, duration: Long)
}