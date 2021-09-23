package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.ignoreAllSSLErrors
import app.tek4tv.ipradio.utils.toCalendar
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
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

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)
            Log.d(TAG, "exoerror: ${error.message ?: ""}")

            //Live stream failed
            //if hub connection has been lost, exit then try to reconnect when hub connected again
            //else try to reconnect to livestream immediately

            eventListener.onPlayerError(error, true)

            if(disconnectedHub) return

            isPlaying = false
            if (currentPlayingMedia != null && checkHls(currentPlayingMedia!!)) {
                playMedia(currentPlayingMedia!!)
            }
            else{
                Log.d(TAG,"stop from reconnecting...")
                stop()
            }
        }
    }

    init {
        val okHttpClient = OkHttpClient.Builder().apply {
            ignoreAllSSLErrors()
        }.build()
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSourceFactory(appContext,null, okHttpFactory)
        exoPlayer = SimpleExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exoPlayer.addListener(exoEventListener)
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        if(isPlaying) stop()

        try {
            currentPlayingMedia = mediaItem
            playHlsExo(mediaItem)
        }
        catch (e :Exception)
        {
            Log.e(TAG, "Error playing media using Exo")
            e.printStackTrace()
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

    override fun release() {
        exoPlayer.release()
    }

    override fun onLostHubConnection() {
        disconnectedHub = true
    }

    override fun onHubReconnected() {
        disconnectedHub = false

        if (currentPlayingMedia != null && checkHls(currentPlayingMedia!!) && !isPlaying) {
            playMedia(currentPlayingMedia!!)
        }
    }

    private fun playHlsExo(mediaItem: MediaItem) {
        val em = com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.path)
        exoPlayer.setMediaItem(em)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    private fun checkHls(mediaItem: MediaItem): Boolean {
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration))
        }
        val check = (now < liveEndTime)

        Log.d(TAG, "check hls: $check")

        return check
    }
}