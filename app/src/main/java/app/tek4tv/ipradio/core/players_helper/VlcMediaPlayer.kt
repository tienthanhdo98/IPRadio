package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

class VlcMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    vlcVideoLayout: VLCVideoLayout
) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: MediaPlayer
    private var currentMedia: MediaItem? = null
    private var currentVlcMedia: Media? = null

    private var vlcEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.EndReached -> {
                Log.e(TAG, "EndReached")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)

                } else {
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onPlayerError(
                        VlcException("EncounteredError"),
                        false,
                        currentMedia!!
                    )
                } else {
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.Playing -> {

                playerEventListener.onPlayMediaSuccess(currentMedia)

            }
        }
    }

    init {
        val args = ArrayList<String>()
        //bật log vlc
//        args.add("-vvv")
//        args.add("--no-drop-late-frames")
//        args.add("--no-skip-frames")
//       args.add("--vout=android-display")
//        args.add("--vout=gles2")
        //       args.add("--android-display-chroma=YV12")
//        args.add("--avcodec-hw=any")
//        RotateScreen("270",args)
        args.add("--skip-frames")
        args.add("--drop-late-frames")
        args.add("--file-caching=1500")
        args.add("--network-caching=1500")


        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = MediaPlayer(mLibVLC)
//        vlcPlayer.aspectRatio = "9:16"
//        vlcPlayer.scale = 3.0f
//        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_16_9
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false)
//        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT

        vlcPlayer.setEventListener(vlcEventListener)


    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()
    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
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
//        val media =  Media(mLibVLC, Uri.parse("https://storage-qltt.tek4tv.vn/2024//09//04//1920X1080MP4/aeca576005d74396ac87dd6a936c7bbe.mp4"))
//
//        currentVlcMedia = media
//
//        vlcPlayer.play(media)
    }

    override fun stop() {
        vlcPlayer.stop()
        currentMedia = null
        currentVlcMedia = null
    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }


    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        val media = mediaItem.getVlcMedia(mLibVLC)
        //val videoPath = mediaItem.body
//        if (!isVideoResolutionSupported(videoPath, 1920, 1080))
//        {
//            Log.d("player", "videoPath: $videoPath")
//        }
        if (timeStamp != -1)
            media.addOption(":start-time=$timeStamp")

        currentVlcMedia = media

        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem) {
        val stream = mediaItem.getVlcMedia(mLibVLC)
//        stream.addOption(":rtsp-tcp")
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

        Log.d(TAG, "check: $check")

        return check
    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!, currentVlcMedia!!)

            if (isStreamFailed) {
                playerEventListener.onPlayerError(
                    VlcException("Stream failed!"),
                    true,
                    currentMedia!!
                )
                //  playLiveStream(currentMedia!!)
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