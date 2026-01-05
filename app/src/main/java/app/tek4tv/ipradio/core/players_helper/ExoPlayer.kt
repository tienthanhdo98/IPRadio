package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.URLUtil
import androidx.annotation.RequiresApi
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
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.strFormatToCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@androidx.media3.common.util.UnstableApi
class ExoMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    private val exoVideoLayout: PlayerView
) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private var exoPlayer: ExoPlayer

    private var currentMedia: MediaItem? = null

    var handler: Handler? = null


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastJob: Job? = null

    private val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(/* minBufferMs = */
        10_000,/* maxBufferMs = */
        50_000,/* bufferForPlaybackMs = */
        1_000,/* bufferForPlaybackAfterRebufferMs = */
        2_000
    ).setPrioritizeTimeOverSizeThresholds(true)
        // để Exo tự ước lượng theo track
        .setTargetBufferBytes(C.LENGTH_UNSET).build()


    private fun handleEndMediaItem() {
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
                handleEndMediaItem()
            }


            if (state == Player.STATE_READY) {
                Log.i(TAG, "STATE_READY")
                playerEventListener.onPlayMediaSuccess(currentMedia)

            }
        }


        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "onPlayerError: ${error.message ?: ""}")
            try {
                val prev = lastJob
                lastJob = scope.launch {
                    try {
                        // Đợi job trước (nếu có) xong đã, nhưng caller không phải suspend
                        prev?.cancelAndJoin()
                        delay(8_000)
                        currentMedia?.let { media ->
                            playerEventListener.onPlayerError(error = error, mediaItem = media)
                            if (checkMediaIsInTimeFrame(media)) {
                                playMedia(media)
                            } else {
                                handleEndMediaItem()
                            }
                        } ?: stop()

                    } catch (e: CancellationException) {
                        e.printStackTrace()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    init {
        exoPlayer = ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)  // ban đầu tối ưu cho file local
            .setUseLazyPreparation(true)  // chuẩn bị lười để giảm spike đầu
            .build()
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus= */ true
        )
        exoPlayer.addListener(exoEventListener)
        exoVideoLayout.player = exoPlayer
        exoVideoLayout.setShutterBackgroundColor(Color.TRANSPARENT)
        exoVideoLayout.setKeepContentOnPlayerReset(true)
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        when (mediaItem.getMediaType()) {
            MediaType.FILE -> {
                playFile(mediaItem, timeStamp)
            }

            MediaType.LIVE -> {
                playLiveStream(mediaItem, timeStamp)
            }

            else -> {
                playFile(mediaItem, timeStamp)
            }
        }
        currentMedia = mediaItem
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        val fileName = URLUtil.guessFileName(mediaItem.body, null, null)
        val localPath = File(MyApp.instance!!.applicationContext.filesDir.path, fileName).path
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
//                        playerEventListener.onMediaForceEndReached(currentMedia)
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
            handler?.removeCallbacksAndMessages(null)
            exoPlayer.stop()
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