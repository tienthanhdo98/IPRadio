package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.PlayerManager
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.*

class VlcMediaPlayer(appContext: Context, private val playerEventListener: IPlayerEventListener) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private val mLibVLC: LibVLC
    private val vlcPlayer: CustomPlayer
    private var currentMedia: MediaItem? = null
    private var currentVlcMedia: Media? = null

    var isPlaying = false
        private set

    private val vlcEventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
        when (event) {
            MediaPlayer.Event.EndReached -> {
                //vlcPlayer.eventListener = { _, _, _, _, _ -> }
                Log.d(TAG, "EndReached")

                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)
                    isPlaying = false
                }
                else {
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                //vlcPlayer.eventListener = { _, _, _, _, _ -> }
                Log.e(TAG, "EncounteredError")

                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onPlayerError(VlcException("EncounteredError"))
                    isPlaying = false
                }
                else {
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.Playing -> {
                if(!isPlaying)
                {
                    //playerEventListener.requestTurnOnPaLive()
                    isPlaying = true
                    playerEventListener.onPlayMediaSuccess(currentMedia)
                }
            }
            else -> {
            }
        }
    }

    init {
        val args = ArrayList<String>()
        args.add("-vvv")

        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        if(isPlaying) stop()

        when (mediaItem.getMediaType()) {
            MediaType.FILE -> {
                playFile(mediaItem, timeStamp)
            }
            MediaType.LIVE -> {
                playLiveStream(mediaItem)
            }
            else -> {
            }
        }

        currentMedia = mediaItem
    }

    override fun reset() {

    }

    override fun stop() {
        if (vlcPlayer.isPlaying)
            vlcPlayer.stop()
        isPlaying = false
    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        val media = mediaItem.getVlcMedia(mLibVLC)

        if (timeStamp != -1)
            media.addOption(":start-time=$timeStamp")

        currentVlcMedia = media

        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem) {
        val stream = mediaItem.getVlcMedia(mLibVLC)
        stream.addOption(":rtsp-tcp")

        currentVlcMedia = stream

        vlcPlayer.play(stream)
    }

    //check to see if there is an error when playing livestream
    private fun checkLiveStream(mediaItem: MediaItem, stream: Media): Boolean {
        if (currentMedia != null && currentMedia!!.path != mediaItem.path) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration))
        }

        val check = (now <= liveEndTime)
                && (stream.state != IMedia.State.Playing)

        Log.e("check", "${now <= liveEndTime}")
        Log.e("check", "${stream.state != IMedia.State.Playing}")

        Log.d(PlayerManager.LOGTAG, "check: $check")

        return check
    }

    private fun onLiveErrorVlc() {
        val isStreamFailed = checkLiveStream(currentMedia!!, currentVlcMedia!!)

        if (isStreamFailed) {
            isPlaying = false
            playerEventListener.onPlayerError(VlcException("Stream failed!"), true)
            playLiveStream(currentMedia!!)
        }
        else
        {
            Log.d(TAG,"Stopping from reconnect")
            stop()
        }
    }
}

class VlcException(mess: String? = null) : Exception(mess?:"")