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
    private var currentPlayingMediaInfo: MediaInfo? = null
    private var currentTimeFrame: String? = null
    private var countErrorCurrentMediaPlaying = 0
    private var MAX_RETRY = 5

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
        playerManagerListener?.onPlayError(mediaItem)
        countErrorCurrentMediaPlaying++
        if (countErrorCurrentMediaPlaying > MAX_RETRY) {
            Log.e("MediaInfo", "Toàn bộ playlist đều lỗi. Dừng phát để tránh treo app.")
            stopAllPlayer("onPlayerError")
            countErrorCurrentMediaPlaying = 0
            return
        }
        //Case: sẽ cố play tiếp
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("MediaInfo", "Thử chuyển sang bài tiếp theo sau khi lỗi...")
            playNextMedia()
        }, 5000) // Nghỉ 3 giây giữa các lần thử lại
    }

    override fun onPlayMediaSuccess(mediaItem: MediaFile?) {
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaFile?) {
        onMediaStopPlaying()
        playNextMedia()
    }


    private fun onMediaStopPlaying() {
        playerManagerListener?.onEndMedia(currentPlayingMedia)
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
//                setTimeFrame(period)
//                found = true
//                break

                // KỂM TRA LOGIC LIỀN MẠCH
                found = true
                if (currentTimeFrame == period) {
                    // Case 2: Đang phát playlist này, server cập nhật thêm playlist khác
                    // Khung giờ này vẫn tồn tại -> KHÔNG LÀM GÌ CẢ (để player tự chạy tiếp)
                    Log.d(
                        "MediaInfo",
                        "Đang phát khung giờ $period, tiếp tục phát không ngắt quãng."
                    )
//                    val resumePoint = calculateResumePoint(start, period)
//                    setTimeFrame(period, resumePoint)

                } else {
                    // Là khung giờ hoàn toàn mới, hoặc trước đó thiết bị đang nghỉ ngơi
                    Log.d("MediaInfo", "Bắt đầu khung giờ mới: $period")
                    val resumePoint = calculateResumePoint(start, period)
                    setTimeFrame(period, resumePoint)
                }
                break
            }
        }
        if (!found) {
            Log.d("MediaInfo", "Không có khung giờ hợp lệ -> Dừng thiết bị")
            if (playMode != PLAY_MODE_INIT && playMode != PLAY_MODE_FORCE) {
                Log.e(
                    "MediaInfo",
                    "stopAllPlayer reason=getPlay playMode=$playMode current=${currentPlayingMedia?.id}"
                )
                stopPlaylist(playMode)
                resetPlayMode()
            }
        }
    }


    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {

        stopAllPlayer("setTimeFrame")

        playMode = timeFrame
        currentTimeFrame = timeFrame

        currentPlaylist = mediaRepo.scheduledList[timeFrame]?.files ?: listOf()

        currentPlayingMediaInfo = mediaRepo.scheduledList[timeFrame]


        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            Log.d("MediaInfo", "resumePoint index : ${resumePoint.first}")
            Log.d("MediaInfo", "resumePoint second: ${resumePoint.second}")
            playMediaByIndex(playlistIndex, resumePoint.second)

        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }
        //playlistIndex = 0
        //playMediaByIndex(playlistIndex)

        isTimeOver = false
    }

    fun playNextMedia() {
        if (currentPlaylist.isEmpty()) return

        playlistIndex++

        if (playlistIndex >= currentPlaylist.size) {
            playlistIndex = 0
            // Nếu bài đầu tiên là nhạc hiệu (ID=0), ta bỏ qua để vào nội dung chính luôn
            if (currentPlaylist[0].id == 0L && currentPlaylist.size > 1) {
                playlistIndex = 1
            }
            Log.d("MediaInfo", "Quay lại đầu Playlist: loop")
        }

        playMediaByIndex(playlistIndex)
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
                    if (timeStamp > 0) currentTimeStamp = timeStamp
                    playerManagerListener?.onPlayTuner(mediaItem)
                    Log.e("MediaInfo", "Player FM start")
                    handlerFMPlayer?.removeCallbacksAndMessages(null)
                    handlerFMPlayer = Handler(Looper.getMainLooper())
                    handlerFMPlayer?.postDelayed(
                        {
                            onMediaEndReached(mediaItem)
                            Log.e("MediaInfo", "Player FM stop")
                        }, mediaItem.duration!!.toLong() * 1000 - currentTimeStamp * 1000
                    )
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

    fun stopAllPlayer(reason: String = "unknown") {
        Log.e(
            "MediaInfo",
            "stopAllPlayer reason=$reason playMode=$playMode current=${currentPlayingMedia?.id}"
        )
        handlerFMPlayer?.removeCallbacksAndMessages(null)
        handlerFMPlayer = null
//        currentPlayingMedia = null
//        currentPlayingMediaInfo = null
        currentTimeFrame = null
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
    fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        return try {
            val mediaList = mediaRepo.scheduledList[timeFrame]?.files ?: return Pair(0, 0)
            if (mediaList.isEmpty()) return Pair(0, 0)

            val now = Calendar.getInstance()
            val secondFromStart =
                ((now.timeInMillis - start.timeInMillis) / 1000L).coerceAtLeast(0L)

            // Nếu playlist lặp liên tục thì dùng modulo tổng duration
            val totalDuration = mediaList.sumOf { (it.duration ?: 0).toLong() }
            if (totalDuration <= 0L) return Pair(0, 0)

            val playTime = secondFromStart % totalDuration

            var acc = 0L
            for (i in mediaList.indices) {
                val mediaDuration = (mediaList[i].duration ?: 0).toLong()
                if (mediaDuration <= 0L) continue

                if (playTime < acc + mediaDuration) {
                    val resumeSecond = (playTime - acc).toInt()
                    return Pair(i, resumeSecond)
                }

                acc += mediaDuration
            }

            Pair(0, 0)
        } catch (e: Exception) {
            Log.e(LOGTAG, "Error calculate resume", e)
            Pair(0, 0)
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
        stopAllPlayer("stopPlaylist")
        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return
        onMediaStopPlaying()
        stopAllPlayer("stopForceMedia")
        playerManagerListener?.onEndForceMedia(currentPlayingMediaInfo)
//        playerManagerListener?.onTimeOver()
    }

    fun stopMediaInfo(mediaInfo: MediaInfo?) {
        if (mediaInfo == null) return
        Log.e(
            "MediaInfo",
            "stopMediaInfo: ${mediaInfo.mid}"
        )
        Log.e(
            "MediaInfo",
            "stopMediaInfo: current ${currentPlayingMediaInfo?.mid}"
        )
        if (mediaInfo.mid == currentPlayingMediaInfo?.mid) {
            if (mediaInfo.mode == 2) {
                Log.e(
                    "MediaInfo",
                    "stopMediaInfo: stop force"
                )
                stopForceMedia()
            } else if (mediaInfo.mode == 1) {
                onMediaStopPlaying()
                stopAllPlayer("stopMediaInfo")
            }

        }
    }

    /**
     * Nhận lệnh phát khẩn cấp từ hub, chuyen json "name" sang media va convert sang force media, tính thời điểm bắt đầu và kết thúc của force media
     * **/
    fun onPlayForceCommand(
        appContext: Context, mediaInfo: MediaInfo, mediaScheduler: MediaScheduler
    ) {
        try {
            if (currentPlayingMediaInfo?.mid == mediaInfo.mid) {
                Log.d("MediaInfo", "Live đang phát. Không phát đè")
                return
            }

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

//                    val endTime = ts.toInt() + durationSlot[index].toInt()
                    val calendar = Calendar.getInstance()
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    val minutes = calendar.get(Calendar.MINUTE)
                    val seconds = calendar.get(Calendar.SECOND)

                    val nowSecondsOfDay = (hours * 3600) + (minutes * 60) + seconds
                    val duration = durationSlot.getOrNull(index)?.toInt() ?: 0
                    val endTime = nowSecondsOfDay + duration
                    mediaInfo.endTime = endTime
                    saveForceMediaInfoToPref(appContext, mediaInfoAdapter.toJson(mediaInfo))
                    playForceMedia(
                        endTime, mediaInfo, mediaScheduler = mediaScheduler
                    )

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
        endTime: Int?, mediaInfo: MediaInfo, mediaScheduler: MediaScheduler
    ) {
        try {
            if (playMode == PLAY_MODE_FORCE) return
            Log.d("aaa", "force")
            //Hủy hẹn giờ của kịch bản đang phát
            mediaScheduler.cancelAllAlarm()

            //hẹn giờ dừng media khẩn cấp
            mediaScheduler.scheduleForceMediaAlarm(endTime)

            playMode = PLAY_MODE_FORCE

            stopAllPlayer("playForceMedia")
            currentPlayingMediaInfo = mediaInfo
            currentPlaylist = mediaInfo.files

            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityStop() {
        stopAllPlayer("onActivityStop")
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
        var currentPlaylist = listOf<MediaFile>()
        var handlerLivePlayer: Handler? = null
    }
}