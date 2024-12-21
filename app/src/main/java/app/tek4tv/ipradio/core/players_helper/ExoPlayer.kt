package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.FrameLayout
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.network.ignoreAllSSLErrors
import app.tek4tv.ipradio.utils.toCalendar
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import okhttp3.OkHttpClient
import java.util.*


//Mainly for playing m3u8 stream
class ExoMediaPlayer(
    private val appContext: Context,
    private val eventListener: IPlayerEventListener,private val exoplayerView: PlayerView
) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private val exoPlayer: ExoPlayer

    private var currentPlayingMedia: MediaItemModel? = null

    private var isPlaying = false
    private var isForceValue = false

    override var disconnectedHub = false

    private var dataSourceFactory: DefaultDataSourceFactory
    override fun isPlaying(): Boolean = isPlaying

    var handler: Handler? = null

    var countReplayLive = 0
    private val exoEventListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            this@ExoMediaPlayer.isPlaying = isPlaying

            if (isPlaying) {
                eventListener.onPlayMediaSuccess(currentPlayingMedia)
                Log.i(TAG, "onPlaybackStateChanged: onIsPlayingChanged")
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            if(state == Player.STATE_ENDED)
            {
                Log.i(TAG, "onPlaybackStateChanged: end reach")
            }
            if (state == Player.STATE_BUFFERING) {
                Log.i(TAG, "onPlaybackStateChanged: Buff")
            }

            if(state == Player.STATE_READY)
            {
                Log.i(TAG, "onPlaybackStateChanged: play")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "exoerror: ${error.message ?: ""}")
            if (disconnectedHub) return
            isPlaying = false
            try {
                val isStreamInTimeFrame = checkHls(currentPlayingMedia!!)
                Log.d(TAG, "onLiveErrorVlc: Chay lai live $isForceValue")
                if (isForceValue) {
                    if (isStreamInTimeFrame) {
                        eventListener.onPlayerError(error, true, currentPlayingMedia!!)
                        Handler(Looper.getMainLooper()).postDelayed({
                            countReplayLive++;
                            Log.d(TAG, "onLiveErrorVlc: Chay lai live")
                            playMedia(currentPlayingMedia!!, isForceValue)
                        }, 10000)
                    } else {
                        Log.d(TAG, "Stopping from reconnect")
                        stop()
                    }
                }
                else{
                    if(isStreamInTimeFrame)
                    {
                        val resumeTime = calculateResumeLivePoint(currentPlayingMedia!!)
                        playMedia(currentPlayingMedia!!, isForceValue, resumeTime)
                    }

                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        //tắt xác thực ssl để phát luồng m3u8
        val okHttpClient = OkHttpClient.Builder().apply {
            //need to ignore all ssl error because the server certificate does not valid
            //on this type of device
            ignoreAllSSLErrors()
        }.build()
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
        dataSourceFactory = DefaultDataSourceFactory(appContext, null, okHttpFactory)
        exoPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exoPlayer.addListener(exoEventListener)
        exoplayerView.player = exoPlayer
    }

    override fun playMedia(mediaItem: MediaItemModel, isForce : Boolean,timeStamp: Int) {
        if(isPlaying) stop()

        try {
            currentPlayingMedia = mediaItem
            isForceValue = isForce
            Log.e(TAG, "Exoplayer is force $isForceValue")
            applyEndingTransformation(exoplayerView) {
                playHlsExo(mediaItem, timeStamp)
            }

        }
        catch (e :Exception)
        {
            Log.e(TAG, "Error playing media using Exo")
            e.printStackTrace()
        }
    }

    private fun playHlsExo(mediaItem: MediaItemModel, timeStamp: Int) {
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        val em = com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.pathBackup)

        exoPlayer.setMediaItem(em)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        Log.e(TAG, "Player exo start")
        if (!isForceValue) {
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper())
            handler?.postDelayed(
                {
                    exoPlayer.stop()
                    Log.d(TAG, "stop")
                    eventListener.onMediaEndReached(currentPlayingMedia)
                },
                (getDurationInSecond(mediaItem.duration!!) * 1000).toLong() - currentTimeStamp * 1000
            )
        }
    }
    private fun applyEndingTransformation(playerView: PlayerView, onComplete: () -> Unit) {
        val overlay = FrameLayout(appContext).apply {
            setBackgroundColor(0xAA000000.toInt()) // Semi-transparent overlay
            alpha = 0f
        }
        (playerView.parent as FrameLayout).addView(overlay)

        // Apply fade-out animation
        overlay.animate()
            .alpha(1f)
            .setDuration(1000) // 1-second fade-out
            .withEndAction {
                (playerView.parent as FrameLayout).removeView(overlay)
                onComplete() // Callback to play the next video
            }
            .start()
    }

    override fun reset() {

    }

    private fun calculateResumeLivePoint(mediaItem: MediaItemModel): Int {

        val startTimeOfMedia = toCalendar(mediaItem.start!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()

    }

    override fun stop() {
        try {
            handler?.removeCallbacksAndMessages(null)
            exoPlayer.stop()
            isPlaying = false
            currentPlayingMedia = null
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
    private fun checkHls(mediaItem: MediaItemModel): Boolean {
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }
        val check = (now <= liveEndTime)

        Log.d(TAG, "check hls: $check")

        return check
    }
}