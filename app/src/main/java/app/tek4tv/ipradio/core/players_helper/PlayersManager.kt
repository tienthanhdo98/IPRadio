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
import org.json.JSONException
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
    private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
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
        mediaStartTime = Calendar.getInstance().timeInMillis
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        onMediaStopPlaying()
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItem?) {
        stopForceMedia()
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
        if (playMode == "") return
        if (currentPlayer == vlcPlayer) {
            vlcPlayer.stop()
        } else {
            exoPlayer.stop()
        }
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
        if (currentPlayer == vlcPlayer) {
            vlcPlayer.pause()
        } else {
            exoPlayer.pause()
        }
    }

    fun resume() {
//        val now = Calendar.getInstance().timeInMillis
//        val mediaDuration = getDurationInSecond(currentPlayingMedia?.duration!!) * 1000
//        val timeEnd = currentPauseTimePlayer + mediaDuration
//        Log.d("CONTROL_MEDIA", "resume: now $now")
//        Log.d("CONTROL_MEDIA", "resume: current pause$currentPauseTimePlayer")
//        if (now < timeEnd) {
//            Log.d("CONTROL_MEDIA", "resume: $timeEnd")
//            if (currentPlayer == vlcPlayer) {
//                vlcPlayer.resume()
//            } else {
//                exoPlayer.resume()
//            }
//        } else {
//            if (currentPauseTimePlayer != 0L) {
//                Log.d("CONTROL_MEDIA", "re play playlist: $timeEnd")
//                startFirstTimeFrame()
//            }
//        }
        vlcPlayer.resume()
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
                    if (mediaItem.body!!.contains("m3u8")) {
                        playLiveStream(mediaItem, false, timeStamp)
                    } else {
                        // play live o trong lich
                        if (mediaItem.contentType?.value == "5") {
                            vlcPlayer.playMedia(mediaItem, true, timeStamp)
                        } else {
                            vlcPlayer.playMedia(mediaItem, false, timeStamp)
                        }
                        currentPlayer = vlcPlayer
                    }
                }

                MediaType.LIVE -> {
                    playLiveStream(mediaItem, true, timeStamp)
                }

                MediaType.FM -> {
                    var currentTimeStamp = 0
                    if (timeStamp >= 0) currentTimeStamp = timeStamp
                    mediaStartTime = Calendar.getInstance().timeInMillis
                    playerManagerListener?.onPlayTuner(mediaItem)
                    if (playMode != PLAY_MODE_FORCE) {
                        handlerFMPlayer?.removeCallbacksAndMessages(null)
                        handlerFMPlayer = Handler(Looper.getMainLooper())
                        handlerFMPlayer?.postDelayed(
                            {
                                Log.e(LOGTAG, "Player FM start")
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
        currentPlayer = if (mediaItem.body!!.contains("m3u8")) {
            exoPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
            exoPlayer
        } else {
            vlcPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
            vlcPlayer
        }
    }

    fun stopAllPlayer() {
        handlerFMPlayer?.removeCallbacksAndMessages(null)
//        currentPlayingMedia = null
        vlcPlayer.stop()
        exoPlayer.stop()
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
        //với force media, fixtime dc gán là start time
        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()

    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    private fun stopTimeFrame() {
        playerManagerListener?.onTimeOver()
        Log.d("turnPA", "onTimeOver: stop time frame")
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
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
        vlcPlayer.release()
        exoPlayer.release()
    }

    fun stopAndEndTimeFrame() {
        vlcPlayer.stop()
        exoPlayer.stop()
        isTimeOver = true
        playerManagerListener?.onTimeOver()
    }

    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
    }

    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
        const val LOGTAG = "player"
        var currentPlaylist = listOf<MediaItem>()
        var handlerLivePlayer: Handler? = null
    }
}