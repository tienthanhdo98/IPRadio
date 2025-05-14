package app.tek4tv.ipradio.core.players_helper

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.LOGTAG
import app.tek4tv.ipradio.core.players_helper.PlayersManager.Companion.handlerLivePlayer
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.*


class VlcMediaPlayer(
    appContext: Context,
    private val playerEventListener: IPlayerEventListener,
    vlcVideoLayout: VLCVideoLayout
) :
    IMediaPlayer {
    private val TAG = "VlcMediaPlayer"
    private var mLibVLC: LibVLC
    private var vlcPlayer: CustomPlayer
    private var currentMedia: MediaItem? = null
    private var currentVlcMedia: Media? = null

    private var isPlaying = false

    private var lastError = -1
    private var countReplayLive = 0


    override fun isPlaying(): Boolean = isPlaying

    private val vlcEventListener = { event: Int, _: Long, _: Long, _: Float, _: String? ->
        when (event) {
            MediaPlayer.Event.EndReached -> {
                vlcVideoLayout.setBackgroundColor(Color.RED)
                if (currentMedia?.getMediaType() == MediaType.FILE) {
                    val isStreamFailed = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)
                    if (!isStreamFailed) {
                        Log.e(TAG, "EndReached ${currentMedia?.title}")
                        playerEventListener.onMediaEndReached(currentMedia)
                        isPlaying = false
                    } else {
                        Log.e(TAG, "EncounteredError file  ${currentMedia?.title}")
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
                vlcVideoLayout.setBackgroundColor(Color.TRANSPARENT)
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
//        args.add("--no-drop-late-frames")
//        args.add("--file-caching=1000")
//        args.add("--network-caching=2000")
        //se ko bi dung o frame video cuoi nhung co bug o 1 vai truong hop, hieu suat kem hon
//        args.add("--vout=android-display")
        args.add("--skip-frames")
        args.add("--drop-late-frames")
        args.add("--file-caching=1000")
        args.add("--network-caching=1000")

        mLibVLC = LibVLC(appContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false)
//        vlcPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        vlcPlayer.eventListener = vlcEventListener
    }

    override fun playMedia(mediaItem: MediaItem, isForce: Boolean, timeStamp: Int) {
        if (isPlaying) stop()
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
//        vlcPlayer.play()
//        val mediaStoragePath = appContext.filesDir.path
//        Log.d("mediaStoragePath", "resume: $mediaStoragePath")
        val stream = Media(
            mLibVLC,
            Uri.parse("https://storage-devkhanhhoa.tek4tv.vn//2025/3/11/11/774926481_xe-dap---thuy-chi-ft.-hoang-dung-[at-concert-25].mp3")
        )

        currentVlcMedia = stream

        vlcPlayer.play(stream)
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

    override fun onLostHubConnection() {
        disconnectedHub = true
    }

    override fun onHubReconnected() {
        disconnectedHub = false

        if (currentMedia == null || currentMedia?.getMediaType() != MediaType.LIVE) return

        if (checkLiveStream(currentMedia!!, currentVlcMedia!!))
            playLiveStream(currentMedia!!, -1)
    }

    private fun playFile(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play file : time stamp $timeStamp")
        val media = mediaItem.getVlcMedia(mLibVLC)
        Log.e(TAG, "vlc play timeStamp $timeStamp")
        //neu file chua tai xong ma cho option start-time se khong chay.
        if (timeStamp != -1 && mediaItem.contentType?.value != "2" && File(mediaItem.body!!).exists()) {
            media.addOption(":start-time=$timeStamp")
        }
        currentVlcMedia = media
        vlcPlayer.play(media)
    }

    private fun playLiveStream(mediaItem: MediaItem, timeStamp: Int) {
        Log.e(TAG, "vlc play live : time stamp $timeStamp")
        Log.e(TAG, "vlc play time duration ${getDurationInSecond(mediaItem.duration!!)}")
        val stream = mediaItem.getVlcMedia(mLibVLC)
        var currentTimeStamp = 0
        if (timeStamp >= 0) currentTimeStamp = timeStamp
        if (timeStamp != -1)
            stream.addOption(":start-time=$timeStamp")
        currentVlcMedia = stream
        vlcPlayer.play(stream)
        Log.e(LOGTAG, "Player vlc live start ${mediaItem.body}")
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
                        Log.e(TAG, "EncounteredError handler live ${currentMedia?.title}")
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

        Log.d(TAG, "checkLiveStream isStillTimeFrame ${now <= liveEndTime}")


        return check
    }

    private fun checkTimeFrameStream(mediaItem: MediaItem, stream: Media): Boolean {
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
        if (currentMedia == null || currentVlcMedia == null) return

        try {
            val isStillInTimeFrame = checkTimeFrameStream(currentMedia!!, currentVlcMedia!!)

            if (isStillInTimeFrame) {
                isPlaying = false
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