package app.tek4tv.ipradio.core.stream

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Job
import androidx.core.net.toUri
import app.tek4tv.ipradio.ui.CustomPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import kotlin.coroutines.cancellation.CancellationException

class StreamBackManagerVlc(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "StreamBackManager"
        private const val CMD_START13 = 0
        private const val CMD_START24 = 1
        private const val CMD_STOP = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private var streamJob: Job? = null
    private var stopTimeoutJob: Job? = null

    // ✅ FIX 1: Thêm currentMediaUrl để lưu URL đang phát thực tế
    private var currentMediaUrl: String? = null

    private var mLibVLC: LibVLC
    private var vlcPlayer: MediaPlayer

    init {
        val args = arrayListOf(
            "-vvv",
            "--sout-keep"
        )
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = MediaPlayer(mLibVLC)
        vlcPlayer.setEventListener { event ->
            Log.d(TAG, "VLC event=${event.type}")
        }
    }

    /**
     * ✅ FIX 3: Gọi hàm này mỗi khi CustomPlayer bắt đầu phát một media mới.
     * Ví dụ: gọi từ nơi bạn gọi vlcPlayer.play(mediaUri)
     */
    fun updateCurrentMediaUrl(url: String) {
        currentMediaUrl = url
        Log.d(TAG, "currentMediaUrl updated: $url")
    }

    fun handleStreamBack(icecastUrl: String, cmd: Int?, timeout: Int?) {
        when (cmd) {
            CMD_STOP -> scope.launch { stopStreamBack() }
            CMD_START13, CMD_START24 -> scope.launch { startStreamBack(icecastUrl, timeout) }
            else -> Log.w(TAG, "Unknown cmd=$cmd, ignore")
        }
    }

    private suspend fun startStreamBack(icecastUrl: String, timeout: Int?) {
        mutex.withLock {
            val config = parseIcecastUrl(icecastUrl)
            if (config == null) {
                Log.e(TAG, "URL Icecast không hợp lệ: $icecastUrl")
                return
            }

            // ✅ FIX 4: Lấy URL media đang phát thực tế
            val mediaUrl = currentMediaUrl
            if (mediaUrl.isNullOrBlank()) {
                Log.e(TAG, "Không có media nào đang phát để stream lại")
                return
            }

            stopStreamBackLocked()

            try {
                Log.d(
                    TAG,
                    "Bắt đầu stream '$mediaUrl' lên Icecast: ${config.host}:${config.port}${config.mount}"
                )

                // ✅ FIX 5: Chuỗi sout đúng
                // - transcode: đảm bảo audio được encode sang mp3 trước khi đẩy lên Icecast
                // - duplicate dst=display: phát ra loa local
                // - duplicate dst=std: đẩy lên Icecast
                // - mux=mp3 + access=shout: giao thức Icecast với stream MP3
                val icecastDst =
                    "//${config.username}:${config.password}@${config.host}:${config.port}${config.mount}"

                val sout =
                    ":sout=#transcode{acodec=mp3,ab=128,channels=2,samplerate=44100}:std{access=shout,mux=mp3,dst=$icecastDst}"

                val media = Media(mLibVLC!!, Uri.parse(mediaUrl)).apply {
                    addOption(sout)
                    addOption(":sout-keep")
                    addOption(":no-sout-all")
                    addOption(":no-video")
                    if (mediaUrl.startsWith("http")) {
                        addOption(":network-caching=1500")
                        addOption(":http-reconnect=true")
                    }
                }

                vlcPlayer.media = media
                media.release()
                vlcPlayer.play()

            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi khởi tạo VLC Stream: ${e.message}", e)
            }

            // Timeout tự động ngắt
            val safeTimeout = timeout ?: 0
            if (safeTimeout > 0) {
                stopTimeoutJob = scope.launch {
                    delay(safeTimeout * 1000L)
                    Log.d(TAG, "Timeout, dừng stream...")
                    mutex.withLock { stopStreamBackLocked() }
                }
            }
        }
    }

    private fun stopStreamBackLocked() {
        stopTimeoutJob?.cancel()
        stopTimeoutJob = null
        streamJob?.cancel()
        streamJob = null
        if (vlcPlayer.isPlaying) {
            vlcPlayer.stop()
        }
        Log.d(TAG, "Đã dừng phát và stream.")
    }

    suspend fun stopStreamBack() {
        mutex.withLock { stopStreamBackLocked() }
    }


    private fun parseIcecastUrl(rawUrl: String): IcecastConfig? {
        return try {
            val uri = rawUrl.toUri()
            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: return null
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "icecasts" || scheme == "https" -> 443
                else -> 80
            }
            val userInfo = uri.encodedUserInfo ?: ""
            val parts = userInfo.split(":", limit = 2)
            val username = parts.getOrNull(0)?.let(Uri::decode).orEmpty()
            val password = parts.getOrNull(1)?.let(Uri::decode).orEmpty()
            val mount = uri.encodedPath
                ?.let(Uri::decode)
                ?.takeIf { it.isNotBlank() }
                ?: "/"
            val tls = scheme == "icecasts" || scheme == "https"
            if (username.isBlank() || password.isBlank()) return null
            IcecastConfig(
                host = host,
                port = port,
                mount = mount,
                username = username,
                password = password,
                tls = tls,
                contentType = "audio/mpeg"  // ✅ FIX 8: mp3 → audio/mpeg
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseIcecastUrl fail: ${e.message}", e)
            null
        }
    }
}