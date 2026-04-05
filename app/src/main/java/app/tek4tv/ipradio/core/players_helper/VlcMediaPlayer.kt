package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.LOGTAG
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.handlerLivePlayer
import app.tek4tv.ipradio.model.MediaFile
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.io.File

class VlcMediaPlayer(
    private val appContext: Context, private val playerEventListener: IPlayerEventListener
) : IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: CustomPlayer
    private var currentMedia: MediaFile? = null
    private var hasStartedPlaying = false
    private var isBufferingTimeoutScheduled = false
    private var bufferingHandler = Handler(Looper.getMainLooper())

    private val vlcEventListener = { event: Int, _: Long, _: Long, _: Float, _: String? ->
        when (event) {
            MediaPlayer.Event.Opening -> {
                hasStartedPlaying = false
                isBufferingTimeoutScheduled = false
                bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
                Log.d(TAG, "Vlc Opening")
                Unit
            }

            MediaPlayer.Event.Buffering -> {
                Log.d(TAG, "Vlc Buffering")
                if (!hasStartedPlaying && currentMedia != null && !isBufferingTimeoutScheduled) {
                    isBufferingTimeoutScheduled = true
                    bufferingHandler.postDelayed(bufferingTimeoutRunnable, 20_000L)
                }
                Unit
            }

            MediaPlayer.Event.Playing -> {
                Log.d(TAG, "Vlc Playing")
                hasStartedPlaying = true
                isBufferingTimeoutScheduled = false
                bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
                playerEventListener.onPlayMediaSuccess(currentMedia)
            }

            MediaPlayer.Event.Stopped -> {
                Log.d(TAG, "Vlc Stopped")
                isBufferingTimeoutScheduled = false
                bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
                Unit
            }

            MediaPlayer.Event.EndReached -> {
                Log.d(TAG, "Vlc EndReached")
                isBufferingTimeoutScheduled = false
                bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
                playerEventListener.onMediaEndReached(currentMedia)
            }


            MediaPlayer.Event.EncounteredError -> {
                Log.d(TAG, "Vlc EncounteredError")
                isBufferingTimeoutScheduled = false
                bufferingHandler.removeCallbacks(bufferingTimeoutRunnable)
                playerEventListener.onPlayerError("Lỗi", currentMedia)
            }


            else -> {
            }
        }
    }

    private val bufferingTimeoutRunnable = Runnable {
        if (!hasStartedPlaying && currentMedia != null) {
            Log.d("MediaInfo", "Vlc Buffering timeout")
            playerEventListener.onPlayerError("Timeout khi tải stream", currentMedia)
        }
    }

    init {
        val args = ArrayList<String>()
        //bật log vlc
//        args.add("-vvv")//
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener

    }


    override fun playMedia(mediaItem: MediaFile, isForce: Boolean, timeStamp: Int) {
//        if (isPlaying) stop()
        currentMedia = mediaItem
        if (isForce) {
            playLiveStream(mediaItem, timeStamp)
        } else {
            playFile(mediaItem, timeStamp)

        }

    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()
    }

    private fun playFile(mediaItem: MediaFile, timeStamp: Int) {
        try {
            Log.e(TAG, "vlc play file : time stamp $timeStamp")
            Log.e("MediaInfo", "vlc play type file : ${mediaItem.duration}")
            val media = mediaItem.getVlcMedia(mLibVLC, appContext)
            val fileName = getFileNameFromUrl(mediaItem.url ?: "")
            val localFile = File(appContext.filesDir.path, fileName)
            Log.e(TAG, "vlc play timeStamp $timeStamp")
            if (timeStamp > 0 && localFile.exists() && localFile.isFile) {
                media.addOption(":start-time=$timeStamp")
                Log.e("MediaInfo", "vlc play type file set start time")
            }

            vlcPlayer.play(media)
        } catch (e: Exception) {
            Log.e("MediaInfo", "vlc play type file :error ${e.message}")
        }

    }

    private fun playLiveStream(mediaItem: MediaFile, timeStamp: Int) {
        Log.e("MediaInfo", "vlc play type stream : ${mediaItem.duration}")
        Log.e(TAG, "vlc play live : time stamp $timeStamp")
        Log.e(TAG, "vlc play time duration ${mediaItem.duration!!}")
        val stream = mediaItem.getVlcMedia(mLibVLC, appContext)
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        vlcPlayer.play(stream)
        Log.e(LOGTAG, "Player vlc live start")
        val durationMs = (mediaItem.duration * 1000).toLong() - currentTimeStamp * 1000
        if (durationMs > 0) {
            bufferingHandler?.removeCallbacksAndMessages(null)
            bufferingHandler = Handler(Looper.getMainLooper())
            bufferingHandler.postDelayed(
                {
                    vlcPlayer.stop()
                    playerEventListener.onMediaEndReached(currentMedia)

                }, (mediaItem.duration * 1000).toLong() - currentTimeStamp * 1000 + 500
            )
        }

    }

    //check xem có đang trong giờ phát luồng live
//    private fun checkLiveStream(mediaItem: MediaFile): Boolean {
//        if (currentMedia!!.url != mediaItem.url) return false
//
//        val now = Calendar.getInstance()
//
//        val liveEndTime = toCalendar(mediaItem.du!!)
//
//        val check = (now <= liveEndTime)
//
//        Log.d(TAG, "checkLiveStream isStillTimeFrame ${now <= liveEndTime}")
//        Log.d(TAG, "checkLiveStream isPlaying${check}")
//
//        return check
//    }
//
//    private fun checkTimeFramePlaying(mediaItem: MediaFile): Boolean {
//        if (currentMedia != null && currentMedia!!.url != mediaItem.url) return false
//
//        val now = Calendar.getInstance()
//
//        val startTime = toCalendar(mediaItem.startTime!!).apply {
//            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!) - 2)
//        }
//
//        val check = (now <= startTime)
//
//        Log.e(TAG, "${now <= startTime}")
//
//
//        return check
//    }


//    private fun checkStreamError() {
//        if (currentMedia == null) return
//
//        try {
//            playerEventListener.onPlayerError(
//                VlcException("Stream failed!"),
//                currentMedia!!
//            )
//            val isStillInTimeFrame = checkTimeFramePlaying(currentMedia!!)
//            if (isStillInTimeFrame) {
//                isPlaying = false
//
//                val timeStamp2 = calculateResumePoint(currentMedia!!)
//                Handler(Looper.getMainLooper()).postDelayed({
//                    if (currentMedia != null) {
//                        if (currentMedia!!.url!!.contains("rtsp://") || currentMedia!!.url!!.contains(
//                                "rtmp://"
//                            )
//                        ) {
//                            playMedia(currentMedia!!, true, timeStamp2)
//                        }
//                        if (currentMedia!!.pathBackup.contains("http://") || currentMedia!!.pathBackup.contains(
//                                "https://"
//                            )
//                        ) {
//                            playMedia(currentMedia!!, false, timeStamp2)
//                        }
//                    }
//                }, 10000)
//
//            } else {
//                Log.d(TAG, "Stop.Not try replay")
//                playerEventListener.onMediaEndReached(currentMedia)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    private fun onLiveErrorVlc() {
//        if (currentMedia == null) return
//
//        try {
//            playerEventListener.onPlayerError(
//                VlcException("Stream failed!"),
//                currentMedia!!
//            )
//            val isStreamFailed = checkLiveStream(currentMedia!!)
//            if (isStreamFailed && countReplayLive < 4) {
//                isPlaying = false
//
//                Handler(Looper.getMainLooper()).postDelayed({
//                    countReplayLive++
//                    Log.d(TAG, "onLiveErrorVlc: Replay live")
//                    playMedia(currentMedia!!, true)
//                }, 10000)
//
//            } else {
//                Log.d(TAG, "Stop.Not try replay. Send long util live end time frame")
//                stop()
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

    override fun stop() {
        try {
            handlerLivePlayer?.removeCallbacksAndMessages(null)
            vlcPlayer.stop()
            currentMedia = null
        } catch (e: Exception) {
            Log.d(TAG, "stop error :$e ")
            currentMedia = null
        }

    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }
}

class VlcException(mess: String? = null) : Exception(mess ?: "")