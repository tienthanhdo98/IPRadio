package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.core.PlayerManager
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
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

    private var isPlaying = false

    private var lastError = -1
   // private var endCount = 0

    override fun isPlaying(): Boolean = isPlaying

    private val vlcEventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
        when (event) {
            MediaPlayer.Event.EndReached -> {
                Log.e(TAG, "EndReached")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)
                    isPlaying = false
                } else {
                    if (!disconnectedHub)
                        onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onPlayerError(VlcException("EncounteredError"))
                    isPlaying = false
                } else {
                    if (!disconnectedHub)
                        onLiveErrorVlc()
                }
                lastError = event
            }

            MediaPlayer.Event.Playing -> {
                if (!isPlaying) {
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
        //bật log vlc
        //args.add("-vvv")

        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        if (isPlaying) stop()

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
        currentMedia = null
        currentVlcMedia = null
    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }

    override var disconnectedHub = false

    override fun onLostHubConnection() {
        disconnectedHub = true
    }

    override fun onHubReconnected() {
        disconnectedHub = false

        if (currentMedia == null || currentMedia?.getMediaType() != MediaType.LIVE) return

        if (checkLiveStream(currentMedia!!, currentVlcMedia!!))
            playLiveStream(currentMedia!!)
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

    //check xem có đang trong giờ phát luồng live
    private fun checkLiveStream(mediaItem: MediaItem, stream: Media): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }

        val check = (now <= liveEndTime)
                && (stream.state != IMedia.State.Playing)

        Log.e("check", "${now <= liveEndTime}")
        Log.e("check", "${stream.state != IMedia.State.Playing}")

        Log.d(PlayerManager.LOGTAG, "check: $check")

        return check
    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!, currentVlcMedia!!)

            if (isStreamFailed) {
                isPlaying = false
                playerEventListener.onPlayerError(VlcException("Stream failed!"), true)
                playLiveStream(currentMedia!!)
            } else {
                Log.d(TAG, "Stopping from reconnect")
                stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class VlcException(mess: String? = null) : Exception(mess ?: "")