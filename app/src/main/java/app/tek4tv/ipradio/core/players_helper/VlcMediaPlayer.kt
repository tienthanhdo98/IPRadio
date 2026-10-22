package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.LOGTAG
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.handlerLivePlayer
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.network.CaCertHelper
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.util.Calendar

class VlcMediaPlayer(
    private val appContext: Context,
    private val playerEventListener: IPlayerEventListener
) : IMediaPlayer {
    private var mLibVLC: LibVLC
    private var vlcPlayer: CustomPlayer
    private var currentMedia: MediaItem? = null
    private var isCurrentMediaForce = false

    private var isPlaying = false
    private var lastError = -1

    var countReplayLive = 0
    var countReplayFile = 0

    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    override fun isPlaying(): Boolean = isPlaying

    private val vlcEventListener = { event: Int, _: Long, _: Long, _: Float, _: String? ->
        when (event) {
            MediaPlayer.Event.Playing -> {
                if (!isPlaying) {
                    Log.i(TAG, "Playing: ${currentMedia?.title}")
                    isPlaying = true
                    // Reset biến đếm retry khi phát thành công
                    countReplayLive = 0
                    countReplayFile = 0
                    playerEventListener.onPlayMediaSuccess(currentMedia)
                }
            }

            MediaPlayer.Event.EndReached -> {
                val media = currentMedia
                if (media != null) {
                    val isFile = media.getMediaType() == MediaType.FILE && !isCurrentMediaForce

                    if (isFile) {
                        val isStreamEarlyEnd = checkTimeFrameStream(media)
                        if (!isStreamEarlyEnd) {
                            Log.i(TAG, "EndReached file hoàn tất: ${media.title}")
                            isPlaying = false
                            playerEventListener.onMediaEndReached(media)
                        } else {
                            Log.w(TAG, "EndReached file sớm (ngắt kết nối stream): ${media.title}")
                            checkStreamError()
                        }
                    } else {
                        // Luồng Live hoặc Force Media
                        val isStillInTime = checkLiveStream(media)
                        if (isStillInTime) {
                            Log.w(
                                TAG,
                                "EndReached Live sớm nhưng vẫn còn trong giờ phát -> Reconnecting: ${media.title}"
                            )
                            onLiveErrorVlc()
                        } else {
                            Log.i(TAG, "EndReached Live hết giờ phát bình thường: ${media.title}")
                            isPlaying = false
                            playerEventListener.onMediaForceEndReached(media)
                        }
                    }
                } else {
                    isPlaying = false
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError: ${currentMedia?.title}")

                // Tự động kiểm tra và tải SSL cert của domain nếu gặp lỗi HTTPS
                CaCertHelper.extractHttpsUrl(currentMedia)?.let { httpsUrl ->
                    Log.i(
                        TAG,
                        "Phát hiện lỗi phát HTTPS: $httpsUrl -> Đang tự động lấy SSL cert..."
                    )
                    CaCertHelper.fetchAndSaveCertFromUrlAsync(appContext, httpsUrl, force = true)
                }

                val media = currentMedia
                if (media != null) {
                    val isFile = media.getMediaType() == MediaType.FILE && !isCurrentMediaForce
                    if (isFile) {
                        Log.e(TAG, "EncounteredError File: ${media.title}")
                        checkStreamError()
                    } else {
                        Log.e(TAG, "EncounteredError Live: ${media.title}")
                        onLiveErrorVlc()
                    }
                }
                isPlaying = false
                lastError = event
            }

            else -> {
            }
        }
    }

    init {
        val caDir = CaCertHelper.setupCaDir(appContext)

        val args = ArrayList<String>()
        // Bật log vlc
        args.add("-vvv")
        args.add("--network-caching=1500")
        args.add("--gnutls-dir-trust=${caDir.absolutePath}")
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener
    }

    override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
        if (isPlaying) stop()

        // Hủy bất kỳ retry cũ nào đang chờ
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null

        // Reset số lần retry nếu đổi sang media mới
        if (currentMedia?.id != mediaItem.id) {
            countReplayLive = 0
            countReplayFile = 0
        }

        currentMedia = mediaItem
        isCurrentMediaForce = isForce

        // Kiểm tra và nạp trước SSL certificate nếu là luồng HTTPS
        CaCertHelper.extractHttpsUrl(mediaItem)?.let { httpsUrl ->
            CaCertHelper.ensureCertForUrlAsync(appContext, httpsUrl)
        }

        if (isForce) {
            playLiveStream(mediaItem, timeStamp)
        } else {
            playFile(mediaItem, timeStamp)
        }
    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        Log.i(TAG, "vlc play file: time stamp $timeStamp, title: ${mediaItem.title}")
        val media = mediaItem.getVlcMedia(mLibVLC)
        if (timeStamp > 0) {
            media.addOption(":start-time=$timeStamp")
        }
        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem, timeStamp: Int) {
        Log.i(
            TAG,
            "vlc play live: time stamp $timeStamp, duration: ${mediaItem.duration}, title: ${mediaItem.title}"
        )
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp

        val totalDurationSec = getMediaDurationSeconds(mediaItem)
        if (totalDurationSec <= 0) {
            Log.e(TAG, "Luồng Live không có duration hợp lệ (${mediaItem.duration}) -> Dừng phát")
            stop()
            if (currentMedia?.getMediaType() == MediaType.FILE && !isCurrentMediaForce) {
                playerEventListener.onMediaEndReached(mediaItem)
            } else {
                playerEventListener.onMediaForceEndReached(mediaItem)
            }
            return
        }

        val remainingSec = totalDurationSec - currentTimeStamp
        if (remainingSec <= 0) {
            Log.w(
                TAG,
                "Luồng Live đã hết thời lượng phát (${currentTimeStamp}s >= ${totalDurationSec}s) -> Kết thúc ngay"
            )
            stop()
            if (currentMedia?.getMediaType() == MediaType.FILE && !isCurrentMediaForce) {
                playerEventListener.onMediaEndReached(mediaItem)
            } else {
                playerEventListener.onMediaForceEndReached(mediaItem)
            }
            return
        }

        val stream = mediaItem.getVlcMedia(mLibVLC)
        vlcPlayer.play(stream)
        Log.i(LOGTAG, "Player vlc live start")

        handlerLivePlayer?.removeCallbacksAndMessages(null)
        handlerLivePlayer = Handler(Looper.getMainLooper())

        val delayMillis = remainingSec * 1000L
        handlerLivePlayer?.postDelayed(
            {
                Log.i(TAG, "Hết thời lượng Live (${totalDurationSec}s) -> Dừng phát")
                vlcPlayer.stop()
                isPlaying = false
                if (mediaItem.getMediaType() == MediaType.FILE && !isCurrentMediaForce) {
                    playerEventListener.onMediaEndReached(mediaItem)
                } else {
                    playerEventListener.onMediaForceEndReached(mediaItem)
                }
            },
            delayMillis
        )
    }

    // Check xem có đang trong giờ phát luồng live hay không
    private fun checkLiveStream(mediaItem: MediaItem): Boolean =
        checkLiveStream(mediaItem)

    // Check xem file VOD có bị ngắt giữa chừng hay không
    private fun checkTimeFrameStream(mediaItem: MediaItem): Boolean =
        checkTimeFrameStream(mediaItem, currentMedia?.body)

    private fun checkStreamError() {
        val media = currentMedia ?: return

        try {
            val isStillInTimeFrame = checkTimeFrameStream(media)

            if (isStillInTimeFrame && countReplayFile < 10) {
                isPlaying = false
                playerEventListener.onPlayerError(
                    VlcException("Stream file failed!"),
                    true,
                    media
                )
                val timeStamp2 = calculateResumePoint(media)
                val wasForce = isCurrentMediaForce

                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = Runnable {
                    val current = currentMedia
                    if (current != null && current.id == media.id) {
                        countReplayFile++
                        Log.i(
                            TAG,
                            "checkStreamError retry (${countReplayFile}/10): ${current.title}, timeStamp: $timeStamp2"
                        )
                        val body = current.body ?: ""
                        val pathBackup = current.pathBackup
                        if (body.contains("rtsp://") || body.contains("rtmp://")) {
                            playMedia(current, true, timeStamp2)
                        } else if (pathBackup.contains("http://") || pathBackup.contains("https://") || body.contains(
                                "http://"
                            ) || body.contains("https://")
                        ) {
                            playMedia(current, wasForce, timeStamp2)
                        } else {
                            playMedia(current, wasForce, timeStamp2)
                        }
                    }
                }
                retryHandler.postDelayed(retryRunnable!!, 5000)

            } else {
                Log.i(
                    TAG,
                    "checkStreamError dừng retry: hết khung giờ hoặc quá 10 lần -> chuyển bài tiếp"
                )
                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = null
                isPlaying = false
                playerEventListener.onMediaEndReached(media)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi checkStreamError: ${e.message}", e)
        }
    }

    private fun onLiveErrorVlc() {
        val media = currentMedia ?: return

        try {
            val isStillInTime = checkLiveStream(media)

            if (isStillInTime && countReplayLive < 5) {
                isPlaying = false
                playerEventListener.onPlayerError(
                    VlcException("Stream live failed!"),
                    true,
                    media
                )
                val resumeTime = calculateResumePoint(media)

                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = Runnable {
                    val current = currentMedia
                    if (current != null && current.id == media.id) {
                        countReplayLive++
                        Log.i(
                            TAG,
                            "onLiveErrorVlc retry (${countReplayLive}/5): ${current.title}, resumeTime: $resumeTime"
                        )
                        playMedia(current, true, resumeTime)
                    }
                }
                retryHandler.postDelayed(retryRunnable!!, 8000)

            } else {
                Log.w(
                    TAG,
                    "onLiveErrorVlc dừng retry: hết giờ hoặc quá 5 lần (${countReplayLive}/5)"
                )
                retryRunnable?.let { retryHandler.removeCallbacks(it) }
                retryRunnable = null
                isPlaying = false
                stop()
                playerEventListener.onMediaForceEndReached(media)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onLiveErrorVlc: ${e.message}", e)
        }
    }

    override fun stop() {
        try {
            retryRunnable?.let { retryHandler.removeCallbacks(it) }
            retryRunnable = null
            handlerLivePlayer?.removeCallbacksAndMessages(null)

            vlcPlayer.stop()
            isPlaying = false
            currentMedia = null
            isCurrentMediaForce = false
        } catch (e: Exception) {
            Log.e(TAG, "stop error: $e")
            isPlaying = false
            currentMedia = null
            isCurrentMediaForce = false
        }
    }

    override fun release() {
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null
        handlerLivePlayer?.removeCallbacksAndMessages(null)
        vlcPlayer.release()
        mLibVLC.release()
    }

    override fun playTest() {
//        val media = Media(
//            mLibVLC,
//            "".toUri()
//        )
//        vlcPlayer.play(media)
    }

    companion object {
        const val TAG = "VlcMediaPlayer"

        fun getMediaStartTime(mediaItem: MediaItem): Calendar {
            val timeStr = mediaItem.start ?: mediaItem.startTime ?: mediaItem.fixTime ?: "00:00:00"
            return toCalendar(timeStr)
        }

        fun getMediaDurationSeconds(mediaItem: MediaItem): Int {
            val dur = mediaItem.duration ?: return 0
            return getDurationInSecond(dur)
        }

        fun calculateResumePoint(
            mediaItem: MediaItem,
            now: Calendar = Calendar.getInstance()
        ): Int {
            val startTime = getMediaStartTime(mediaItem)
            val elapsed = ((now.timeInMillis - startTime.timeInMillis) / 1000).toInt()
            return if (elapsed > 0) elapsed else 0
        }

        // Check xem có đang trong giờ phát luồng live hay không
        fun checkLiveStream(mediaItem: MediaItem, now: Calendar = Calendar.getInstance()): Boolean {
            val startTime = getMediaStartTime(mediaItem)
            val durationSec = getMediaDurationSeconds(mediaItem)
            if (durationSec <= 0) {
                Log.w(
                    TAG,
                    "checkLiveStream: duration không hợp lệ (${mediaItem.duration}) -> Hết giờ phát"
                )
                return false
            }

            val liveEndTime = (startTime.clone() as Calendar).apply {
                add(Calendar.SECOND, durationSec)
            }
            val isStillInTime = (now <= liveEndTime)
            Log.d(
                TAG,
                "checkLiveStream: isStillInTime=$isStillInTime (now: ${now.time}, endTime: ${liveEndTime.time})"
            )
            return isStillInTime
        }

        // Check xem file VOD có bị ngắt giữa chừng hay không
        fun checkTimeFrameStream(
            mediaItem: MediaItem,
            currentMediaBody: String? = null,
            now: Calendar = Calendar.getInstance()
        ): Boolean {
            if (currentMediaBody != null && currentMediaBody != mediaItem.body) return false

            // File cục bộ đã tải về máy: khi nhận EndReached từ VLC nghĩa là đã đọc tới EOF (hoàn tất 100%).
            val currentBody = mediaItem.body
            if (!currentBody.isNullOrEmpty() && File(currentBody).exists()) {
                return false
            }

            val startTime = getMediaStartTime(mediaItem)
            val durationSec = getMediaDurationSeconds(mediaItem)
            if (durationSec <= 0) return false

            val liveEndTime = (startTime.clone() as Calendar).apply {
                add(Calendar.SECOND, durationSec - 2)
            }
            val isStillInTime = (now <= liveEndTime)
            Log.d(
                TAG,
                "checkTimeFrameStream: isStillInTime=$isStillInTime (now: ${now.time}, endTime: ${liveEndTime.time})"
            )
            return isStillInTime
        }
    }
}

class VlcException(mess: String? = null) : Exception(mess ?: "")