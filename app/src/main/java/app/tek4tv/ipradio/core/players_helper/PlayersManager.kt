package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaInfoAdapter
import app.tek4tv.ipradio.model.ForceMedia.Companion.saveForceMediaInfoToPref
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.model.MediaFileType
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils.Companion.convertSecondsToCalendar
import app.tek4tv.ipradio.utils.convertCalendarToString
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.utils.unixSecondsToCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class PlayersManager(
    appContext: Context, private val mediaRepo: MediaRepo
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val vlcPlayer: IMediaPlayer = VlcMediaPlayer(appContext, this)
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this)
    private var currentPlayer: IMediaPlayer? = null
    private var currentPlayingMedia: MediaFile? = null

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
    //MWG
    fun getPlay() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val now = Calendar.getInstance().timeInMillis
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            if (t.size != 2) continue
            val start = toCalendar(t[0]) //start time
            val end = toCalendar(t[1]) //end time

            if (now in start.timeInMillis..end.timeInMillis) {
                setTimeFrame(period)
                found = true
                break
            }
        }
        if (!found) {
            Log.d("MediaInfo", "Không có khung nào phát hiện tại")
        }
    }


    fun setTimeFrame(timeFrame: String) {

        stopAllPlayer()

        playMode = timeFrame

        currentPlaylist = mediaRepo.scheduledList[timeFrame] ?: listOf()

        playlistIndex = 0

        playMediaByIndex(playlistIndex)

        isTimeOver = false
    }

    fun playNextMedia() {
        if (playMode == "") return
        playlistIndex++
        if (playlistIndex < currentPlaylist.size) {
            Log.d("MediaInfo", "Play media phat tiep")
            playMediaByIndex(playlistIndex)
        } else {
            //Ko phát lại nhạc hiệu. chỉ phát lần đầu. Nhạch hiệu Id = 0/ index = 0
            if (currentPlayingMedia?.id == 0L) {
                playlistIndex = 1
                playMediaByIndex(playlistIndex)
            }
            //Ko có nhạc hiệu
            else {
                playlistIndex = 0
                playMediaByIndex(playlistIndex)
            }


        }
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
                    if (mediaItem.id == 0L) {
                        playLiveStream(mediaItem, false, timeStamp)
                    } else {
                        playLiveStream(mediaItem, true, timeStamp)
                    }

                }

                MediaFileType.FM -> {
                    Log.d("MediaInfo", "playMedia: fm: ${mediaItem.duration}")
                    var currentTimeStamp = 0
                    if (timeStamp != -1) currentTimeStamp = timeStamp
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
        currentPlayingMedia = null
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
        appContext: Context, mediaInfo: MediaInfo, mediaScheduler: MediaScheduler
    ) {
        try {
            val start = mediaInfo.start ?: return
            val expired = mediaInfo.expired ?: return
            Log.d("MediaInfo", "Mode: Live ")
            Log.d(
                "MediaInfo", "Mode: Bản tin ${mediaInfo.summary}"
            )
            val now = Calendar.getInstance()
            val startTime = unixSecondsToCalendar(start)
            Log.d(
                "MediaInfo", "startTime: ${mediaInfo.start} ${
                    convertCalendarToString(
                        startTime
                    )
                }"
            )
            val expiredTime = unixSecondsToCalendar(expired)
            Log.d(
                "MediaInfo", "expiredTime: ${mediaInfo.expired} ${
                    convertCalendarToString(
                        expiredTime
                    )
                }"
            )
            if (!now.before(expiredTime)) { // now >= expiredTime
                Log.d("MediaInfo", "Đã hết giờ phát (expired)")
                return // thoát khỏi function xử lý mediaInfo luôn
            }
            if (now.before(startTime)) {
                Log.d("MediaInfo", "Chưa đến giờ phát (start)")
                return
            }
            val timeSlot = mediaInfo.ts
            val durationSlot = mediaInfo.ds
            var shouldPlay = false
            //xem lại live có cần phải check ds. ts ko..
            if (timeSlot != null && durationSlot != null && durationSlot.size == timeSlot.size) {
                timeSlot.forEachIndexed { index, ts ->
                    if (shouldPlay) return@forEachIndexed
                    //KO check, phat ngay khi nhận
                    shouldPlay = true

                    saveForceMediaInfoToPref(appContext, mediaInfoAdapter.toJson(mediaInfo))

                    val endTime = ts.toInt() + durationSlot[index].toInt()
                    playForceMedia(
                        endTime, mediaInfo.files, mediaScheduler = mediaScheduler
                    )

                    //Check start time
//                    val slotStart = convertSecondsToCalendar(ts.toInt())
//                    Log.d(
//                        "MediaInfo", "slotStart: ${convertCalendarToString(slotStart)}"
//                    )
//                    val slotEnd = convertSecondsToCalendar(ts.toInt()).apply {
//                        add(
//                            Calendar.SECOND, durationSlot[index].toInt()
//                        )
//                    }
//                    Log.d(
//                        "MediaInfo", "slotEnd: ${convertCalendarToString(slotEnd)}"
//                    )
//
//                    when {
//                        now < slotStart -> {
//                            Log.d(
//                                "MediaInfo", "Chưa đến khung giờ phát"
//                            )
//                        }
//
//                        now >= slotEnd -> {
//                            Log.d("MediaInfo", "Đã hết khung phát")
//                        }
//
//                        else -> { // slotStart <= now < slotEnd, và đã qua start, chưa expired
//                            Log.d(
//                                "MediaInfo", "Đang phát trong khung: ${
//                                    convertCalendarToString(
//                                        slotStart, "HH:mm:ss"
//                                    )
//                                } - ${
//                                    convertCalendarToString(
//                                        slotEnd, "HH:mm:ss"
//                                    )
//                                } duration=${
//                                    durationSlot.getOrNull(index)
//                                }"
//                            )
//                            // nếu chỉ phát 1 slot thì return/break ở đây
//                            shouldPlay = true
//
//                            saveForceMediaInfoToPref(appContext, mediaInfoAdapter.toJson(mediaInfo))
//
//                            val endTime = ts.toInt() + durationSlot[index].toInt()
//                            playForceMedia(
//                                endTime, mediaInfo.files, mediaScheduler = mediaScheduler
//                            )
//                            return
//                        }
//                    }
                }
            } else {
                Log.d(
                    "MediaInfo", "Giờ phát hoặc thời lượng không hợp lệ"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun playForceMedia(
        endTime: Int, listForceMedia: List<MediaFile>, mediaScheduler: MediaScheduler
    ) {
        try {
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm(endTime)

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