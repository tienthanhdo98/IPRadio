package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.*

class VlcMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    vlcVideoLayout: VLCVideoLayout
) : IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: MediaPlayer
    private var currentMedia: MediaItem? = null
    private var handler: Handler? = null

    private var vlcEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.EndReached -> {
                Log.e(TAG, "EndReached File")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)

                } else {
//                    onLiveErrorVlc()
                    Log.e(TAG, "EndReached Live")
                    playerEventListener.onMediaForceEndReached(currentMedia)

                }
            }

            MediaPlayer.Event.EncounteredError -> {

                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    Log.e(TAG, "EncounteredError File")
                    playerEventListener.onPlayerError(
                        VlcException("EncounteredError"), false, currentMedia!!
                    )
                } else {
                    Log.e(TAG, "EncounteredError Live")
//                    onLiveErrorVlc()
                    playerEventListener.onMediaForceEndReached(currentMedia)
                }
            }

            MediaPlayer.Event.Playing -> {
                Log.e(TAG, "Playing ${currentMedia?.title}")
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
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false)
        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        vlcPlayer.setEventListener(vlcEventListener)


    }

    override fun playMedia(mediaItem: MediaItem, timeStamp: Int) {
        when (mediaItem.getMediaType()) {
            MediaType.FILE -> {
                playFile(mediaItem, timeStamp)
            }

            MediaType.LIVE -> {
                playLiveStream(mediaItem)
            }

        }

        currentMedia = mediaItem
    }


    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()
    }

    override fun reset() {
    }

    override fun stop() {
        handler?.removeCallbacksAndMessages(null)
        vlcPlayer.stop()
        vlcPlayer.media = null
        currentMedia = null
        Log.d(TAG, "stop vlc")
    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }


    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        val media = mediaItem.getVlcMedia(mLibVLC)
        //neu file chua tai xong ma cho option start-time se khong chay.
        if (timeStamp != -1 && mediaItem.contentType?.id != 2 && File(mediaItem.body!!).exists()) {
            media.addOption(":start-time=$timeStamp")
        }

        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem) {
        Log.e(TAG, "vlc play live")
        val stream = mediaItem.getVlcMedia(mLibVLC)
        vlcPlayer.media = stream
        Handler(Looper.getMainLooper()).postDelayed({
            vlcPlayer.play(stream)
        }, 200) // thử 100-200ms
        Log.e(TAG, "Player vlc live body ${mediaItem.body}")
        Log.e(TAG, "Player vlc live contentType ${mediaItem.contentType?.id}")
        Log.e(TAG, "Player vlc live duration ${mediaItem.duration}")
        //luong live
        if (mediaItem.contentType?.id == 5) {
            handler?.removeCallbacksAndMessages(null)
            handler = Handler(Looper.getMainLooper()).apply {
                postDelayed(
                    {
                        vlcPlayer.stop()
                        if (currentMedia?.getMediaType() == MediaType.FILE) {
                            val isStreamFailed = checkTimeFrameStream(currentMedia!!)
                            if (!isStreamFailed) {
                                Log.e(TAG, "EndReached ${currentMedia?.title}")
                                Log.d(TAG, "Player vlc live stop file")
                                playerEventListener.onMediaEndReached(currentMedia)
                            } else {
                                Log.e(TAG, "EncounteredError handler live ${currentMedia?.title}")
                                checkLiveStream(currentMedia!!)
                            }
                        } else {
                            Log.d(TAG, "Player vlc live stop force")
                            playerEventListener.onMediaForceEndReached(currentMedia)
                        }
                    }, (getDurationInSecond(mediaItem.duration!!) * 1000).toLong()
                )
            }

        }
    }

    private fun checkTimeFrameStream(mediaItem: MediaItem): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.start!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!) - 2)
        }

        val check = (now <= liveEndTime)

        Log.e(TAG, "${now <= liveEndTime}")


        return check
    }


    //check xem có đang trong giờ phát luồng live
    private fun checkLiveStream(mediaItem: MediaItem): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }

        val check = (now <= liveEndTime)

        Log.e("check", "${now <= liveEndTime}")

        Log.d(TAG, "check: $check")

        return check
    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!)

            if (isStreamFailed) {
                playerEventListener.onPlayerError(
                    VlcException("Stream failed!"), true, currentMedia!!
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