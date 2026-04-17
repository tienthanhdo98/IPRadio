package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.mediaInfoAdapter
import app.tek4tv.ipradio.model.ForceMedia.Companion.saveForceMediaInfoToPref
import app.tek4tv.ipradio.model.ForceProgressState
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
import kotlin.math.ceil

class PlayersManager(
    appContext: Context,
    private val mediaRepo: MediaRepo,
    private val mediaScheduler: MediaScheduler
) : IPlayerEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

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

    //Live
    private var forceTargetLoopCount = 0
    private var forceCompletedLoopCount = 0
    private var forceDurationSeconds = 0L

    private var forceSessionStartEpochSec = 0L
    private var forceTotalLoopDurationSec = 0

    //Lịch
    private var pendingTimeFrame: String? = null
    private var endingTimeFrame: String? = null

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

//    override fun onMediaEndReached(mediaItem: MediaFile?) {
//        onMediaStopPlaying()
//        playNextMedia()
//    }

    override fun onMediaEndReached(mediaItem: MediaFile?) {
        onMediaStopPlaying()

        if (playMode == PLAY_MODE_FORCE) {
            playNextMedia()
            return
        }
        resolveAfterCurrentMediaEnded()
    }

    private fun resolveAfterCurrentMediaEnded() {
        val now = System.currentTimeMillis()
        val resolvedPeriodKey = mediaScheduler.getActivePeriodKey(now)
        val currentPeriodKey = currentTimeFrame

        Log.d(
            "MediaInfo",
            "resolveAfterCurrentMediaEnded current=$currentPeriodKey resolved=$resolvedPeriodKey pending=$pendingTimeFrame ending=$endingTimeFrame"
        )

        when {
            resolvedPeriodKey == null -> {
                Log.d("MediaInfo", "Không còn lịch active sau khi media kết thúc -> stop")
                stopAllPlayer("noActiveScheduleAfterMediaEnded")
                resetPlayMode()
                isTimeOver = true
                checkTimeOver()
            }

            resolvedPeriodKey != currentPeriodKey -> {
                Log.d(
                    "MediaInfo", "Chuyển sang lịch mới sau khi media kết thúc: $resolvedPeriodKey"
                )
                startTimeFrameFromBoundary(resolvedPeriodKey)
            }

            else -> {
                Log.d("MediaInfo", "Vẫn trong cùng lịch hiện tại -> play next media")
                playNextMedia()
            }
        }
    }

    private fun startTimeFrameFromBoundary(timeFrame: String) {
        Log.d("MediaInfo", "startTimeFrameFromBoundary: $timeFrame")
        setTimeFrame(timeFrame, null)
    }

    private fun startTimeFrameResolved(timeFrame: String) {
        val t = timeFrame.split("-")
        if (t.size != 2) return

        val start = toCalendar(t[0])
        val resumePoint = calculateResumePoint(start, timeFrame)
        setTimeFrame(timeFrame, resumePoint)
    }

    fun markPlaylistEnd(listKey: String) {
        if (listKey != currentTimeFrame) return

        Log.d("MediaInfo", "markPlaylistEnd current=$currentTimeFrame")
        endingTimeFrame = listKey
    }

    fun onScheduleStart(listKey: String) {
        Log.d(
            "MediaInfo",
            "onScheduleStart listKey=$listKey current=$currentTimeFrame playMode=$playMode"
        )

        if (playMode == PLAY_MODE_FORCE) {
            Log.d("MediaInfo", "Force đang phát -> bỏ qua schedule start")
            return
        }

        if (currentTimeFrame == listKey) {
            Log.d("MediaInfo", "Schedule start trùng timeframe hiện tại -> bỏ qua")
            return
        }

        if (currentTimeFrame == null || currentPlayingMedia == null) {
            Log.d("MediaInfo", "Đang idle -> start từ đầu timeframe mới")
            setTimeFrameFromStart(listKey)
            return
        }

        // đang phát lịch cũ, chỉ ghi nhận pending
        pendingTimeFrame = listKey
        Log.d("MediaInfo", "Đang phát dở -> pending timeframe mới: $pendingTimeFrame")
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
                        "MediaInfo", "Đang phát khung giờ $period, tiếp tục phát không ngắt quãng."
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
            playerManagerListener?.onTimeOver()
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
        pendingTimeFrame = null
        endingTimeFrame = null

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

        isTimeOver = false
    }
//    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
//
//        stopAllPlayer("setTimeFrame")
//
//        playMode = timeFrame
//        currentTimeFrame = timeFrame
//
//        currentPlaylist = mediaRepo.scheduledList[timeFrame]?.files ?: listOf()
//
//        currentPlayingMediaInfo = mediaRepo.scheduledList[timeFrame]
//
//
//        if (resumePoint != null) {
//            playlistIndex = resumePoint.first
//            Log.d("MediaInfo", "resumePoint index : ${resumePoint.first}")
//            Log.d("MediaInfo", "resumePoint second: ${resumePoint.second}")
//            playMediaByIndex(playlistIndex, resumePoint.second)
//
//        } else {
//            playlistIndex = 0
//            playMediaByIndex(playlistIndex)
//        }
//
//
//        isTimeOver = false
//    }

    fun playNextMedia() {
        if (playMode == "") return
        if (currentPlaylist.isEmpty()) return

        if (playMode != PLAY_MODE_FORCE) {
            val now = System.currentTimeMillis()
            val resolvedPeriodKey = mediaScheduler.getActivePeriodKey(now)

            when {
                resolvedPeriodKey == null -> {
                    Log.d("MediaInfo", "playNextMedia: không còn lịch active -> stop")
                    stopAllPlayer("playNextMedia_noActiveSchedule")
                    resetPlayMode()
                    isTimeOver = true
                    checkTimeOver()
                    return
                }

                resolvedPeriodKey != currentTimeFrame -> {
                    Log.d(
                        "MediaInfo",
                        "playNextMedia: lịch active đã đổi từ $currentTimeFrame sang $resolvedPeriodKey"
                    )
                    startTimeFrameFromBoundary(resolvedPeriodKey)
                    return
                }
            }
        }

        if (playMode == PLAY_MODE_FORCE) {
            val mediaInfo = currentPlayingMediaInfo
            if (mediaInfo != null) {
                val progress = calculateForceProgress(mediaInfo)
                if (progress.isExpired) {
                    Log.d("MediaInfo", "Force media expired by elapsed time")
                    stopForceMedia()
                    return
                }
            }
        }

        playlistIndex++

        if (playlistIndex >= currentPlaylist.size) {
            playlistIndex = 0

            if (playMode == PLAY_MODE_FORCE) {
                forceCompletedLoopCount++

                Log.d(
                    "MediaInfo",
                    "Force loop completed: $forceCompletedLoopCount/$forceTargetLoopCount"
                )

                if (forceCompletedLoopCount >= forceTargetLoopCount) {
                    Log.d("MediaInfo", "Force media finished by completed loop count")
                    stopForceMedia()
                    return
                }
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
//                        vlcPlayer.playMedia(mediaItem, false, timeStamp)
//                        currentPlayer = vlcPlayer
                        exoPlayer.playMedia(mediaItem, false, timeStamp)
                        currentPlayer = exoPlayer
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
        exoPlayer.playMedia(mediaItem, isForce, resumeLiveTime)
        currentPlayer = exoPlayer

    }

    //    fun stopAllPlayer(reason: String = "unknown") {
//        Log.e(
//            "MediaInfo",
//            "stopAllPlayer reason=$reason playMode=$playMode current=${currentPlayingMedia?.id}"
//        )
//        handlerFMPlayer?.removeCallbacksAndMessages(null)
//        handlerFMPlayer = null
////        currentPlayingMedia = null
////        currentPlayingMediaInfo = null
//        currentTimeFrame = null
//        exoPlayer.stop()
//    }
    fun stopAllPlayer(reason: String = "unknown") {
        Log.e(
            "MediaInfo",
            "stopAllPlayer reason=$reason playMode=$playMode current=${currentPlayingMedia?.id}"
        )
        handlerFMPlayer?.removeCallbacksAndMessages(null)
        handlerFMPlayer = null
        currentTimeFrame = null
        currentPlayingMedia = null
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

    //    private fun calculateResumeLivePoint(mediaItem: MediaFile): Int {
////        //với force media, fixtime dc gán là start time
////        val startTimeOfMedia = toCalendar(mediaItem.fixTime!!)
////        val now = Calendar.getInstance()
////        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
//        return 0
//    }
    private fun calculateResumeLivePoint(mediaItem: MediaFile): Int {
        if (playMode != PLAY_MODE_FORCE) return 0

        val mediaInfo = currentPlayingMediaInfo ?: return 0
        val progress = calculateForceProgress(mediaInfo)

        if (progress.isExpired) return 0

        val currentIndex = currentPlaylist.indexOfFirst { it.id == mediaItem.id }
        if (currentIndex < 0) return 0

        return if (currentIndex == progress.itemIndex) {
            progress.itemOffsetSec
        } else {
            0
        }
    }

    //    fun stopPlaylist(cancelListKey: String) {
//        if (cancelListKey != playMode) return
//        playMode = ""
//
//        stopAllPlayer("stopPlaylist")
//        //check time over
//        isTimeOver = true
//        checkTimeOver()
//    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return

        Log.d("MediaInfo", "stopPlaylist soft-end for $cancelListKey")
        markPlaylistEnd(cancelListKey)
    }

    fun setTimeFrameFromStart(timeFrame: String) {
        setTimeFrame(timeFrame, null)
    }

    fun setTimeFrameWithResume(timeFrame: String) {
        val t = timeFrame.split("-")
        if (t.size != 2) {
            setTimeFrame(timeFrame, null)
            return
        }

        val start = toCalendar(t[0])
        val resumePoint = calculateResumePoint(start, timeFrame)
        setTimeFrame(timeFrame, resumePoint)
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return
        onMediaStopPlaying()
        stopAllPlayer("stopForceMedia")
        resetForceLoopState()
        playerManagerListener?.onEndForceMedia(currentPlayingMediaInfo)
//        playerManagerListener?.onTimeOver()
    }

    fun stopMediaInfo(mediaInfo: MediaInfo?) {
        if (mediaInfo == null) return
        Log.e(
            "MediaInfo", "stopMediaInfo: ${mediaInfo.mid}"
        )
        Log.e(
            "MediaInfo", "stopMediaInfo: current ${currentPlayingMediaInfo?.mid}"
        )
        if (mediaInfo.mid == currentPlayingMediaInfo?.mid) {
            if (mediaInfo.mode == 2) {
                Log.e(
                    "MediaInfo", "stopMediaInfo: stop force"
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
                Log.d(
                    "MediaInfo",
                    "Live đang phát. Không phát đè ${currentPlayingMediaInfo?.mid} - ${mediaInfo.mid}"
                )
                return
            }
            //val start = mediaInfo.start ?: return
            val expired = mediaInfo.expired ?: return
            val now = Calendar.getInstance()
            Log.d("MediaInfo", "Mode: Live ")
            Log.d(
                "MediaInfo", "Mode: Bản tin ${mediaInfo.summary}"
            )
//            val startTime = unixSecondsToCalendar(start)
//            Log.d(
//                "MediaInfo", "startTime: ${mediaInfo.start} ${
//                    convertCalendarToString(
//                        startTime
//                    )
//                }"
//            )
//            if (now.before(startTime)) {
//                Log.d("MediaInfo", "Chưa đến giờ phát (start)")
//                return
//            }
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
                playerManagerListener?.onForceMediaExpired(mediaInfo)
                return // thoát khỏi function xử lý mediaInfo luôn
            }
            val timeSlot = mediaInfo.ts
            val durationSlot = mediaInfo.ds
            var shouldPlay = false
            //xem lại live có cần phải check ds. ts ko..
            if (timeSlot != null && durationSlot != null && durationSlot.size == timeSlot.size) {
                timeSlot.forEachIndexed { index, ts ->
                    if (shouldPlay) return@forEachIndexed
                    shouldPlay = true

                    val calendar = Calendar.getInstance()
                    val hours = calendar.get(Calendar.HOUR_OF_DAY)
                    val minutes = calendar.get(Calendar.MINUTE)
                    val seconds = calendar.get(Calendar.SECOND)

                    val nowSecondsOfDay = (hours * 3600) + (minutes * 60) + seconds
                    val duration = durationSlot.getOrNull(index)?.toInt() ?: 0
                    val endTime = nowSecondsOfDay + duration
//                    mediaInfo.endTime = endTime
                    val sessionStartEpochSec = System.currentTimeMillis() / 1000L
                    mediaInfo.start = sessionStartEpochSec
                    mediaInfo.endTime = 0
                    saveForceMediaInfoToPref(appContext, mediaInfoAdapter.toJson(mediaInfo))
                    playForceMedia(
                        mediaInfo, mediaScheduler = mediaScheduler
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
        mediaInfo: MediaInfo, mediaScheduler: MediaScheduler
    ) {
        try {
            if (playMode == PLAY_MODE_FORCE) return

            Log.d("aaa", "force")
            mediaScheduler.cancelAllAlarm()

            playMode = PLAY_MODE_FORCE
            stopAllPlayer("playForceMedia")

            currentPlayingMediaInfo = mediaInfo
            currentPlaylist = mediaInfo.files

            prepareForceLoopState(mediaInfo)

            val progress = calculateForceProgress(mediaInfo)
            if (progress.isExpired) {
                Log.d("MediaInfo", "playForceMedia: force session expired before play")
                stopForceMedia()
                return
            }

            forceCompletedLoopCount = progress.completedLoops
            playlistIndex = progress.itemIndex

            Log.d(
                "MediaInfo",
                "playForceMedia progress loops=$forceCompletedLoopCount/$forceTargetLoopCount index=$playlistIndex offset=${progress.itemOffsetSec}"
            )

            playMediaByIndex(playlistIndex, progress.itemOffsetSec)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun prepareForceLoopState(mediaInfo: MediaInfo) {

        val playlist = mediaInfo.files
        val totalLoopDuration = playlist.sumOf { it.duration ?: 0 }

        forceDurationSeconds = mediaInfo.ds?.firstOrNull() ?: 0
        forceTotalLoopDurationSec = totalLoopDuration
        forceSessionStartEpochSec = mediaInfo.start ?: 0L

        forceTargetLoopCount = when {
            playlist.isEmpty() -> 0
            totalLoopDuration <= 0 -> 1
            forceDurationSeconds <= 0 -> 1
            else -> ceil(forceDurationSeconds.toDouble() / totalLoopDuration.toDouble()).toInt()
        }

        forceCompletedLoopCount = 0

        Log.d(
            "MediaInfo",
            "prepareForceLoopState totalLoopDuration=$totalLoopDuration forceDurationSeconds=$forceDurationSeconds forceTargetLoopCount=$forceTargetLoopCount start=$forceSessionStartEpochSec"
        )
    }

    private fun calculateForceProgress(mediaInfo: MediaInfo): ForceProgressState {
        val playlist = mediaInfo.files
        if (playlist.isEmpty()) {
            return ForceProgressState(
                isExpired = true, completedLoops = 0, itemIndex = 0, itemOffsetSec = 0
            )
        }

        val sessionStart = mediaInfo.start ?: 0L
        if (sessionStart <= 0L) {
            return ForceProgressState(
                isExpired = true, completedLoops = 0, itemIndex = 0, itemOffsetSec = 0
            )
        }

        val totalLoopDuration = playlist.sumOf { it.duration ?: 0 }
        if (totalLoopDuration <= 0) {
            return ForceProgressState(
                isExpired = true, completedLoops = 0, itemIndex = 0, itemOffsetSec = 0
            )
        }

        val durationSec = mediaInfo.ds?.firstOrNull() ?: 0
        if (durationSec <= 0) {
            return ForceProgressState(
                isExpired = true, completedLoops = 0, itemIndex = 0, itemOffsetSec = 0
            )
        }

        val targetLoopCount = ceil(durationSec.toDouble() / totalLoopDuration.toDouble()).toInt()
        val totalPlaybackWindowSec = targetLoopCount * totalLoopDuration

        val nowEpochSec = System.currentTimeMillis() / 1000L
        val elapsedSec = (nowEpochSec - sessionStart).toInt().coerceAtLeast(0)

        if (elapsedSec >= totalPlaybackWindowSec) {
            return ForceProgressState(
                isExpired = true, completedLoops = targetLoopCount, itemIndex = 0, itemOffsetSec = 0
            )
        }

        val completedLoops = elapsedSec / totalLoopDuration
        val elapsedInCurrentLoop = elapsedSec % totalLoopDuration

        var acc = 0
        for (i in playlist.indices) {
            val dur = playlist[i].duration ?: 0
            if (dur <= 0) continue

            if (elapsedInCurrentLoop < acc + dur) {
                return ForceProgressState(
                    isExpired = false,
                    completedLoops = completedLoops,
                    itemIndex = i,
                    itemOffsetSec = elapsedInCurrentLoop - acc
                )
            }
            acc += dur
        }

        return ForceProgressState(
            isExpired = false, completedLoops = completedLoops, itemIndex = 0, itemOffsetSec = 0
        )
    }

    fun playMediaInfoTest(mediaInfo: MediaInfo) {

        stopAllPlayer("playMediaInfoTest")

        currentPlayingMediaInfo = mediaInfo
        currentPlaylist = mediaInfo.files

        playlistIndex = 0
        playMode = PLAY_MODE_TEST
        playMediaByIndex(playlistIndex)
    }

    private fun resetForceLoopState() {
        forceTargetLoopCount = 0
        forceCompletedLoopCount = 0
        forceDurationSeconds = 0
        forceSessionStartEpochSec = 0L
        forceTotalLoopDurationSec = 0
    }

    fun onActivityStop() {
        stopAllPlayer("onActivityStop")
    }

    fun onActivityDestroy() {
        exoPlayer.release()
    }


    fun resetPlayMode() {
        playMode = PLAY_MODE_INIT//"0"
        currentPlayingMedia = null
        currentPlayingMediaInfo = null
        resetForceLoopState()
    }

    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_TEST = "-1"
        const val PLAY_MODE_INIT = "0"
        const val LOGTAG = "player"
        var currentPlaylist = listOf<MediaFile>()
    }
}