package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.network.ignoreAllSSLErrors
import app.tek4tv.ipradio.utils.toCalendar
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.*

//Mainly for playing m3u8 stream
class ExoMediaPlayer(
    appContext: Context,
    private val eventListener: IPlayerEventListener) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private val exoPlayer  : SimpleExoPlayer

    private var currentPlayingMedia : MediaItem? = null

    private var isPlaying = false
    private var isForceValue = false

    override var disconnectedHub = false

    override fun isPlaying(): Boolean = isPlaying

    private val exoEventListener = object : Player.EventListener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            this@ExoMediaPlayer.isPlaying = isPlaying

            if(isPlaying) {
                eventListener.onPlayMediaSuccess(currentPlayingMedia)
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

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "exoerror: ${error.message ?: ""}")

            //Live stream failed
            //if hub connection has been lost, exit then try to reconnect when hub connected again
            //else try to reconnect to livestream immediately

            eventListener.onPlayerError(error, true,currentPlayingMedia!! )

            if(disconnectedHub) return

            isPlaying = false
            if (currentPlayingMedia != null && checkHls(currentPlayingMedia!!)) {
                playMedia(currentPlayingMedia!!,isForceValue)
            }
            else{
                Log.d(TAG,"stop from reconnecting...")
                stop()
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
        val dataSourceFactory = DefaultDataSourceFactory(appContext,null, okHttpFactory)
        exoPlayer = SimpleExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exoPlayer.addListener(exoEventListener)
    }

    override fun playMedia(mediaItem: MediaItem, isForce : Boolean,timeStamp: Int) {
        if(isPlaying) stop()

        try {
            currentPlayingMedia = mediaItem
            isForceValue = isForce
            playHlsExo(mediaItem)
        }
        catch (e :Exception)
        {
            Log.e(TAG, "Error playing media using Exo")
            e.printStackTrace()
        }
    }
    private fun playHlsExo(mediaItem: MediaItem) {

        val em = com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.pathBackup)

        exoPlayer.setMediaItem(em)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        if(!isForceValue) {
            GlobalScope.launch {
                delay((getDurationInSecond(mediaItem.duration!!) * 1000).toLong())
                withContext(Dispatchers.Main)
                {
                    exoPlayer.stop()
                    Log.d(TAG, "stop")
                    eventListener.onMediaEndReached(currentPlayingMedia)
                }
            }
        }
    }

    override fun reset() {

    }

    override fun stop() {
        try {
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

    override fun onLostHubConnection() {
        disconnectedHub = true
    }

    override fun onHubReconnected() {
        disconnectedHub = false

        if (currentPlayingMedia != null && checkHls(currentPlayingMedia!!) && !isPlaying) {
            playMedia(currentPlayingMedia!!,isForceValue)
        }
    }



    /**
     * Check if a stream is still in time to play
     * **/
    private fun checkHls(mediaItem: MediaItem): Boolean {
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }
        val check = (now < liveEndTime)

        Log.d(TAG, "check hls: $check")

        return check
    }
}