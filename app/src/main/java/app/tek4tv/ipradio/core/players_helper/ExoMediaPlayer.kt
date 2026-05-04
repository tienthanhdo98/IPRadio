package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.utils.network.ignoreAllSSLErrors
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import okhttp3.OkHttpClient
import java.io.File

class ExoMediaPlayer(
    private val appContext: Context,
    private val eventListener: IPlayerEventListener
) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private val exoPlayer: ExoPlayer
    private var currentMedia: MediaFile? = null

    // --- Trạng thái giống VLC ---
    private var hasStartedPlaying = false
    private var isBufferingTimeoutScheduled = false
    private var bufferingHandler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------------------
    // Timeout 20s – giống VlcMediaPlayer
    // -------------------------------------------------------------------
    private val bufferingTimeoutRunnable = Runnable {
        if (!hasStartedPlaying && currentMedia != null) {
            Log.d(TAG, "ExoPlayer buffering timeout")
            eventListener.onPlayerError("Timeout khi tải stream", currentMedia)
        }
    }

    // -------------------------------------------------------------------
    // Listener – map 1-1 với vlcEventListener
    // -------------------------------------------------------------------
    private val exoListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {

                // Tương đương Vlc Opening (reset state)
                Player.STATE_IDLE -> {
                    Log.d(TAG, "ExoPlayer Idle/Stopped")
                    cancelBufferingTimeout()
                }

                // Tương đương Vlc Buffering
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "ExoPlayer Buffering")
                    if (!hasStartedPlaying && currentMedia != null && !isBufferingTimeoutScheduled) {
                        isBufferingTimeoutScheduled = true
                        bufferingHandler.postDelayed(bufferingTimeoutRunnable, 20_000L)
                    }
                }

                // Tương đương Vlc EndReached
                Player.STATE_ENDED -> {
                    Log.d(TAG, "ExoPlayer EndReached")
                    cancelBufferingTimeout()
                    eventListener.onMediaEndReached(currentMedia)
                }

                else -> Unit
            }
        }

        // Tương đương Vlc Playing
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && !hasStartedPlaying) {
                Log.d(TAG, "ExoPlayer Playing")
                hasStartedPlaying = true
                cancelBufferingTimeout()
                eventListener.onPlayMediaSuccess(currentMedia)
            }
        }

        // Tương đương Vlc EncounteredError
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer Error: ${error.message}")
            cancelBufferingTimeout()
            eventListener.onPlayerError("Lỗi: ${error.message}", currentMedia)
        }
    }

    // -------------------------------------------------------------------
    // Init – giữ nguyên OkHttp + SSL ignore của file gốc
    // -------------------------------------------------------------------
    init {
        val okHttpClient = OkHttpClient.Builder().apply {
            ignoreAllSSLErrors()
        }.build()
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSourceFactory(appContext, null, okHttpFactory)
//        val loadControl = DefaultLoadControl.Builder()
//            .setBufferDurationsMs(
//                /* minBufferMs  */ 1_500,   // default 50_000 – giữ tối thiểu 1.5s trong buffer
//                /* maxBufferMs  */ 3_000,   // default 50_000 – không cache quá 3s
//                /* bufferForPlaybackMs             */ 800,  // default 2_500 – cần 0.8s là phát
//                /* bufferForPlaybackAfterRebufferMs*/ 1_500 // default 5_000 – sau rebuffer cần 1.5s
//            )
//            .build()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,   // minBufferMs
                1500,  // maxBufferMs
                250,   // bufferForPlaybackMs
                500    // bufferForPlaybackAfterRebufferMs
            )
            .build()
        exoPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .also { it.addListener(exoListener) }
    }

    // -------------------------------------------------------------------
    // IMediaPlayer impl
    // -------------------------------------------------------------------
    override fun playMedia(mediaItem: MediaFile, isForce: Boolean, timeStamp: Int) {
        currentMedia = mediaItem
        if (isForce) {
            playLiveStream(mediaItem)
        } else {
            playFile(mediaItem, timeStamp)
        }
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun resume() {
        exoPlayer.play()
    }

    override fun stop() {
        try {
            cancelBufferingTimeout()
            exoPlayer.stop()
            currentMedia = null
        } catch (e: Exception) {
            Log.d(TAG, "stop error: $e")
            currentMedia = null
        }
    }

    override fun release() {
        cancelBufferingTimeout()
        exoPlayer.removeListener(exoListener)
        exoPlayer.release()
    }

    override fun getCurrentPositionMs(): Long = try { exoPlayer.currentPosition } catch (_: Exception) { 0L }

    override fun getCurrentDurationMs(): Long = try {
        val d = exoPlayer.duration
        if (d == com.google.android.exoplayer2.C.TIME_UNSET || d <= 0) 0L else d
    } catch (_: Exception) { 0L }

    // -------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------

    /**
     * Tương đương VlcMediaPlayer.playFile()
     * Ưu tiên file local nếu tồn tại, ngược lại dùng URL gốc.
     * timeStamp > 0 → seekTo vị trí đó (chỉ khi file local tồn tại).
     */
    private fun playFile(mediaItem: MediaFile, timeStamp: Int) {
        try {
            Log.d(TAG, "playFile timeStamp=$timeStamp url=${mediaItem.url}")

            val url = mediaItem.url ?: return
            val fileName = getFileNameFromUrl(url)
            val localFile = File(appContext.filesDir.path, fileName)

            val uri = if (localFile.exists() && localFile.isFile) {
                Uri.fromFile(localFile)
            } else {
                val backupUrl = mediaItem.pathBackup
                if (backupUrl.isNotBlank()) {
                    backupUrl.toUri()
                } else {
                    url.toUri()
                }
            }

            resetPlayState()
            exoPlayer.setMediaItem(com.google.android.exoplayer2.MediaItem.fromUri(uri))
            exoPlayer.prepare()

            if (timeStamp > 0 && localFile.exists() && localFile.isFile) {
                exoPlayer.seekTo(timeStamp * 1000L)
                Log.d(TAG, "playFile seekTo ${timeStamp}s")
            }

            exoPlayer.playWhenReady = true

        } catch (e: Exception) {
            Log.e(TAG, "playFile error: ${e.message}")
        }
    }

    /**
     * Tương đương VlcMediaPlayer.playLiveStream()
     * Dùng pathBackup như file gốc, schedule stop theo duration - timeStamp.
     */
    private fun playLiveStream(mediaItem: MediaFile) {
        Log.d(TAG, "playLiveStream duration=${mediaItem.duration}")


        resetPlayState()
        exoPlayer.setMediaItem(
            com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.pathBackup)
        )
        exoPlayer.prepare()

        exoPlayer.playWhenReady = true
        val duration = mediaItem.duration ?: 0
        val durationMs = duration * 1000L
        if (durationMs <= 0) {
            Log.e(
                TAG,
                "playNormalMedia: live stream nhưng duration=0 → bỏ qua để tránh phát mãi"
            )
            eventListener.onPlayerError("Stream thiếu duration", mediaItem)
            return
        }
        bufferingHandler = Handler(Looper.getMainLooper())
        bufferingHandler.postDelayed(
            {
                Log.d("MediaInfo", "playLiveStream: schedule stop live handler")
                exoPlayer.stop()
                eventListener.onMediaEndReached(currentMedia)
            },
            durationMs
        )
        Log.d("MediaInfo", "playLiveStream: schedule stop after ${durationMs}ms")
    }

    private fun resetPlayState() {
        hasStartedPlaying = false
        cancelBufferingTimeout()
        exoPlayer.stop()
    }

    private fun cancelBufferingTimeout() {
        isBufferingTimeoutScheduled = false
        bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
    }
}