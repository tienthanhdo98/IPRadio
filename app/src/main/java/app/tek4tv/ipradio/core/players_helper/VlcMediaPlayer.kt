package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.LOGTAG
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.handlerLivePlayer
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.isInRangeTimePlay
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.util.*

class VlcMediaPlayer(appContext: Context, private val playerEventListener: IPlayerEventListener) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: CustomPlayer
    private var currentMedia: MediaItem? = null
    private var isPlaying = false
    var countReplayLive = 0


    override fun isPlaying(): Boolean = isPlaying

    private val vlcEventListener = { event: Int, _: Long, _: Long, _: Float, _: String? ->
        when (event) {
            MediaPlayer.Event.EndReached -> {
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    val isStreamFailed = checkTimeFramePlaying(currentMedia!!)
                    if (!isStreamFailed) {
                        Log.e(TAG, "EndReached ${currentMedia?.title}")
                        playerEventListener.onMediaEndReached(currentMedia)
                        isPlaying = false
                    } else {
                        Log.e(TAG, "EncounteredError  ${currentMedia?.title}")
                        checkStreamError()
                    }
                } else {
                    playerEventListener.onMediaForceEndReached(currentMedia)
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "EncounteredError ${currentMedia?.title}")
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    Log.e(TAG, "EncounteredError File")
                    checkStreamError()
                    isPlaying = false
                } else {
                    Log.e(TAG, "EncounteredError Live")
                    onLiveErrorVlc()
                }
            }

            MediaPlayer.Event.Playing -> {
                if (!isPlaying) {
                    Log.e(TAG, "Playing ${currentMedia?.title}")
                    isPlaying = true
                    playerEventListener.onPlayMediaSuccess(currentMedia)
                }
            }

            else -> {
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
//        args.add("-vvv")//
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener

    }


    override fun playMedia(mediaItem: MediaItem, isForce: Boolean,timeStamp: Int) {
        if (isPlaying) stop()
//        if (NetworkUtils.isNetworkConnected(MyApp.appContext)) {
//            if (isForce) {
//                playLiveStream(mediaItem, timeStamp)
//            } else {
//                if (isInRangeTimePlay()) {
//                    playFile(mediaItem, timeStamp)
//                }
//            }
//            currentMedia = mediaItem
//        }
        if (isForce) {
            playLiveStream(mediaItem, timeStamp)
        } else {
            if (isInRangeTimePlay()) {
                playFile(mediaItem, timeStamp)
            }
        }
        currentMedia = mediaItem
    }

    override fun reset() {

    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
        vlcPlayer.play()

//        val stream = Media(
//            mLibVLC,
//            Uri.parse("http://14.224.130.186:8000/binhdinh")
//        )
//        stream.addOption(":rtsp-tcp")
//        stream.addOption(":network-caching=20")
//
//        stream.addOption(":run-time=600")
//        currentVlcMedia = stream
//
//        vlcPlayer.play(stream)
//        val handler = Handler(Looper.getMainLooper())
//        handler.postDelayed(
//            {
//                vlcPlayer.stop()
//                playerEventListener.onMediaEndReached(currentMedia)
//            },
//            60000
//        )
//        val stream = Media(
//            mLibVLC,
//            Uri.parse("http://14.224.130.186:8000/binhdinh")
//        )
//
//        currentVlcMedia = stream
//
//        vlcPlayer.play(stream)
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play file : time stamp $timeStamp")
        val media = mediaItem.getVlcMedia(mLibVLC)
        Log.e(TAG, "vlc play timeStamp $timeStamp")
        if (timeStamp != -1)
            media.addOption(":start-time=$timeStamp")

        vlcPlayer.play(media)

    }

    private fun playLiveStream(mediaItem: MediaItem,timeStamp: Int) {
        Log.e(TAG, "vlc play live : time stamp $timeStamp")
        Log.e(TAG, "vlc play time duration ${getDurationInSecond(mediaItem.duration!!)}")
        val stream = mediaItem.getVlcMedia(mLibVLC)
//        stream.addOption(":rtsp-tcp")
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        //Chỉ phát tiếp bản tin trực tiếp khi bản tin là type: file(3/4)
        if (timeStamp != -1 && (mediaItem.contentType?.id == 3.toLong() || mediaItem.contentType?.id == 4.toLong()) )
            stream.addOption(":start-time=$timeStamp")
        vlcPlayer.play(stream)
        Log.e(LOGTAG, "Player vlc live start")
        handlerLivePlayer?.removeCallbacksAndMessages(null)
        handlerLivePlayer = Handler(Looper.getMainLooper())
        handlerLivePlayer?.postDelayed(
            {
                vlcPlayer.stop()
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    val isStreamFailed = checkTimeFramePlaying(currentMedia!!)
                    if (!isStreamFailed) {
                        Log.e(TAG, "EndReached ${currentMedia?.title}")
                        Log.d(TAG, "Player vlc live stop file")
                        playerEventListener.onMediaEndReached(currentMedia)
                        isPlaying = false
                    } else {
                        Log.e(TAG, "EncounteredError  ${currentMedia?.title}")
                        checkStreamError()
                    }
                } else {
                    Log.d(TAG, "Player vlc live stop force")
                    playerEventListener.onMediaForceEndReached(currentMedia)
                }

            },
            (getDurationInSecond(mediaItem.duration) * 1000).toLong() - currentTimeStamp * 1000
        )

    }

    //check xem có đang trong giờ phát luồng live
    private fun checkLiveStream(mediaItem: MediaItem): Boolean {
        if (currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.end!!)

        val check = (now <= liveEndTime)

        Log.d(TAG, "checkLiveStream isStillTimeFrame ${now <= liveEndTime}")
        Log.d(TAG, "checkLiveStream isPlaying${check}")

        return check
    }

    private fun checkTimeFramePlaying(mediaItem: MediaItem): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val startTime = toCalendar(mediaItem.startTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!) - 2)
        }

        val check = (now <= startTime)

        Log.e(TAG, "${now <= startTime}")


        return check
    }


    private fun checkStreamError() {
        if (currentMedia == null) return

        try {
            playerEventListener.onPlayerError(
                VlcException("Stream failed!"),
                currentMedia!!
            )
            val isStillInTimeFrame = checkTimeFramePlaying(currentMedia!!)
            if (isStillInTimeFrame) {
                isPlaying = false

                val timeStamp2 = calculateResumePoint(currentMedia!!)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentMedia != null) {
                        if (currentMedia!!.body!!.contains("rtsp://") || currentMedia!!.body!!.contains(
                                "rtmp://"
                            )
                        ) {
                            playMedia(currentMedia!!, true, timeStamp2)
                        }
                        if (currentMedia!!.pathBackup.contains("http://") || currentMedia!!.pathBackup.contains(
                                "https://"
                            )
                        ) {
                            playMedia(currentMedia!!, false, timeStamp2)
                        }
                    }
                }, 10000)

            } else {
                Log.d(TAG, "Stop.Not try replay")
                playerEventListener.onMediaEndReached(currentMedia)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onLiveErrorVlc() {
        if (currentMedia == null) return

        try {
            playerEventListener.onPlayerError(
                VlcException("Stream failed!"),
                currentMedia!!
            )
            val isStreamFailed = checkLiveStream(currentMedia!!)
            if (isStreamFailed && countReplayLive < 4) {
                isPlaying = false

                Handler(Looper.getMainLooper()).postDelayed({
                    countReplayLive++
                    Log.d(TAG, "onLiveErrorVlc: Replay live")
                    playMedia(currentMedia!!, true)
                }, 10000)

            } else {
                Log.d(TAG, "Stop.Not try replay. Send long util live end time frame")
                stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        try {
            handlerLivePlayer?.removeCallbacksAndMessages(null)
            vlcPlayer.stop()
            isPlaying = false
            currentMedia = null
        } catch (e: Exception) {
            Log.d(TAG, "stop error :$e ")
            isPlaying = false
            currentMedia = null
        }

    }

    override fun release() {
        vlcPlayer.release()
        mLibVLC.release()
    }
}

class VlcException(mess: String? = null) : Exception(mess ?: "")