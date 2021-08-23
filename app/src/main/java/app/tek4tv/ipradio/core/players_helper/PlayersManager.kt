package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.core.HubEventObserver
import app.tek4tv.ipradio.core.PlayerCallback
import app.tek4tv.ipradio.core.PlayerManager
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class PlayersManager(
    appContext: Context,
    private val rootLayout : View,
    private val mediaRepo: MediaRepo) : IPlayerEventListener, HubEventObserver {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this)
    private var currentPlayingMedia: MediaItem? = null

    //-2 for force media
    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    var currentPlaylistKey = "0"

    private var currentPlaylist = listOf<MediaItem>()

    private var playlistIndex = 0

    var playerCallback: PlayerCallback? = null
    var isTimeOver = false

    override fun requestTurnOnPaLive() {
        playerCallback?.requestTurnOnPa()
    }

    override fun onPlayerError(e: Exception?, isLiveError: Boolean) {
        if(isLiveError)
        {
            playerCallback?.onLiveError()
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        currentPlayingMedia = mediaItem
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        playNextMedia()
    }

    override fun onLostHubConnection() {

    }

    override fun onConnectedToHub() {

    }

    //Playlist - timeframe
    fun startFirstTimeFrame()
    {
        isTimeOver = true
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            val now = Calendar.getInstance().timeInMillis

            if (now in start.timeInMillis..end.timeInMillis) {

                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)

                break
            }
        }
        checkTimeOver()
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
        playerCallback?.onStartTimeFrame(timeFrame)
        currentPlaylistKey = timeFrame

        currentPlaylist = mediaRepo.scheduledList[timeFrame] ?: listOf()

        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            playMediaByIndex(playlistIndex, resumePoint.second)
        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }

        isTimeOver = false
    }

    private fun playNextMedia() {
        if (currentPlaylistKey == "") return

        rootLayout.post {
            playlistIndex++
            if (playlistIndex >= currentPlaylist.size) playlistIndex = 0

            playMediaByIndex(playlistIndex)
        }
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            playMedia(mediaItem, timeStamp)

            Log.d(PlayerManager.LOGTAG, "Play media $index")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {
        try {
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    vlcPlayer.playMedia(mediaItem, timeStamp)
                }
                MediaType.LIVE -> {
                    playLiveStream(mediaItem)
                }
                MediaType.FM -> {
                    playerCallback?.onPlayTuner(mediaItem)
                }
                else -> {
                    playNextMedia()
                }
            }

            playerCallback?.onPlayNewMedia(playlistIndex, mediaItem)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaItem) {
        if(mediaItem.path.endsWith("m3u8")) exoPlayer.playMedia(mediaItem, -1)
        else vlcPlayer.playMedia(mediaItem, -1)
    }

    fun stopAllPlayer()
    {
        vlcPlayer.stop()
        exoPlayer.stop()
    }

    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerCallback?.onTimeOver()
        }
    }

    private fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        try {
            val mediaList = mediaRepo.scheduledList[timeFrame] ?: return Pair(0, 0)
            val now = Calendar.getInstance()
            val secondFromStart = ((now.timeInMillis - start.timeInMillis) / 1000).toInt()

            var t = 0

            for (i in mediaList.indices) {
                val media = mediaList[i]
                val mediaDuration = getDurationInSecond(media.duration)

                t += mediaDuration

                if (t > secondFromStart) {
                    return if(GlobalProperties.getInst().resumeFromStart) {
                        Pair(i, 0)
                    } else {
                        val startTimeStamp = mediaDuration - (t - secondFromStart)
                        Pair(i, startTimeStamp)
                    }
                }
            }

            return Pair(0, 0)
        } catch (e: Exception) {
            Log.e(PlayerManager.LOGTAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != currentPlaylistKey) return
        currentPlaylistKey = ""
        stopAllPlayer()

        playerCallback?.onEndTimeFrame(cancelListKey)

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    fun endScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        val itemToEnd = scheduledItems[mediaIndex]
        if (itemToEnd.id != currentPlayingMedia?.id) return

        stopAllPlayer()

        playNextMedia()
    }

    fun playForceMedia(forceMedia: ForceMedia) {
        currentPlaylistKey = PLAY_MODE_FORCE
        stopAllPlayer()

        playMedia(forceMedia.toMediaItem())
    }

    fun stopForceMedia() {
        if (currentPlaylistKey != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerCallback?.onEndForceMedia()
    }

    fun onActivityStop()
    {
        stopAllPlayer()
    }

    fun onActivityDestroy()
    {
        vlcPlayer.release()
        exoPlayer.release()
    }

    companion object{
        const val PLAY_MODE_FORCE = "-2"
    }
}