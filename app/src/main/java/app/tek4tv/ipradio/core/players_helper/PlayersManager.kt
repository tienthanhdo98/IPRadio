package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.listMediaFileItemAdapter
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaFileType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar


/**
 * Manager all media playback
 * 2 Mode: Scheduled and force media
 * Scheduled: Play media in a fixed scheduled
 * Force Media: Play media by command from server
 * **/
class PlayersManager(
    appContext: Context, private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this)
    private var currentPlayer: IMediaPlayer? = null
    private var currentPlayingMedia: MediaFile? = null

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

    override fun onPlayerError(e: String?, mediaItem: MediaFile?) {
        //To log stream played duration
        onMediaStopPlaying()
        playerManagerListener?.onLiveError(mediaItem)

    }

    override fun onPlayMediaSuccess(mediaItem: MediaFile?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
        mediaStartTime = Calendar.getInstance().timeInMillis
    }

    override fun onMediaEndReached(mediaItem: MediaFile?) {
        onMediaStopPlaying()
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaFile?) {
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
//        if (currentPlayer == vlcPlayer) {
//            vlcPlayer.stop()
//        } else {
//            exoPlayer.stop()
//        }
        playlistIndex++

        if (playlistIndex < currentPlaylist.size) {
            Log.d("MediaInfo", "Play media phat tiep")
            playMediaByIndex(playlistIndex)
        } else {
//            Log.d("MediaInfo", "Play media phat tiep cham dich")
//            stopAllPlayer()
//            stopTimeFrame()
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
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
    }


    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            Log.d(LOGTAG, "currentPlaylist ${currentPlaylist.size}")
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]
            Log.d(LOGTAG, "play ${mediaItem.url}")
            playMedia(mediaItem, timeStamp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMedia(mediaItem: MediaFile, timeStamp: Int = -1) {
        try {
            currentPlayingMedia = mediaItem
            val type = mediaItem.getMediaType()
            when (type) {
                MediaFileType.SILENT -> {
                    Log.d("MediaInfo", "playMedia: silent: ${mediaItem.duration}")
                    handlerFMPlayer?.removeCallbacksAndMessages(null)
                    handlerFMPlayer = Handler(Looper.getMainLooper())
                    handlerFMPlayer?.postDelayed(
                        {
                            Log.e("MediaInfo", "Player silent start")
                            onMediaEndReached(mediaItem)
                            Log.e("MediaInfo", "Player silent stop")
                        }, mediaItem.duration!!.toLong() * 1000
                    )

                }

                MediaFileType.FILE -> {
                    Log.d("MediaInfo", "playMedia: File: ${mediaItem.duration}")
                    if (mediaItem.url!!.contains("m3u8")) {
                        playLiveStream(mediaItem, false, timeStamp)
                    } else {
                        vlcPlayer.playMedia(mediaItem, false, timeStamp)
                        currentPlayer = vlcPlayer
                    }
                }

                MediaFileType.LIVE -> {
                    Log.d("MediaInfo", "playMedia: Live: ${mediaItem.duration}")
                    playLiveStream(mediaItem, true, timeStamp)
                }

                MediaFileType.FM -> {
                    Log.d("MediaInfo", "playMedia: fm: ${mediaItem.duration}")
                    var currentTimeStamp = 0
                    if (timeStamp != -1) currentTimeStamp = timeStamp
                    mediaStartTime = Calendar.getInstance().timeInMillis
                    playerManagerListener?.onPlayTuner(mediaItem)
//                    if (playMode != PLAY_MODE_FORCE) {
                    handlerFMPlayer?.removeCallbacksAndMessages(null)
                    handlerFMPlayer = Handler(Looper.getMainLooper())
                    handlerFMPlayer?.postDelayed(
                        {
                            Log.e("MediaInfo", "Player FM start")
                            onMediaEndReached(mediaItem)
                            Log.e("MediaInfo", "Player FM stop")
                        }, mediaItem.duration!!.toLong() * 1000 - currentTimeStamp * 1000
                    )
//                    }
                }

                else -> {
                    Log.e(LOGTAG, "Player not in live and file")
                    playNextMedia()
                }

            }
            Log.e(LOGTAG, "Player ${Looper.getMainLooper().isCurrentThread}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playLiveStream(mediaItem: MediaFile, isForce: Boolean, timeStamp: Int) {
        val resumeLiveTime = if (isForce) calculateResumeLivePoint(mediaItem) else timeStamp
        Log.d(LOGTAG, "playLiveStream resume time: $resumeLiveTime")
        currentPlayer = if (mediaItem.url!!.contains("m3u8")) {
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
                val mediaDuration = media.duration!!

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

    private fun calculateResumeLivePoint(mediaItem: MediaFile): Int {
//        //với force media, fixtime dc gán là start time
//        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
//        val now = Calendar.getInstance()
//        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
        return 0
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
//        if (playMode != PLAY_MODE_FORCE) return

        stopAllPlayer()
        onMediaStopPlaying()
        playerManagerListener?.onEndForceMedia()
//        playerManagerListener?.onTimeOver()
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
     * **/
    fun onPlayForceCommand(
        context: Context, responseHub: ResponseHub, mediaScheduler: MediaScheduler
    ) {
        try {
            val duration = responseHub.duration
            val listMediaFile = listMediaFileItemAdapter.fromJson(responseHub.message!!)
            val type = responseHub.type
            val level = responseHub.level
            //Play
            playForceMedia(context, duration, listMediaFile!!, mediaScheduler = mediaScheduler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun playForceMedia(
        context: Context,
        duration: Int,
        listForceMedia: List<MediaFile>,
        mediaScheduler: MediaScheduler
    ) {
        try {
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm(duration)

            playMode = PLAY_MODE_FORCE
            stopAllPlayer()

            currentPlaylist = listForceMedia

            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

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
        var currentPlaylist = listOf<MediaFile>()
        var handlerLivePlayer: Handler? = null
    }
}