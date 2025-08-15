package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class ExoMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    exoVideoLayout: PlayerView
) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private val exoPlayer: ExoPlayer

    private var currentMedia: MediaItem? = null

    var handler: Handler? = null

    var handlerError: Handler? = null


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastJob: Job? = null


    private val exoEventListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            if (isPlaying) {
                playerEventListener.onPlayMediaSuccess(currentMedia)
                Log.i(TAG, "onPlaybackStateChanged: onIsPlayingChanged")
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            if (state == Player.STATE_ENDED) {
                Log.i(TAG, "onPlaybackStateChanged: end reach")
                Log.e(TAG, "EndReached")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)
                } else {
                    //them dk check de khi phat live voi dang file se khong bi end luon du van dang trong khung
                    if (!checkMediaIsInTimeFrame(currentMedia!!)) {
                        Log.e(TAG, "EndReached Live van trong lich")
                    } else {
                        Log.e(TAG, "EndReached Live end")
                        playerEventListener.onMediaForceEndReached(currentMedia)
                    }
                }
            }
            if (state == Player.STATE_BUFFERING) {
                Log.i(TAG, "onPlaybackStateChanged: Buff")
            }

            if (state == Player.STATE_READY) {
                Log.i(TAG, "onPlaybackStateChanged: play")
            }

        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "exoerror: ${error.message ?: ""}")
            try {
//                handlerError?.removeCallbacksAndMessages(null)
//                handlerError = Handler(Looper.getMainLooper()).apply {
//                    postDelayed(
//                        {
//                            playerEventListener.onPlayerError(
//                                error = error,
//                                mediaItem = currentMedia!!
//                            )
//                            val isStreamInTimeFrame = checkMediaIsInTimeFrame(currentMedia!!)
//
//                            if (isStreamInTimeFrame) {
//                                playMedia(currentMedia!!)
//                            } else {
//                                stop()
//                            }
//                        }, 20000
//                    )
//                }

                val prev = lastJob
                lastJob = scope.launch {
                    // Đợi job trước (nếu có) xong đã, nhưng caller không phải suspend
                    prev?.join()

                    delay(20_000)
                    playerEventListener.onPlayerError(error = error, mediaItem = currentMedia!!)

                    if (checkMediaIsInTimeFrame(currentMedia!!)) {
                        playMedia(currentMedia!!)
                    } else {
                        stop()
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    init {


        //tắt xác thực ssl để phát luồng m3u8
//        val okHttpClient = OkHttpClient.Builder().apply {
//            //need to ignore all ssl error because the server certificate does not valid
//            //on this type of device
//            ignoreAllSSLErrors()
//        }.build()
//        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
//        dataSourceFactory = DefaultDataSourceFactory(appContext, null, okHttpFactory)

        exoPlayer = ExoPlayer.Builder(appContext)
//            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exoPlayer.addListener(exoEventListener)
        exoVideoLayout.player = exoPlayer

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
        val media = mediaItem.getExoMedia()
        exoPlayer.setMediaItem(media)
        Log.e(TAG, "Player local body ${mediaItem.body}")
        Log.e(TAG, "Player local contentType ${mediaItem.contentType?.id}")
        Log.e(TAG, "Player local duration ${mediaItem.duration}")
        //neu file chua tai xong ma cho option start-time se khong chay.
        if (timeStamp != -1 && mediaItem.contentType?.id != 2 && File(mediaItem.body!!).exists()) {
            exoPlayer.setMediaItem(media, timeStamp.toLong() * 1000)
        } else {
            exoPlayer.setMediaItem(media)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun playLiveStream(mediaItem: MediaItem, timeStamp: Int) {
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp

        val media = androidx.media3.common.MediaItem.fromUri(mediaItem.pathBackup)
        Log.e(TAG, "Player exo live body ${mediaItem.body}")
        Log.e(TAG, "Player exo live contentType ${mediaItem.contentType?.id}")
        Log.e(TAG, "Player exo live duration ${mediaItem.duration}")
        exoPlayer.setMediaItem(media)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        Log.e(TAG, "Player exo start")
        if (mediaItem.contentType?.id == 5) {
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper())
            handler?.postDelayed(
                {
                    exoPlayer.stop()
                    if (currentMedia?.getMediaType() == MediaType.FILE) {
                        val isStreamFailed = checkMediaIsInTimeFrame(currentMedia!!)
                        if (!isStreamFailed) {
                            Log.e(TAG, "EndReached ${currentMedia?.title}")
                            Log.d(TAG, "Player vlc live stop file")
                            playerEventListener.onMediaEndReached(currentMedia)
                        } else {
                            Log.e(TAG, "EncounteredError handler live ${currentMedia?.title}")
                            checkMediaIsInTimeFrame(currentMedia!!)
                        }
                    } else {
                        Log.d(TAG, "Player vlc live stop force")
                        playerEventListener.onMediaForceEndReached(currentMedia)
                    }
                },
                (getDurationInSecond(mediaItem.duration!!) * 1000).toLong() - currentTimeStamp * 1000
            )
        }


    }

    override fun reset() {

    }

    private fun calculateResumeLivePoint(mediaItem: MediaItem): Int {

        val startTimeOfMedia = toCalendar(mediaItem.start!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
    }

    override fun stop() {
        try {
            handler?.removeCallbacksAndMessages(null)
            exoPlayer.stop()
            currentMedia = null
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

        val liveEndTime = toCalendar(mediaItem.end!!)
        val check = (now <= liveEndTime)

        Log.d(TAG, "check hls: $check")

        return check
    }
}