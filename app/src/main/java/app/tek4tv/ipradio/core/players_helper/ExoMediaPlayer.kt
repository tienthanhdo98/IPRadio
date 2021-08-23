package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.PlayerManager
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import java.util.*

class ExoMediaPlayer(
    appContext: Context,
    private val eventListener: IPlayerEventListener) : IMediaPlayer {

    private val TAG = "ExoMediaPlayer"

    private val exoPlayer = SimpleExoPlayer.Builder(appContext).build()

    private var currentPlayingMedia : MediaItem? = null

    var currentExoState = -1
        private set

    var isPlaying = false
        private set

    private val exoEventListener = object : Player.EventListener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            this@ExoMediaPlayer.isPlaying = isPlaying

            if(isPlaying) {
                eventListener.onPlayMediaSuccess(currentPlayingMedia)
                eventListener.requestTurnOnPaLive()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            currentExoState = state

            /*if (state == ExoPlayer.STATE_READY) {
                eventListener.requestTurnOnPaLive()
            }*/

            Log.d(TAG, "exo state: $state")
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)

            Log.d(TAG, "exoerror: ${error.message ?: ""}")

            eventListener.onPlayerError(error, true)

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun release() {
        exoPlayer.release()
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