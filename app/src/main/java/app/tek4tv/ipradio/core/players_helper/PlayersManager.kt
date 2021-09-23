package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.core.HubEventObserver
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.core.PlayerCallback
import app.tek4tv.ipradio.core.PlayerManager
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PlayersManager(
    appContext: Context,
    private val rootLayout : View,
    private val mediaRepo: MediaRepo) : IPlayerEventListener, HubEventObserver {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this)
    private var currentPlayer: IMediaPlayer? = null
    private var currentPlayingMedia: MediaItem? = null

    //-2 for force media
    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var playMode = "0"

    private var currentPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerCallback: PlayerCallback? = null
    var isTimeOver = false

    override fun onPlayerError(e: Exception?, isLiveError: Boolean) {
        if(isLiveError)
        {
            playerCallback?.onLiveError()
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerCallback?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        playNextMedia()
    }

    override fun onLostHubConnection() {
        vlcPlayer.disconnectedHub = true
        exoPlayer.disconnectedHub = true
        currentPlayer?.onLostHubConnection()
    }

    override fun onReconnectedToHub() {
        vlcPlayer.disconnectedHub = false
        exoPlayer.disconnectedHub = false
        currentPlayer?.onHubReconnected()
    }

    //Playlist - timeframe
    fun startFirstTimeFrame()
    {
        //isTimeOver = true
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
        //checkTimeOver()
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
        playerCallback?.onStartTimeFrame(timeFrame)
        playMode = timeFrame

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
        if (playMode == "") return

        rootLayout.post {
            playlistIndex++
            if(playlistIndex < currentPlaylist.size) playMediaByIndex(playlistIndex)
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

    fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {
        playerCallback?.onPlayNewMedia(playlistIndex, mediaItem)
        try {
            currentPlayingMedia = mediaItem
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    vlcPlayer.playMedia(mediaItem, timeStamp)
                    currentPlayer = vlcPlayer
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaItem) {
        currentPlayer = if(mediaItem.path.endsWith("m3u8")) {
            exoPlayer.playMedia(mediaItem, -1)
            exoPlayer
        } else {
            vlcPlayer.playMedia(mediaItem, -1)
            vlcPlayer
        }
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
        if (cancelListKey != playMode) return
        playMode = ""
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

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerCallback?.onEndForceMedia()
    }

    fun onPlayForceCommand(context: Context, responseHub: ResponseHub, mediaScheduler: MediaScheduler) {
        try {
            val duration = responseHub.duration
            val type = responseHub.type
            val level = responseHub.level
            val mediaName = responseHub.name

            //if we are playing a force media that has a lower level, skip playing this
            if (playMode == PlayerManager.PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) return
                }
            }

            val forceMedia = ForceMedia(responseHub.message!!, duration, type, level).apply {
                val mediaEndTime = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, duration)
                }
                endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                name = mediaName
            }
            playForceMedia(context, forceMedia, mediaScheduler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playForceMedia(context: Context, forceMedia: ForceMedia, mediaScheduler: MediaScheduler) {
        try {
            Log.d("aaa","force")
            mediaScheduler.cancelAllAlarm()

            forceMedia.saveItemToPref(context)
            mediaScheduler.scheduleForceMediaAlarm()

            playerCallback?.onPlayForceMedia()

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()
            playMedia(forceMedia.toMediaItem())

        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    fun resetPlayMode()
    {
        playMode = "0"
    }

    companion object{
        const val PLAY_MODE_FORCE = "-2"
    }
}