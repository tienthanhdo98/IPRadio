package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.core.HubEventObserver
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.MoshiInst.moshi
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


/**
 * Manager all media playback
 * 2 Mode: Scheduled and force media
 * Scheduled: Play media in a fixed scheduled
 * Force Media: Play media by command from server
 * **/
class PlayersManager(
    appContext: Context,
    private val rootLayout: View,
    private val mediaRepo: MediaRepo
) : IPlayerEventListener, HubEventObserver {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this)
    private var currentPlayer: IMediaPlayer? = null
    private var currentPlayingMedia: MediaItem? = null

    /**
     * The timestamp when a media begin to play
     * **/
    private var mediaStartTime = 0L

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_INIT//"0"

    /**
     * List of media for a timeframe
     * **/
    private var currentPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false

    var played = false

    override fun onPlayerError(e: Exception?, isLiveError: Boolean,mediaItem: MediaItem) {
        if (isLiveError) {
            //To log stream played duration
            onMediaStopPlaying()
            playerManagerListener?.onLiveError(mediaItem)
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
        mediaStartTime = Calendar.getInstance().timeInMillis
        played = true
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        onMediaStopPlaying()
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

    private fun onMediaStopPlaying() {
        if (played) {
            Log.e("AAA", "Đừng vào đây lần 2")
            val duration = Calendar.getInstance().timeInMillis - mediaStartTime
            playerManagerListener?.onMediaStopPlaying(currentPlayingMedia!!, duration)
        }

        played = false
    }

    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/
    var isInTimeFrame = false
    var indexOfNowTimeFrame = -1
    var isHaveNextTimeFrameAfter = false
    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        val now = Calendar.getInstance().timeInMillis
        Log.d("CONTROL_MEDIA", "play mode: ${scheduledList.keys}")
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            if (now in start.timeInMillis..end.timeInMillis) {
                if(i <= scheduledList.size)
                {
                    if (i + 1 < scheduledList.size) {
                        Log.d(LOGTAG, "have next one time frame")
                        val nextPeriod = scheduledList.keys.elementAt(i + 1)
                        val nextT = nextPeriod.split("-")
                        val nextStart = toCalendar(nextT[0])
                        isHaveNextTimeFrameAfter = nextStart.timeInMillis - end.timeInMillis > 10000
                    } else {
                        Log.d(LOGTAG, "only one time frame")
                    }
                }
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                Log.d(LOGTAG, "Play media in timeframe")
                found = true
                isInTimeFrame = true
                indexOfNowTimeFrame = i
                break
            }
        }

        if (!found) {
            isInTimeFrame = false
            if(isHaveNextTimeFrameAfter)
            {
                playerManagerListener?.onTimeOver()
            }
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()

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

    fun playNextMedia() {
        Log.d("CONTROL_MEDIA", "play mode: ${playMode}")
        Log.d("CONTROL_MEDIA", "play mode: ${playlistIndex}-${currentPlaylist.size}")
        Log.d("CONTROL_MEDIA", "play mode: $indexOfNowTimeFrame}")
        if (playMode == "") return
            playlistIndex++
            if (playlistIndex < currentPlaylist.size) {
                Log.d(LOGTAG, "Play media phat tiep")
                playMediaByIndex(playlistIndex)
            }
    }
    fun pauseMedia()
    {
        vlcPlayer.pause()
    }
    fun resumeMedia()
    {
        vlcPlayer.resume()
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            playMedia(mediaItem, timeStamp)

            Log.d(LOGTAG, "Play media $index")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {
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
                    mediaStartTime = Calendar.getInstance().timeInMillis
                    playerManagerListener?.onPlayTuner(mediaItem)
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
        currentPlayer = if (mediaItem.body!!.endsWith("m3u8")) {
            exoPlayer.playMedia(mediaItem, -1)
            exoPlayer
        } else {
            vlcPlayer.playMedia(mediaItem, -1)
            vlcPlayer
        }
    }

    fun stopAllPlayer() {
        vlcPlayer.stop()
        exoPlayer.stop()
        played = false
    }


    /**
     * If there are no timeframe to play in the next 15s,
     * fire an timeover event
     * **/
    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerManagerListener?.onTimeOver()
        }
    }

    /**
     * Find media and the second from start which it should be played at the moment
     * trả về index của media cần phát tịa thời điểm hiện tại và nếu là file thì thời gian tính từ lúc bắt đầu
     * **/
    private fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        try {
            val mediaList = mediaRepo.scheduledList[timeFrame] ?: return Pair(0, 0)
            val now = Calendar.getInstance()
            val secondFromStart = ((now.timeInMillis - start.timeInMillis) / 1000).toInt()

            var t = 0

            for (i in mediaList.indices) {
                val media = mediaList[i]
                val mediaDuration = getDurationInSecond(media.duration!!)

                t += mediaDuration

                if (t > secondFromStart) {
                    return if (GlobalProperties.getInst().resumeFromStart) {
                        Pair(i, 0)
                    } else {
                        val startTimeStamp = mediaDuration - (t - secondFromStart)
                        Pair(i, startTimeStamp)
                    }
                }
            }

            return Pair(0, 0)
        } catch (e: Exception) {
            Log.e(LOGTAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }
    fun stopTimeFrame()
    {
        playerManagerListener?.onTimeOver()
    }
    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()



        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    fun endScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        val itemToEnd = scheduledItems[mediaIndex]
        if (itemToEnd.mediaID != currentPlayingMedia?.mediaID) return

        stopAllPlayer()
        onMediaStopPlaying()

        playNextMedia()
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        onMediaStopPlaying()
        playerManagerListener?.onEndForceMedia()
        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub
     * **/

    fun onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler
    ) {
        try {
            val responseAdapter = moshi.adapter(MediaItem::class.java)
            val duration = responseHub.duration
            val type = responseHub.type
            val level = responseHub.level
            val mediaItemObject = responseAdapter.fromJson(responseHub.name)
            //check xem nếu đang phát media khẩn cấp có cấp độ thấp hơn không
            //Nếu có thì bỏ qua không phát
            //Chỉ ưu tiên media khẩn cấp có cấp độ thấp hơn

            if (playMode == PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) return
                }
            }

            val forceMedia =
                ForceMedia(responseHub.message!!, duration, type, level, mediaItemObject).apply {

                    val mediaEndTime = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, convertStringToInt(duration))
                    }
                    endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                    fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                }
            playForceMedia(context, forceMedia, mediaScheduler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun convertStringToInt(time: String): Int {
        val listDur = time.split(":")
        val h = listDur[0].toInt()
        val m = listDur[1].toInt()
        val s = listDur[2].toInt()
        return (h * 3600 + m * 60 + s) / 60
    }

    fun playForceMedia(context: Context, forceMedia: ForceMedia, mediaScheduler: MediaScheduler) {
        try {
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

            forceMedia.saveItemToPref(context)
            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm()

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()
            playMedia(forceMedia.toMediaItem())

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityStop() {
        stopAllPlayer()
    }

    fun onActivityDestroy() {
        vlcPlayer.release()
        exoPlayer.release()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }

    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
        const val LOGTAG = "player"
    }
}