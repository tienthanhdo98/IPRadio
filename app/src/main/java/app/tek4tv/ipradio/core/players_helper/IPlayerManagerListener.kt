package app.tek4tv.ipradio.core.players_helper

import app.tek4tv.ipradio.model.MediaItem

/**
 * Event listener for PlayersManager
 * **/
interface IPlayerManagerListener {
    fun onTimeOver()
    fun onLiveError(mediaItem: MediaItem)
    fun onEndForceMedia()
    fun onPlayMediaSuccess(mediaItem: MediaItem?)
    fun onMediaEndReach(mediaItem: MediaItem)
    fun onLeftMediaPlaying(listMediaItem: List<MediaItem>)
    fun onEndLeftMediaPlaying()
}