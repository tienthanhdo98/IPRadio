package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.core.constants.AppConstants.FULL_SCREEN
import app.tek4tv.ipradio.core.constants.AppConstants.LEFT_AREA
import app.tek4tv.ipradio.core.constants.AppConstants.RIGHT_AREA
import app.tek4tv.ipradio.core.scheduler.MediaScheduler
import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonListMediaItemAdapter
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.ForceMedia.Companion.saveListForceToPref
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager all media playback
 * 2 Mode: Scheduled and force media
 * Scheduled: Play media in a fixed scheduled
 * Force Media: Play media by command from server
 * **/
class PlayersManager(
    appContext: Context,
    imageLayout: ImageView? = null,
    private val mediaRepo: MediaRepo,
    exoVideoLayout: PlayerView
) : IPlayerEventListener {

    var TAG = "PlayersManager"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exoPlayer: IMediaPlayer = ExoMediaPlayer(appContext, this, exoVideoLayout)
    private var currentPlayingMedia: MediaItem? = null

    // Quản lý số lần lỗi liên tiếp của từng media item trong lịch hiện tại
    private val itemFailCountMap = ConcurrentHashMap<String, Int>()
    private val skippedItemKeys = ConcurrentHashMap.newKeySet<String>()

    private val scheduleRecoveryRunnable = Runnable {
        Log.i(TAG, "Auto-recovery: Thử kiểm tra lại kịch bản sau khi bị lỗi toàn bộ...")
        itemFailCountMap.clear()
        skippedItemKeys.clear()
        startFirstTimeFrame()
    }

    private fun getMediaKey(item: MediaItem?): String {
        if (item == null) return "unknown"
        return item.body?.takeIf { it.isNotBlank() }
            ?: item.pathBackup.takeIf { it.isNotBlank() }
            ?: item.title?.takeIf { it.isNotBlank() }
            ?: "item_${item.hashCode()}"
    }

    /**
     * Timestamp (ms) khi bắt đầu phát ảnh tĩnh, dùng để tính tiến trình phát hiện tại
     */
    var imagePlaybackStartMs = 0L
        private set

    /**
     * -2 for force media
     * 0 for init
     * HH:mm:ss-HH:mm:ss for scheduled timeframe
     * **/
    var playMode = PLAY_MODE_INIT

    /**
     * List of media for a timeframe
     * **/
    private var currentPlaylist = listOf<MediaItem>()

    private var currentTextPlaylist = listOf<MediaItem>()

    var playlistIndex = 0
        private set

    var playerManagerListener: IPlayerManagerListener? = null

    /**
     * Determine if we will not play any media in >= 15s to go
     * **/
    var isTimeOver = false

    override fun onPlayerError(error: Exception, mediaItem: MediaItem) {
        Log.e(TAG, "onPlayerError: ${error.message} - item: ${mediaItem.title ?: mediaItem.body}")
        currentPlayingMedia = null
        playerManagerListener?.onMediaError(error.message ?: "Unknown media error", mediaItem)
        handleMediaPlaybackFailed(mediaItem, error.message ?: "playback_failed")
    }

    override fun onPlayMediaSuccess(mediaItem: MediaItem?) {
        mediaItem?.let {
            val key = getMediaKey(it)
            itemFailCountMap.remove(key)
            skippedItemKeys.remove(key)
        }
        playerManagerListener?.onPlayMediaSuccess(mediaItem)
    }

    override fun onMediaEndReached(mediaItem: MediaItem?) {
        playerManagerListener?.onMediaEndReach(mediaItem!!)
        playNextMedia()
    }

    override fun onMediaForceEndReached(mediaItem: MediaItem?) {
        Log.d(TAG, "onMediaForceEndReached")
        playNextMedia()
    }

    /**
     * Xử lý thông minh khi một item bị lỗi:
     * 1. Nếu kịch bản chỉ có <= 1 item: Bỏ kịch bản ngay để chống lặp đệ quy 170 lần/s làm đơ Main thread.
     * 2. Nếu kịch bản có nhiều item:
     *    - Thử lại tối đa 3 lần (mỗi lần cách 3s).
     *    - Sau 3 lần vẫn lỗi: đưa vào danh sách bỏ qua (skippedItemKeys).
     *    - Nếu tất cả các item trong lịch đều lỗi: Bỏ kịch bản về standby và hẹn giờ tự phục hồi sau 60s.
     *    - Nếu còn item khác khả dụng: nhảy sang item tiếp theo.
     */
    private fun handleMediaPlaybackFailed(mediaItem: MediaItem?, reason: String) {
        val item = mediaItem ?: currentPlaylist.getOrNull(playlistIndex)
        if (item == null || currentPlaylist.isEmpty()) {
            Log.w(TAG, "handleMediaPlaybackFailed: mediaItem is null or currentPlaylist is empty")
            return
        }

        val key = getMediaKey(item)
        val currentFails = (itemFailCountMap[key] ?: 0) + 1
        itemFailCountMap[key] = currentFails

        Log.e(TAG, "handleMediaPlaybackFailed: key=$key, failCount=$currentFails/3, reason=$reason")

        // 1. Nếu kịch bản chỉ có 1 item duy nhất và bị lỗi -> Bỏ luôn kịch bản
        if (currentPlaylist.size <= 1) {
            Log.e(TAG, "Kịch bản chỉ có 1 item duy nhất và đã bị lỗi -> Bỏ qua luôn để tránh Infinite Loop!")
            skippedItemKeys.add(key)
            abandonCurrentScheduleAndFallback()
            return
        }

        // 2. Nếu failCount < 3: Thử lại sau 3 giây
        if (currentFails < 3) {
            Log.w(TAG, "Item $key lỗi lần $currentFails/3. Thử lại sau 3 giây...")
            mainHandler.postDelayed({
                if (playMode.isNotEmpty() && currentPlayingMedia == null) {
                    playMedia(item)
                }
            }, 3000L)
            return
        }

        // 3. Nếu failCount >= 3: Bỏ qua item này
        Log.e(TAG, "Item $key đã lỗi liên tiếp $currentFails lần -> Bỏ qua item này!")
        skippedItemKeys.add(key)

        // 4. Kiểm tra xem toàn bộ playlist có bị lỗi hết không
        val allFailed = currentPlaylist.all { skippedItemKeys.contains(getMediaKey(it)) }
        if (allFailed) {
            Log.e(TAG, "Toàn bộ ${currentPlaylist.size} items trong lịch $playMode đều bị lỗi -> Bỏ luôn lịch này!")
            abandonCurrentScheduleAndFallback()
            return
        }

        // 5. Nếu vẫn còn item khác khả dụng: chuyển sang bài tiếp theo
        Log.i(TAG, "Vẫn còn item khả dụng trong lịch -> Chuyển sang bài tiếp theo")
        playNextPlayableMedia()
    }

    private fun playNextPlayableMedia() {
        if (currentPlaylist.isEmpty()) return

        var foundIndex = -1
        for (step in 1..currentPlaylist.size) {
            val nextIdx = (playlistIndex + step) % currentPlaylist.size
            val nextItem = currentPlaylist[nextIdx]
            if (!skippedItemKeys.contains(getMediaKey(nextItem))) {
                foundIndex = nextIdx
                break
            }
        }

        if (foundIndex != -1) {
            playlistIndex = foundIndex
            Log.i(TAG, "Chuyển sang item khả dụng tiếp theo tại index $playlistIndex: ${getMediaKey(currentPlaylist[playlistIndex])}")
            playMediaByIndex(playlistIndex)
        } else {
            Log.e(TAG, "Không còn item nào khả dụng trong playlist!")
            abandonCurrentScheduleAndFallback()
        }
    }

    private fun abandonCurrentScheduleAndFallback() {
        Log.w(TAG, "abandonCurrentScheduleAndFallback: playMode=$playMode -> Về trạng thái chờ và hẹn giờ phục hồi sau 60s")
        stopAllPlayer()
        currentPlayingMedia = null
        isTimeOver = true
        playerManagerListener?.onTimeOver()

        // Hẹn giờ tự phục hồi sau 60s để kiểm tra lại (sau khi file đã tải xong hoặc mạng hồi phục)
        mainHandler.removeCallbacks(scheduleRecoveryRunnable)
        mainHandler.postDelayed(scheduleRecoveryRunnable, 60_000L)
    }

    fun isIdle(): Boolean {
        return playMode == PLAY_MODE_IDLE
    }

    fun isPlaying(): Boolean {
        return playMode != PLAY_MODE_IDLE
    }

    fun getCurrentPlaybackPositionMs(): Long {
        val isImage = imagePlaybackStartMs > 0L
        return if (isImage) {
            (System.currentTimeMillis() - imagePlaybackStartMs).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    fun startLeftTimeFrame(timeFrame: String) {
        val listMediaLeft = mediaRepo.scheduledListLeft[timeFrame] ?: listOf()
        playerManagerListener?.onLeftMediaPlaying(listMediaLeft)
    }

    fun startFirstTimeFrame() {
        var found = false
        val scheduledList = mediaRepo.scheduledList
        Log.d(TAG, "startFirstTimeFrame: list: ${scheduledList.size}")
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])
            val now = Calendar.getInstance().timeInMillis
            val isInRange = if (start.after(end)) {
                now >= start.timeInMillis || now <= end.timeInMillis
            } else {
                now in start.timeInMillis..end.timeInMillis
            }
            if (isInRange) {
                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)
                startLeftTimeFrame(timeFrame = period)
                found = true
                Log.d(TAG, "startFirstTimeFrame: found, start $resumePoint")
                break
            }
        }
        if (!found) {
            Log.d(TAG, "startFirstTimeFrame: not found")
            playerManagerListener?.onTimeOver()
        }
    }

    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
        playMode = timeFrame
        itemFailCountMap.clear()
        skippedItemKeys.clear()
        mainHandler.removeCallbacks(scheduleRecoveryRunnable)

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
        if (playMode == "") return
        if (currentPlaylist.isEmpty()) return

        var foundIndex = -1
        for (step in 1..currentPlaylist.size) {
            val nextIdx = playlistIndex + step
            if (nextIdx >= currentPlaylist.size) {
                // Đã duyệt hết danh sách
                break
            }
            val nextItem = currentPlaylist[nextIdx]
            if (!skippedItemKeys.contains(getMediaKey(nextItem))) {
                foundIndex = nextIdx
                break
            }
        }

        if (foundIndex != -1) {
            playlistIndex = foundIndex
            playMediaByIndex(playlistIndex)
        } else {
            if (playMode == PLAY_MODE_FORCE) {
                stopForceMedia()
            } else {
                stopTimeFrame()
            }
        }
    }

    private fun playMediaByIndex(index: Int, timeStamp: Int = 0) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            playMedia(mediaItem, timeStamp)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseMedia() {
        currentPauseTimePlayer = Calendar.getInstance().timeInMillis
        exoPlayer.pause()
    }

    var currentPauseTimePlayer: Long = 0
    fun resumeMedia() {
        val now = Calendar.getInstance().timeInMillis
        val mediaDuration = getDurationInSecond(currentPlayingMedia?.duration ?: "00:00:15") * 1000
        val timeEnd = currentPauseTimePlayer + mediaDuration
        Log.d("CONTROL_MEDIA", "resume: now $now")
        Log.d("CONTROL_MEDIA", "resume: current pause$currentPauseTimePlayer")
        if (now < timeEnd) {
            Log.d("CONTROL_MEDIA", "resume: $timeEnd")
            exoPlayer.resume()
        } else {
            if (currentPauseTimePlayer != 0L) {
                Log.d("CONTROL_MEDIA", "re play playlist: $timeEnd")
                startFirstTimeFrame()
            }
        }
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = 0) {
        try {
            currentPlayingMedia = mediaItem
            val b = mediaItem.body ?: ""
            val isVideo = b.contains(".mp4", ignoreCase = true) || b.contains(".m3u8", ignoreCase = true)
            val isImageMedia = !isVideo && (mediaItem.contentType?.id == 2 ||
                    b.contains(".jpg", ignoreCase = true) ||
                    b.contains(".png", ignoreCase = true) ||
                    b.contains(".jpeg", ignoreCase = true) ||
                    b.contains(".webp", ignoreCase = true))

            imagePlaybackStartMs = if (isImageMedia) {
                System.currentTimeMillis() - (timeStamp * 1000L)
            } else {
                0L
            }

            when (mediaItem.getMediaType()) {
                MediaType.FILE -> {
                    if (mediaItem.body!!.contains("m3u8")) {
                        playLiveStream(mediaItem)
                    } else {
                        exoPlayer.playMedia(mediaItem, timeStamp)
                    }
                }

                MediaType.LIVE -> {
                    if (mediaItem.contentType?.id == 2 || isImageMedia) {
                        exoPlayer.playMedia(mediaItem, timeStamp)
                    } else {
                        playLiveStream(mediaItem)
                    }
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
        exoPlayer.playMedia(mediaItem, 0)
    }

    fun stopAllPlayer() {
        mainHandler.removeCallbacksAndMessages(null)
        imagePlaybackStartMs = 0L
        currentPlayingMedia = null
        exoPlayer.stop()
    }

    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerManagerListener?.onTimeOver()
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
                val mediaDuration = getDurationInSecond(media.duration ?: "00:00:15")

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
            Log.e(TAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    fun stopTimeFrame() {
        playerManagerListener?.onTimeOver()
    }

    fun stopForceMedia() {
        if (playMode != PLAY_MODE_FORCE) return
        stopAllPlayer()
        playerManagerListener?.onEndForceMedia()
        playerManagerListener?.onTimeOver()
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != playMode) return
        playMode = ""
        stopAllPlayer()
        isTimeOver = true
        checkTimeOver()
    }

    fun onPlayForceCommand(
        context: Context,
        responseHub: ResponseHub,
        mediaScheduler: MediaScheduler,
        isLayoutChange: Boolean? = false
    ) {
        try {
            val listMediaItem = responseHub.name?.let { jsonListMediaItemAdapter.fromJson(it) }
            val listForceMedia = mutableListOf<MediaItem>()
            if (playMode == PLAY_MODE_FORCE) {
                if (ForceMedia.hasValidForceMedia(context)) {
                    val currentListForceMedia = ForceMedia.getLocalForceMedia(context)
                    if (currentListForceMedia != null && currentListForceMedia.first().level < responseHub.level!!) return
                }
            }
            val currentTime = Calendar.getInstance()

            listMediaItem?.forEach {
                val forceMedia = ForceMedia(
                    duration = it.duration ?: "00:00:15",
                    type = it.type ?: "",
                    level = it.level,
                    mediaItem = it
                ).apply {
                    val listDur = duration!!.split(":")
                    val h = listDur.getOrNull(0)?.toIntOrNull() ?: 0
                    val m = listDur.getOrNull(1)?.toIntOrNull() ?: 0
                    val s = listDur.getOrNull(2)?.toIntOrNull() ?: 0
                    val mediaEndTime = currentTime.apply {
                        add(Calendar.HOUR, h)
                        add(Calendar.MINUTE, m)
                        add(Calendar.SECOND, s)
                    }
                    endTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(mediaEndTime.time)
                }.toMediaItem()
                listForceMedia.add(forceMedia)
            }
            saveListForceToPref(context, jsonListMediaItemAdapter.toJson(listForceMedia))
            if (isLayoutChange == false) {
                playForceMedia(listForceMedia, mediaScheduler = mediaScheduler)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playForceMedia(
        listForceMedia: List<MediaItem>, mediaScheduler: MediaScheduler
    ) {
        try {
            mediaScheduler.cancelAllScheduledListAlarm()
            playMode = PLAY_MODE_FORCE

            currentTextPlaylist =
                listForceMedia.filter { it.broadcastRegion?.value == LEFT_AREA.toInt() }

            playerManagerListener?.onLeftMediaPlaying(currentTextPlaylist)

            currentPlaylist =
                listForceMedia.filter { it.broadcastRegion?.value == RIGHT_AREA.toInt() || it.broadcastRegion?.value == FULL_SCREEN }
            playlistIndex = 0

            playMediaByIndex(playlistIndex, 0)

            listForceMedia.lastOrNull()?.let {
                mediaScheduler.scheduleForceMediaAlarm(it)
            }

        } catch (e: JsonDataException) {
            Log.d(TAG, "playForceMedia error: ${e.message} ")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAndEndTimeFrame() {
        mainHandler.removeCallbacks(scheduleRecoveryRunnable)
        itemFailCountMap.clear()
        skippedItemKeys.clear()
        imagePlaybackStartMs = 0L
        stopAllPlayer()
        currentPlaylist = emptyList()
        playerManagerListener?.onTimeOver()
    }

    fun onActivityDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        exoPlayer.release()
    }

    fun resetPlayMode() {
        mainHandler.removeCallbacks(scheduleRecoveryRunnable)
        itemFailCountMap.clear()
        skippedItemKeys.clear()
        imagePlaybackStartMs = 0L
        playMode = PLAY_MODE_INIT
    }

    companion object {
        const val PLAY_MODE_FORCE = "-2"
        const val PLAY_MODE_INIT = "0"
        const val PLAY_MODE_IDLE = "0"
    }
}
