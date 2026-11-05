package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.URLUtil
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.strFormatToCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@androidx.media3.common.util.UnstableApi
class ExoMediaPlayer(
    private val appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    private val exoVideoLayout: PlayerView
) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private var exoPlayer: ExoPlayer

    private var currentMedia: MediaItem? = null

    var handler: Handler? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bufferingTimeoutJob: Job? = null

    // Timeout đệm mặc định 25 giây (phù hợp với thiết bị chạy ngoài hiện trường mạng 4G yếu)
    private val DEFAULT_BUFFERING_TIMEOUT_MS = 25_000L

    /**
     * Tính timeout buffering linh hoạt theo thời lượng video (Adaptive Timeout):
     * - Tối thiểu 25s cho mạng yếu nạp đệm.
     * - Nếu video dài hơn 25s: cho phép chờ nạp tối đa bằng thời lượng clip (chặn trần 60s).
     */
    private fun getBufferingTimeoutMs(mediaItem: MediaItem?): Long {
        val durSec = getDurationInSecond(mediaItem?.duration ?: "")
        return if (durSec > 0) {
            (durSec * 1000L).coerceIn(DEFAULT_BUFFERING_TIMEOUT_MS, 60_000L)
        } else {
            DEFAULT_BUFFERING_TIMEOUT_MS
        }
    }

    /**
     * LoadControl cấu hình tối ưu cho thiết bị chạy mạng yếu:
     * - bufferForPlaybackMs = 1.500ms (1.5s): Hạ ngưỡng khởi phát để phát nhanh, không phải chờ lâu.
     * - bufferForPlaybackAfterRebufferMs = 2.500ms (2.5s): Nạp vừa đủ khi bị khựng mạng.
     * - minBufferMs = 15.000ms (15s): Gom nhiều buffer khi đường truyền thông thoáng để chạy ổn định.
     * - maxBufferMs = 50.000ms (50s): Đệm trước tối đa.
     */
    private val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(/* minBufferMs = */
            15_000,/* maxBufferMs = */
            50_000,/* bufferForPlaybackMs = */
            1_500,/* bufferForPlaybackAfterRebufferMs = */
            2_500
        ).setPrioritizeTimeOverSizeThresholds(true).setTargetBufferBytes(C.LENGTH_UNSET).build()


    private fun handleEndMediaItem() {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = null
        when (currentMedia?.getMediaType()) {
            MediaType.FILE -> {
                playerEventListener.onMediaEndReached(currentMedia)
            }

            MediaType.LIVE -> {
                playerEventListener.onMediaForceEndReached(currentMedia)
            }

            else -> {
                playerEventListener.onMediaEndReached(currentMedia)
            }
        }
    }

    private val exoEventListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            if (state == Player.STATE_ENDED) {
                Log.e(TAG, "STATE_ENDED")
                bufferingTimeoutJob?.cancel()
                bufferingTimeoutJob = null
                handleEndMediaItem()
            }

            if (state == Player.STATE_BUFFERING) {
                val timeoutMs = getBufferingTimeoutMs(currentMedia)
                Log.d(TAG, "STATE_BUFFERING: adaptive timeout set to ${timeoutMs}ms")
                bufferingTimeoutJob?.cancel()
                bufferingTimeoutJob = scope.launch {
                    try {
                        delay(timeoutMs)
                        Log.w(
                            TAG,
                            "STATE_BUFFERING timeout (${timeoutMs}ms) — skip media: ${currentMedia?.body}"
                        )
                        playerEventListener.onMediaEndReached(currentMedia)
                    } catch (_: CancellationException) {
                        // Bình thường khi chuyển sang STATE_READY hoặc stop()
                    }
                }
            }

            if (state == Player.STATE_READY) {
                Log.i(TAG, "STATE_READY")
                bufferingTimeoutJob?.cancel()
                bufferingTimeoutJob = null
                playerEventListener.onPlayMediaSuccess(currentMedia)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.e(TAG, "onPlayerError: ${error.message ?: ""}")
            bufferingTimeoutJob?.cancel()
            bufferingTimeoutJob = null

            val media = currentMedia
            if (media != null) {
                // Ủy quyền xử lý lỗi cho PlayersManager quản lý FailCount và Skip an toàn,
                // không tự động gọi đệ quy lặp vô tận trong player.
                playerEventListener.onPlayerError(error = error, mediaItem = media)
            } else {
                stop()
            }
        }

    }

    init {
        exoPlayer =
            ExoPlayer.Builder(appContext).setLoadControl(loadControl).setUseLazyPreparation(true)
                .build()
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),/* handleAudioFocus= */ true
        )
        exoPlayer.addListener(exoEventListener)
        exoVideoLayout.player = exoPlayer
        exoVideoLayout.setShutterBackgroundColor(Color.TRANSPARENT)
        exoVideoLayout.setKeepContentOnPlayerReset(true)
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = null

        val effectiveUrl = mediaItem.body?.takeIf { it.isNotBlank() } ?: mediaItem.pathBackup
        val fileName = URLUtil.guessFileName(effectiveUrl, null, null)
        val localFile = File(appContext.filesDir.path, fileName)
        val isImage = isImageMedia(mediaItem, localFile)

        when (mediaItem.getMediaType()) {
            MediaType.FILE -> {
                if (isImage) {
                    playImage(mediaItem, if (timeStamp > 0) timeStamp else 0)
                } else {
                    playFile(mediaItem, timeStamp)
                }
            }

            MediaType.LIVE -> {
                if (isImage) {
                    playImage(mediaItem, if (timeStamp > 0) timeStamp else 0)
                } else {
                    playLiveStream(mediaItem, timeStamp)
                }
            }

            else -> {
                if (isImage) {
                    playImage(mediaItem, if (timeStamp > 0) timeStamp else 0)
                } else {
                    playFile(mediaItem, timeStamp)
                }
            }
        }
        currentMedia = mediaItem
    }

    private fun isImageMedia(mediaItem: MediaItem, localFile: File?): Boolean {
        if (mediaItem.contentType?.id == 2) return true
        val b = mediaItem.body?.takeIf { it.isNotBlank() } ?: mediaItem.pathBackup
        if (b.contains(".jpg", ignoreCase = true) || b.contains(
                ".png",
                ignoreCase = true
            ) || b.contains(".jpeg", ignoreCase = true) || b.contains(".webp", ignoreCase = true)
        ) return true
        return localFile?.exists() == true && (localFile.name.endsWith(
            ".jpg",
            true
        ) || localFile.name.endsWith(".png", true) || localFile.name.endsWith(
            ".jpeg",
            true
        ) || localFile.name.endsWith(".webp", true))
    }

    private fun playImage(
        mediaItem: MediaItem, seekPositionSeconds: Int = 0, playWhenReady: Boolean = true
    ) {
        val effectiveUrl = mediaItem.body?.takeIf { it.isNotBlank() } ?: mediaItem.pathBackup
        val fileName = URLUtil.guessFileName(effectiveUrl, null, null)
        val localFile = File(appContext.filesDir.path, fileName)

        val fileToLoad = when {
            localFile.exists() -> localFile
            !mediaItem.body.isNullOrBlank() && File(mediaItem.body!!).exists() -> File(mediaItem.body!!)
            else -> null
        }

        // Kiểm tra file ảnh hỏng
        if (fileToLoad != null) {
            if (fileToLoad.length() == 0L) {
                Log.e(TAG, "playImage: file ảnh 0 bytes: ${fileToLoad.absolutePath}")
                playerEventListener.onPlayerError(
                    IllegalStateException("Image file size is 0 bytes: ${fileToLoad.absolutePath}"),
                    mediaItem
                )
                return
            }
            try {
                val bmp = BitmapFactory.decodeFile(fileToLoad.absolutePath)
                if (bmp == null && fileToLoad.length() > 0L) {
                    Log.e(
                        TAG,
                        "playImage: decodeFile trả về null (ảnh lỗi): ${fileToLoad.absolutePath}"
                    )
                    playerEventListener.onPlayerError(
                        IllegalStateException("Failed to decode image bitmap: ${fileToLoad.absolutePath}"),
                        mediaItem
                    )
                    return
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Lỗi bộ nhớ hoặc giải mã ảnh (OOM): ${t.message}")
                playerEventListener.onPlayerError(
                    IllegalStateException(
                        "Failed to decode image (possible OOM)", t
                    ), mediaItem
                )
                return
            }
        }

        val localPath = localFile.path
        val media = mediaItem.getExoMedia(localPath)
        val totalDurationSeconds = getDurationInSecond(mediaItem.duration ?: "00:00:15")
        val effectiveDurationSeconds = if (totalDurationSeconds > 0) totalDurationSeconds else 15

        val remainingSeconds = effectiveDurationSeconds - seekPositionSeconds.coerceAtLeast(0)
        if (remainingSeconds <= 0) {
            Log.d(
                TAG,
                "playImage: seekPosition ($seekPositionSeconds s) >= duration ($effectiveDurationSeconds s) -> kết thúc media"
            )
            handleEndMediaItem()
            return
        }

        val durationMs = remainingSeconds * 1000L
        Log.d(
            TAG,
            "Player play image ${mediaItem.body} - duration: ${durationMs}ms (seek: ${seekPositionSeconds}s)"
        )
        val finalMediaItem = media.buildUpon().setImageDurationMs(durationMs).build()

        exoPlayer.setMediaItem(finalMediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.play()
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        val fileName = URLUtil.guessFileName(mediaItem.body, null, null)
        val localPath = File(appContext.filesDir.path, fileName).path
        val media = mediaItem.getExoMedia(localPath)
        //neu file chua tai xong ma cho option start-time se khong chay.
        if (timeStamp != -1 && mediaItem.contentType?.id != 2 && File(localPath).exists()) {
            exoPlayer.setMediaItem(media, timeStamp.toLong() * 1000)
        } else {
            exoPlayer.setMediaItem(media)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    private fun playLiveStream(mediaItem: MediaItem, timeStamp: Int) {
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        //Hls Media
        if (mediaItem.contentType?.id == 5 || mediaItem.body?.contains("m3u8") == true) {
            val httpFactory = DefaultHttpDataSource.Factory()
            val hlsSource = HlsMediaSource.Factory(httpFactory)
                .createMediaSource(androidx.media3.common.MediaItem.fromUri(mediaItem.pathBackup)) // hoặc mediaItem có setMimeType
            exoPlayer.setMediaSource(hlsSource)
        } else {
            //Tạo ProgressiveMediaSource để tránh lỗi 416
            val httpFactory = DefaultHttpDataSource.Factory()
            val mediaSource = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(androidx.media3.common.MediaItem.fromUri(mediaItem.pathBackup))
            exoPlayer.setMediaSource(mediaSource)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        Log.e(TAG, "Player exo start")
        Log.e(TAG, "Player exo ${mediaItem.title}")
        Log.e(TAG, "Player exo ${timeStamp}")
        Log.e(TAG, "Player exo ${mediaItem.duration}")
        Log.e(TAG, "Player exo ${mediaItem.end}")
        if (mediaItem.contentType?.id == 5) {
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper())
            handler?.postDelayed(
                {
                    Log.e(TAG, "Player exo stop")
                    stop()
                    if (mediaItem.getMediaType() == MediaType.FILE) {
                        playerEventListener.onMediaEndReached(mediaItem)
                    } else {
                        val isStreamTimeFrame = checkMediaIsInTimeFrame(mediaItem)
                        if (!isStreamTimeFrame) {
                            playerEventListener.onMediaEndReached(mediaItem)
                        } else {
                            checkMediaIsInTimeFrame(mediaItem)
                        }
                    }
                },
                (getDurationInSecond(mediaItem.duration!!) * 1000).toLong() - currentTimeStamp * 1000
            )
        }
    }

    override fun reset() {

    }

    override fun stop() {
        try {
            bufferingTimeoutJob?.cancel()
            bufferingTimeoutJob = null
            handler?.removeCallbacksAndMessages(null)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            currentMedia = null
            Log.d(TAG, "stop")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun resume() {
        exoPlayer.play()
    }

    override fun release() {
        bufferingTimeoutJob?.cancel()
        bufferingTimeoutJob = null
        exoPlayer.release()
    }

    /**
     * Check if a stream is still in time to play
     * **/
    private fun checkMediaIsInTimeFrame(mediaItem: MediaItem): Boolean {
        val now = Calendar.getInstance()

        val check = mediaItem.end?.let { end ->
            now <= strFormatToCalendar(end)
        } ?: true
        Log.d(TAG, "checkMediaIsInTimeFrame: ${mediaItem.end}")
        Log.d(TAG, "checkMediaIsInTimeFrame: $check")
        return check
    }
}
