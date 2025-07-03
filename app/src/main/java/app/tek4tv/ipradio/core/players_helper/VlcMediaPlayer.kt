package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.utils.TimeUtils.getDurationInSecond
import app.tek4tv.ipradio.utils.TimeUtils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.*


class VlcMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    vlcVideoLayout: VLCVideoLayout
) : BaseMediaPlayer(playerEventListener) {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: MediaPlayer
    private var countReplayLive = 0

    private var vlcEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.EndReached -> {
                if (currentMedia?.getMediaType() == MediaType.FILE) {
//                    val isStreamFailed = checkTimeFrameStream(currentMedia!!)
//                    if (!isStreamFailed) {
//                        Log.e(TAG, "EndReached ${currentMedia?.title}")
//                        playerEventListener.onMediaEndReached(currentMedia)
//                    } else {
//                        Log.e(TAG, "EndReached EncounteredError file  ${currentMedia?.title}")
//                        checkStreamError()
//                    }
                    playerEventListener.onMediaEndReached(currentMedia)
                    Log.e(TAG, "EndReached file ${currentMedia?.title}")
                } else {
                    Log.e(TAG, "EndReached force  ${currentMedia?.title}")
                    playerEventListener.onMediaForceEndReached(currentMedia)
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError ${currentMedia?.title}")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    Log.e(TAG, "EncounteredError File")
                    checkStreamError()
                } else {
                    Log.e(TAG, "EncounteredError Live")
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.Playing -> {
                Log.e(TAG, "Playing ${currentMedia?.title}")
                playerEventListener.onPlayMediaSuccess(currentMedia)
            }

        }
    }

    private fun calculateResumePoint(mediaItem: MediaItem): Int {
        val startTimeOfMedia = toCalendar(mediaItem.start!!)
        val now = Calendar.getInstance()
        return ((now.timeInMillis - startTimeOfMedia.timeInMillis) / 1000).toInt()
    }

    init {
        val args = ArrayList<String>()
        //bật log vlc
        //args.add("-vvv")
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = MediaPlayer(mLibVLC)
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false)
        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        vlcPlayer.setEventListener(vlcEventListener)

    }

    override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
        stop()
        if (isForce) {
            playLiveStream(mediaItem, timeStamp)
        } else {
            playFile(mediaItem, timeStamp)

        }
        currentMedia = mediaItem

    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()
    }


    override fun stop() {
        try {
            handler?.removeCallbacksAndMessages(null)
            vlcPlayer.stop()
            vlcPlayer.media = null
            currentMedia = null
            Log.d(TAG, "stop vlc")

        } catch (e: Exception) {
            Log.d(TAG, "stop error :$e ")
            currentMedia = null
        }
    }


    override fun release() {
//        vlcPlayer.release()
////        mLibVLC.release()
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play file : time stamp $timeStamp")
        val media = mediaItem.getVlcMedia(mLibVLC)
        //neu file chua tai xong ma cho option start-time se khong chay.
        if (timeStamp != -1 && mediaItem.contentType?.value != "2" && File(mediaItem.body!!).exists()) {
            media.addOption(":start-time=$timeStamp")
        }
        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play live")
        val stream = mediaItem.getVlcMedia(mLibVLC)
        vlcPlayer.media = stream
        Handler(Looper.getMainLooper()).postDelayed({
            vlcPlayer.play(stream)
        }, 200) // thử 100-200ms
        Log.e(TAG, "Player vlc live start ${mediaItem.body}")
        //luong live
        if (mediaItem.contentType?.value == "5") {
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
                                eventListener.onMediaEndReached(currentMedia)
                            } else {
                                Log.e(TAG, "EncounteredError handler live ${currentMedia?.title}")
                                checkStreamError()
                            }
                        } else {
                            Log.d(TAG, "Player vlc live stop force")
                            playerEventListener.onMediaForceEndReached(currentMedia)
                        }
                    },
                    (getDurationInSecond(mediaItem.duration!!) * 1000).toLong()
                )
            }

        }

    }

    //check xem có đang trong giờ phát luồng live
    private fun checkLiveStream(mediaItem: MediaItem): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }
        val check = (now <= liveEndTime)

        Log.d(TAG, "checkLiveStream isStillTimeFrame ${now <= liveEndTime}")


        return check
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


    private fun checkStreamError() {
        if (currentMedia == null) return

        try {
            val isStillInTimeFrame = checkTimeFrameStream(currentMedia!!)

            if (isStillInTimeFrame) {
                val timeStamp2 = calculateResumePoint(currentMedia!!)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentMedia != null) {
                        if (currentMedia!!.body!!.contains("rtsp://") || currentMedia!!.body!!.contains(
                                "rtmp://"
                            )
                        ) {
                            playerEventListener.onPlayerError(
                                VlcException("Stream failed!"),
                                true,
                                currentMedia!!
                            )
                            playMedia(currentMedia!!, true, timeStamp2)
                        }
                        if (currentMedia!!.pathBackup.contains("http://") || currentMedia!!.pathBackup.contains(
                                "https://"
                            )
                        ) {
                            playerEventListener.onPlayerError(
                                VlcException("Stream failed!"),
                                false,
                                currentMedia!!
                            )
                            playMedia(currentMedia!!, false, timeStamp2)
                        }
                    }
                }, 5000)

            } else {
                Log.d(TAG, "Stopping from reconnect")
                playerEventListener.onMediaEndReached(currentMedia)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!)
            //chi check luong live loi va phat lai 4 lan
            if (isStreamFailed && countReplayLive < 4) {
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