package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.net.Uri
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
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.*

class VlcMediaPlayer(appContext: Context, private val playerEventListener: IPlayerEventListener) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: CustomPlayer
    private var currentMedia: MediaItem? = null
    private var currentVlcMedia: Media? = null

    private var isPlaying = false

    private var lastError = -1
    // private var endCount = 0
    var countReplayLive = 0



    override fun isPlaying(): Boolean = isPlaying

    private val vlcEventListener = { event: Int, _: Long, _: Long, _: Float, _: String? ->
        when (event) {
            MediaPlayer.Event.EndReached -> {
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    val isStreamFailed = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)
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
                    if (!disconnectedHub)
                        onLiveErrorVlc()
                }
                lastError = event
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
//        args.add("-vvv")
        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.eventListener = vlcEventListener

    }


    override fun playMedia(mediaItem: MediaItem, isForce: Boolean,timeStamp: Int) {
        if (isPlaying) stop()
//        if (checkOldMediaPlaying(mediaItem) && !isForce ) stop()
//        when (mediaItem.getMediaType()) {
//            MediaType.FILE -> {
//                playFile(mediaItem, timeStamp)
//            }
//            MediaType.LIVE -> {
//                playLiveStream(mediaItem)
//            }
//            else -> {
//                Log.e(TAG, "cant play")
//            }
//        }
        if (NetworkUtils.isNetworkConnected(MyApp.appContext)) {
            if (isForce) {
                playLiveStream(mediaItem, timeStamp)
            } else {
                if (isInRangeTimePlay()) {
                    playFile(mediaItem, timeStamp)
                }
            }
            currentMedia = mediaItem
        }
    }

    override fun reset() {

    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override fun resume() {
//      vlcPlayer.play()

        val stream = Media(
            mLibVLC,
            Uri.parse("http://luuchieu.truyenhinhso.vn:8000/media/live/6FD7C899AC6864C87A7C3258/index.m3u8")
        )
        stream.addOption(":network-caching=20")

        stream.addOption(":run-time=600")
        currentVlcMedia = stream

        vlcPlayer.play(stream)
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
//            Uri.parse("rtmp://truyenthanhthongminh.vn/s6909/live")
//        )
//        stream.addOption(":network-caching=20")
//        currentVlcMedia = stream
//
//        vlcPlayer.play(stream)
    }


    override fun stop() {
//        if (vlcPlayer.isPlaying)
        try {
            handlerLivePlayer?.removeCallbacksAndMessages(null)
            vlcPlayer.stop()
            isPlaying = false
            currentMedia = null
            currentVlcMedia = null
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

    override fun onLostHubConnection() {
        disconnectedHub = true
    }

    override fun onHubReconnected() {
        disconnectedHub = false

        if (currentMedia == null || currentMedia?.getMediaType() != MediaType.LIVE) return

        if (checkLiveStream(currentMedia!!, currentVlcMedia!!))
            playLiveStream(currentMedia!!,-1)
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play file : time stamp $timeStamp")
        val media = mediaItem.getVlcMedia(mLibVLC)
        Log.e(TAG, "vlc play timeStamp $timeStamp")
        if (timeStamp != -1)
            media.addOption(":start-time=$timeStamp")

        currentVlcMedia = media

        vlcPlayer.play(media)

    }

    private fun playLiveStream(mediaItem: MediaItem,timeStamp: Int) {
        Log.e(TAG, "vlc play live : time stamp $timeStamp")
        Log.e(TAG, "vlc play time duration ${getDurationInSecond(mediaItem.duration!!)}")
        val stream = mediaItem.getVlcMedia(mLibVLC)
//        stream.addOption(":rtsp-tcp")
        stream.addOption(":network-caching=20")
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
//        stream.addOption(":run-time=${getDurationInSecond(mediaItem.duration) - currentTimeStamp}")
        if (timeStamp != -1)
            stream.addOption(":start-time=$timeStamp")
        currentVlcMedia = stream
        vlcPlayer.play(stream)
        Log.e(LOGTAG, "Player vlc live start")
        handlerLivePlayer?.removeCallbacksAndMessages(null)
        handlerLivePlayer = Handler(Looper.getMainLooper())
        handlerLivePlayer?.postDelayed(
            {
                vlcPlayer.stop()
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    val isStreamFailed = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)
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
    private fun checkLiveStream(mediaItem: MediaItem, stream: Media): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!))
        }
        val check = (now <= liveEndTime)
                && (stream.state != IMedia.State.Playing)

        Log.d(TAG, "checkLiveStream isStillTimeFrame ${now <= liveEndTime}")
        Log.d(TAG, "checkLiveStream isPlaying${stream.state != IMedia.State.Playing}")

        return check
    }

    private fun checkTimeFrameStream(mediaItem: MediaItem, stream: Media): Boolean {
        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false

        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.start!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!)-2)
        }

        val check = (now <= liveEndTime)
                && (stream.state != IMedia.State.Playing)

        Log.e(TAG, "${now <= liveEndTime}")
        Log.e(TAG, "${stream.state != IMedia.State.Playing}")


        return check
    }

    private fun checkOldMediaPlaying(mediaItem: MediaItem): Boolean {
//        if (currentMedia != null && currentMedia!!.body != mediaItem.body) return false
        Log.d(TAG, "old media:${mediaItem.body} ")
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.start!!).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration!!) - 2)
        }

        val check = (now >= liveEndTime)

        Log.e(TAG, "${getDurationInSecond(mediaItem.duration!!)}")
        Log.e(TAG, "${now <= liveEndTime}")

        Log.d(LOGTAG, "check old: $check")

        return check
    }

    private fun checkStreamError() {
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStillInTimeFrame = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)

            if (isStillInTimeFrame) {
                isPlaying = false
                playerEventListener.onPlayerError(
                    VlcException("Stream failed!"),
                    true,
                    currentMedia!!
                )
                val timeStamp2 = calculateResumePoint(currentMedia!!)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentMedia != null) {
                        if (currentMedia!!.body!!.contains("rtsp://") || currentMedia!!.body!!.contains(
                                "rtmp://"
                            )
                        ) {
                            playMedia(currentMedia!!, true,timeStamp2)
                        }
                        if (currentMedia!!.pathBackup.contains("http://") || currentMedia!!.pathBackup.contains(
                                "https://"
                            )
                        ) {
                            playMedia(currentMedia!!, false,timeStamp2)
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
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStreamFailed = checkLiveStream(currentMedia!!, currentVlcMedia!!)

            if (isStreamFailed && countReplayLive < 4) {
                isPlaying = false
                playerEventListener.onPlayerError(
                    VlcException("Stream failed!"),
                    true,
                    currentMedia!!
                )
                //playMedia(currentMedia!!)
                Handler(Looper.getMainLooper()).postDelayed({
                    countReplayLive++;
                    Log.d(TAG, "onLiveErrorVlc: Chay lai live")
                    playMedia(currentMedia!!, true)
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