package app.tek4tv.ipradio.core.players_helper

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.LOG_FOLDER_NAME
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*


class VlcMediaPlayer(
    private val playerEventListener: IPlayerEventListener,
    vlcVideoLayout: VLCVideoLayout
) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: MediaPlayer
    private var currentMedia: MediaItemModel? = null
    private var currentVlcMedia: Media? = null

    private var isPlaying = false

    private var countReplayLive = 0

    override fun isPlaying(): Boolean = isPlaying

    var handlerLivePlayer: Handler? = null

    private var vlcEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.EndReached -> {
                Log.e(TAG, "EndReached ${currentMedia?.getMediaType()}")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    playerEventListener.onMediaEndReached(currentMedia)
                } else {
                    playerEventListener.onMediaForceEndReached(currentMedia)
                }

            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError ${currentMedia?.title}")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    Log.e(TAG, "EncounteredError File")
//                    checkStreamError()
                    isPlaying = false
                } else {
                    Log.e(TAG, "EncounteredError Live")
                    if (!disconnectedHub)
                        onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.Playing -> {
//                if (!isPlaying) {
                Log.e(TAG, "Playing ${currentMedia?.title}")
                isPlaying = true
                playerEventListener.onPlayMediaSuccess(currentMedia)
//                }
            }
        }
    }

     val path = "${appContext.filesDir.path}/vlc_log.txt"
    init {
        val args = ArrayList<String>()
        //bật log vlc
        args.add("-vvv")
        args.add("--no-drop-late-frames")
         args.add("--file-caching=500")
         args.add("--network-caching=2000")
        //args.add("--skip-frames")
        // args.add("--no-skip-frames")
        //args.add("--aout=opensles")
        args.add("--verbose=2")
        args.add("--file-logging")
        args.add("--logfile=$path")

        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = MediaPlayer(mLibVLC)
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false)
        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        vlcPlayer.setEventListener(vlcEventListener)
    }


    override fun playMedia(mediaItem: MediaItemModel, isForce: Boolean, timeStamp: Int) {

//        if (isPlaying) stop()

        if (isForce) {
            playLiveStream(mediaItem, timeStamp)
        } else {
            playFile(mediaItem, timeStamp)
        }

        currentMedia = mediaItem

    }

    override fun reset() {

    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        // vlcPlayer.play()

        val mediaStoragePath = appContext.filesDir.path
        Log.d("mediaStoragePath", "resume: $mediaStoragePath")
        try {
            val stream = Media(
                mLibVLC,
                Uri.parse("$mediaStoragePath/test.webp")
            )
//
            vlcPlayer.play(stream)
        } catch (e: Exception) {
            Log.d(TAG, "resume: ${e.message}")
        }


//        currentVlcMedia = stream


    }


    override fun stop() {
        try {
            handlerLivePlayer?.removeCallbacksAndMessages(null)
            vlcPlayer.stop()
            isPlaying = false
            currentMedia = null
            currentVlcMedia = null
            Log.d(TAG, "stop vlc")
        } catch (e: Exception) {
            Log.d(TAG, "stop error :$e ")
            isPlaying = false
            currentMedia = null
            currentVlcMedia = null
        }
    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }

    override var disconnectedHub = false

    private fun playFile(mediaItem: MediaItemModel, timeStamp: Int) {
        Log.e(TAG, "vlc play file")
        val media = mediaItem.getVlcMedia(mLibVLC)
        // Handle image-specific options
        if (mediaItem.body!!.contains("jpg") || mediaItem.body!!.contains("png")) {
            //media.addOption(":image-duration=${getMediaDuration(mediaItem) / 1000}")
            media.addOption(":image-duration=5")
        }

        vlcPlayer.play(media)
        currentVlcMedia = media

    }

    private fun playLiveStream(mediaItem: MediaItemModel, timeStamp: Int) {
        Log.e(TAG, "vlc play live : time stamp $timeStamp")
        Log.e(TAG, "vlc play time duration ${getDurationInSecond(mediaItem.duration!!)}")
        val stream = mediaItem.getVlcMedia(mLibVLC)
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        if (timeStamp != -1)
            stream.addOption(":start-time=$timeStamp")
        currentVlcMedia = stream
        vlcPlayer.play(stream)
        Log.e(TAG, "Player vlc live start")
        handlerLivePlayer?.removeCallbacksAndMessages(null)
        handlerLivePlayer = Handler(Looper.getMainLooper())
        handlerLivePlayer?.postDelayed(
            {
                vlcPlayer.stop()
                if (currentMedia?.getMediaType() == MediaType.FILE) {
//                    val isStreamFailed = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)
//                    if (!isStreamFailed) {
//                        Log.e(TAG, "EndReached ${currentMedia?.title}")
//                        Log.d(TAG, "Player vlc live stop file")
//                        playerEventListener.onMediaEndReached(currentMedia)
//                        isPlaying = false
//                    } else {
//                        Log.e(TAG, "EncounteredError  ${currentMedia?.title}")
//                        checkStreamError()
//                    }
                    playerEventListener.onMediaEndReached(currentMedia)
                } else {
                    Log.d(TAG, "Player vlc live stop force")
                    playerEventListener.onMediaForceEndReached(currentMedia)

                }

            },
            (getDurationInSecond(mediaItem.duration) * 1000).toLong() - currentTimeStamp * 1000
        )
    }

    //check xem có đang trong giờ phát luồng live
    private fun checkLiveStream(mediaItem: MediaItemModel, stream: Media): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }

        return ((now <= liveEndTime)
                && (!isPlaying))
    }

//    private fun checkTimeFrameStream(mediaItem: MediaItemModel, stream: Media): Boolean {
//        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false
//
//        val now = Calendar.getInstance()
//
//        val liveEndTime = toCalendar(mediaItem.start!!).apply {
//            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!) - 2)
//        }
//
//        val check = (now <= liveEndTime)
//                && (stream.state != IMedia.State.Playing)
//
//        Log.e(TAG, "${now <= liveEndTime}")
//        Log.e(TAG, "${stream.state != IMedia.State.Playing}")
//
//
//        return check
//    }


    private fun checkStreamError() {
//        if (currentMedia == null || currentVlcMedia == null) return
//
//        try {
//            val isStillInTimeFrame = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)
//
//            if (isStillInTimeFrame) {
//                isPlaying = false
//                playerEventListener.onPlayerError(
//                    VlcException("Stream failed!"),
//                    true,
//                    currentMedia!!
//                )
////                val timeStamp2 = calculateResumePoint(currentMedia!!)
//                Handler(Looper.getMainLooper()).postDelayed({
//                    if (currentMedia != null) {
//                        if (currentMedia!!.body!!.contains("rtsp://") || currentMedia!!.body!!.contains(
//                                "rtmp://"
//                            )
//                        ) {
//                            playMedia(currentMedia!!, true,timeStamp2)
//                        }
//                        if (currentMedia!!.pathBackup.contains("http://") || currentMedia!!.pathBackup.contains(
//                                "https://"
//                            )
//                        ) {
//                            playMedia(currentMedia!!, false,timeStamp2)
//                        }
//                    }
//                }, 5000)
//
//            } else {
//                Log.d(TAG, "Stopping from reconnect")
//                playerEventListener.onMediaEndReached(currentMedia)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
    }

    fun getMediaDuration(mediaItem: MediaItemModel): Int {
        if (mediaItem.duration != null) {
            val mediaDuration = getDurationInSecond(mediaItem.duration)
            if (mediaDuration > 0) return mediaDuration * 1000
        }
        return 15000

    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!, currentVlcMedia!!)
            //chi check luong live loi va phat lai 4 lan
            if (isStreamFailed && countReplayLive < 4) {
                isPlaying = false
                playerEventListener.onPlayerError(
                    VlcException("Stream failed!"),
                    true,
                    currentMedia!!
                )
                //playMedia(currentMedia!!)
                Handler(Looper.getMainLooper()).postDelayed({
                    countReplayLive++
                    Log.d(TAG, "onLiveErrorVlc: Chay lai live")
                    if (currentMedia != null) {
                        playMedia(currentMedia!!, true)
                    }
                }, 5000)

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