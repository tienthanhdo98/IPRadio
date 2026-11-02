package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date


/**
 * Manager all media playback
 * 2 Mode: Scheduled and force media
 * Scheduled: Play media in a fixed scheduled
 * Force Media: Play media by command from server
 * **/
class PlayersManager(
    appContext: Context,
    private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private var currentPlayingMedia: MediaItem? = null


    /**
     * -2 for force media (PLAY_MODE_FORCE)
     * "" for idle / init / stopped (PLAY_MODE_IDLE)
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_IDLE

    /**
     * List of media for a timeframe
     * **/
    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false


    var handlerFMPlayer: Handler? = null

    override fun onPlayerError(e: Exception?, isLiveError: Boolean, mediaItem: MediaItem) {
        if (isLiveError) {
            //To log stream played duration
            onMediaStopPlaying()
            playerManagerListener?.onLiveError(mediaItem)
        }
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        if (playMode == PLAY_MODE_FORCE) {
            stopForceMedia()
        } else {
            onMediaStopPlaying()
            playNextMedia()
        }
    }

    override fun onMediaForceEndReached(mediaItem: MediaItem?) {
        if (playMode == PLAY_MODE_FORCE) {
            stopForceMedia()
        } else {
            onMediaStopPlaying()
            playNextMedia()
        }
    }


    private fun onMediaStopPlaying() {

    }


    /**
     * Tìm khung thơi gian hiện tại cần phát
     * **/
    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        val now = Calendar.getInstance().timeInMillis
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            Log.d(LOGTAG, "onTimeOver: $period")
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            if (start.after(end)) {
                // Khung giờ qua nửa đêm (VD: 23:00 - 01:00)
                if (now < end.timeInMillis) {
                    // Sau nửa đêm (rạng sáng), start thuộc về ngày hôm qua
                    start.add(Calendar.DAY_OF_MONTH, -1)
                } else {
                    // Trước nửa đêm (tối), end thuộc về ngày mai
                    end.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            if (now in start.timeInMillis..end.timeInMillis) {
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                found = true
                break
            }
        }
        if (!found) {
            playerManagerListener?.onTimeOver()
            Log.d(LOGTAG, "onTimeOver: not time frame found")
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {

        stopAllPlayer()

        playMode = timeFrame

        currentPlaylist = mediaRepo.scheduledList[timeFrame] ?: listOf()

        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            playMediaByIndex(playlistIndex, resumePoint.second)
            Log.d(LOGTAG, "resumePoint index : ${resumePoint.first}")
            Log.d(LOGTAG, "resumePoint second: ${resumePoint.second}")
        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }

        isTimeOver = false
    }

    fun playNextMedia() {
        if (playMode == PLAY_MODE_IDLE) return
        vlcPlayer.stop()
        playlistIndex++
        try {
            if (playlistIndex < currentPlaylist.size) {
                Log.d(LOGTAG, "Play media phat tiep")
                playMediaByIndex(playlistIndex)
            } else {
                Log.d(LOGTAG, "Play media phat tiep cham dich")
                stopAllPlayer()
                stopTimeFrame()
            }
        } catch (e: Exception) {
            Log.d(LOGTAG, "playNextMedia error: $e")
        }

    }

    var currentPauseTimePlayer: Long = 0L
    fun pausePlayer() {
        currentPauseTimePlayer = Calendar.getInstance().timeInMillis
        vlcPlayer.pause()
    }

    fun resume() {
        vlcPlayer.resume()
    }

    fun isPlaying(): Boolean {
        return vlcPlayer.isPlaying() || handlerFMPlayer != null
    }


    private fun playMediaByIndex(index: Int, timeStamp: Int = 0) {
        try {
            Log.d(LOGTAG, "currentPlaylist ${currentPlaylist.size}")
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]
            Log.d(LOGTAG, "play ${mediaItem.title}")
            playMedia(mediaItem, timeStamp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = 0) {
        try {
            currentPlayingMedia = mediaItem
            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    // play live o trong lich
                    if (mediaItem.contentType?.value == "5" ||
                        mediaItem.contentType?.id == 5 ||
                        mediaItem.body?.contains("m3u8") == true
                    ) {
                        vlcPlayer.playMedia(mediaItem, true, timeStamp)
                    } else {
                        val finalTimeStamp = if (playMode == PLAY_MODE_FORCE && timeStamp == 0) {
                            calculateResumeLivePoint(mediaItem)
                        } else {
                            timeStamp
                        }
                        vlcPlayer.playMedia(mediaItem, false, finalTimeStamp)
                    }

                }

                MediaType.LIVE -> {
                    playLiveStream(mediaItem, true, timeStamp)
                }

                MediaType.FM -> {
                    var currentTimeStamp = 0
                    if (timeStamp >= 0) currentTimeStamp = timeStamp
                    playerManagerListener?.onPlayTuner(mediaItem)
                    if (playMode != PLAY_MODE_FORCE) {
                        handlerFMPlayer?.removeCallbacksAndMessages(null)
                        handlerFMPlayer = Handler(Looper.getMainLooper())
                        handlerFMPlayer?.postDelayed(
                            {
                                Log.e(LOGTAG, "Player FM start")
                                handlerFMPlayer = null
                                onMediaEndReached(mediaItem)
                                Log.e(LOGTAG, "Player FM stop")
                            },
                            getDurationInSecond(mediaItem.duration!!).toLong() * 1000 - currentTimeStamp * 1000
                        )
                    }
                }

                else -> {
                    Log.e(LOGTAG, "Player not in live and file")
                    playNextMedia()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
        val resumeLiveTime = if (isForce) calculateResumeLivePoint(mediaItem) else timeStamp
        Log.d(LOGTAG, "playLiveStream resume time: $resumeLiveTime")
        vlcPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
        vlcPlayer
    }

    fun stopAllPlayer() {
        handlerFMPlayer?.removeCallbacksAndMessages(null)
        handlerFMPlayer = null
        vlcPlayer.stop()
    }


    /**
     * If there are no timeframe to play in the next 15s,
     * fire an timeover event
     * **/
    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerManagerListener?.onTimeOver()
            Log.d("turnPA", "onTimeOver: check time over")
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

    private fun calculateResumeLivePoint(mediaItem: MediaItem): Int {
        val startStr = mediaItem.start ?: mediaItem.startTime ?: mediaItem.fixTime ?: "00:00:00"
        val startTimeOfMedia = toCalendar(startStr)
        val now = Calendar.getInstance()
        val elapsed = ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
        return if (elapsed > 0) elapsed else 0
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = PLAY_MODE_IDLE
        stopAllPlayer()

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    private fun stopTimeFrame() {
        stopAllPlayer()
        playMode = PLAY_MODE_IDLE
        isTimeOver = true
        playerManagerListener?.onTimeOver()
        Log.d("turnPA", "onTimeOver: stop time frame")
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playMode = PLAY_MODE_IDLE
        onMediaStopPlaying()
        playerManagerListener?.onEndForceMedia()
//        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
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
            Log.d("alarmx", "Duration $duration")
            if (playMode == PLAY_MODE_FORCE) {
                val value = ConfigUtil.getString(context, ConfigUtil.FORCE_MEDIA, "")
                if (value != null && value != "") {
                    val currentForceMedia = ForceMedia.jsonAdapter.fromJson(value)

                    if (currentForceMedia != null && currentForceMedia.level < level) return
                }
            }
            val forceMedia =
                ForceMedia(responseHub.message!!, duration, type, level, mediaItemObject).apply {
                    val listDur = duration.split(":")
                    val h = listDur[0].toInt()
                    val m = listDur[1].toInt()
                    val s = listDur[2].toInt()
                    val mediaEndTime = Calendar.getInstance().apply {
                        add(Calendar.HOUR, h)
                        add(Calendar.MINUTE, m)
                        add(Calendar.SECOND, s)
                    }
                    endTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(mediaEndTime.time)
                    fixTime = SimpleDateFormat("HH:mm:ss").format(Date())
                }
            playForceMedia(context, forceMedia, mediaScheduler)

        } catch (e: JsonDataException) {
            Log.d("alarmx", "JsonDataException: ${e.message}")
        } catch (e: JsonEncodingException) {
            Log.d("alarmx", "JsonEncodingException: ${e.message}")
        } catch (e: Exception) {
            Log.d("alarmx", "Exception ${e.message}")
        }
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
        stopAllPlayer()
        vlcPlayer.release()
    }

    fun stopAndEndTimeFrame() {
        stopAllPlayer()
        playMode = PLAY_MODE_IDLE
        isTimeOver = true
        playerManagerListener?.onTimeOver()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_IDLE
    }

    fun isIdle(): Boolean {
        return playMode == PLAY_MODE_IDLE
    }

    fun playTest() {
        vlcPlayer.playTest()
    }


    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_IDLE = ""
        const val LOGTAG = "player"
        var currentPlaylist = listOf<MediaItem>()
        var handlerLivePlayer: Handler? = null
    }

}