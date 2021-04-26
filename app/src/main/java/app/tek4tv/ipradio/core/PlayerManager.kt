package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.util.*

class PlayerManager(
    applicationContext: Context,
    private var viewModel: MainViewModel,
    private var rootLayout: View
) {
    private val mLibVLC: LibVLC
    private val vlcPlayer: CustomPlayer

    private val exoPlayer = SimpleExoPlayer.Builder(applicationContext).build()

    private val playlistRepo: MediaRepo
        get() = viewModel.mediaRepo

    var currentPlaylist = listOf<MediaItem>()

    //-2 for force media
    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    var currentPlaylistKey = "0"

    var playerCallback: PlayerCallback? = null

    var playlistIndex = 0

    var isTimeOver = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var currentPlayingMedia: MediaItem? = null
    var currentVlcMedia: Media? = null

    private var isUsingExoPlayer = false
    private var currentExoState = -1

    private val exoEventListener = object : Player.EventListener {
        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            currentExoState = state

            Log.d(LOGTAG,"exo state: $state")
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)

            Log.d(LOGTAG,"exoerror: ${error.message?:""}")
            error.printStackTrace()

            if(currentPlayingMedia!=null && checkHls(currentPlayingMedia!!))
                playMedia(currentPlayingMedia!!)
        }
    }

    init {
        val args = ArrayList<String>()
        args.add("-vvv")

        mLibVLC = LibVLC(applicationContext, args)
        vlcPlayer = CustomPlayer(mLibVLC)

        instance = this

        /*coroutineScope.launch {
            while (true)
            {
                delay(3000)
                Log.d(LOGTAG,"vlc media: ${currentVlcMedia?.state ?: ""}")
            }
        }*/
    }


    private fun playMediaByIndex(index: Int, timeStamp: Int = -1) {
        try {
            if (currentPlaylist.isEmpty()) return
            val mediaItem = currentPlaylist[index]

            //skip LIVE and TUNER as they will be played by alarm
            if (mediaItem.getMediaType() == MediaType.VOD)
                playMedia(mediaItem, timeStamp)

            Log.d(LOGTAG, "Play media $index")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = viewModel.mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        val itemToPlay = scheduledItems[mediaIndex]

        for (frame in playlistRepo.scheduledList.keys) {
            val t = frame.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            val indexInSubList = playlistRepo.scheduledList[frame]!!.indexOf(itemToPlay)

            if (toCalendar(itemToPlay.fixTime) in (start..end) && indexInSubList != -1) {
                playlistIndex = indexInSubList
                break
            }
        }

        playMedia(itemToPlay)
    }


    fun endScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = viewModel.mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        val itemToEnd = scheduledItems[mediaIndex]
        if (itemToEnd.id != currentPlayingMedia?.id) return

        stopAllPlayer()

        //playlistIndex = currentPlaylist.indexOf(itemToEnd)
        playNextMedia()
    }

    private fun playMedia(mediaItem: MediaItem, timeStamp: Int = -1) {

        currentPlayingMedia = mediaItem

        if (currentVlcMedia != null) {
            if (!currentVlcMedia!!.isReleased)
                currentVlcMedia?.release()
        }

        isUsingExoPlayer = false

        try {
            vlcPlayer.eventListener = { _, _, _, _, _ -> }
            exoPlayer.removeListener(exoEventListener)

            when (mediaItem.getMediaType()) {
                MediaType.VOD -> {
                    playVOD(mediaItem, timeStamp)
                }
                MediaType.LIVE -> {
                    playLiveStream(mediaItem)
                }
                MediaType.FM -> {
                    playerCallback?.onPlayTuner(mediaItem)
                    currentVlcMedia = null
                }
                else -> {
                    playNextMedia()
                    currentVlcMedia = null
                }
            }

            playerCallback?.onPlayNewMedia(playlistIndex, mediaItem)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playNextMedia() {
        if (currentPlaylistKey == "") return

        rootLayout.post {
            playlistIndex++
            if (playlistIndex >= currentPlaylist.size) playlistIndex = 0

            playMediaByIndex(playlistIndex)
        }
    }

    private fun playVOD(mediaItem: MediaItem, timeStamp: Int) {
        vlcPlayer.eventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
            when (event) {
                MediaPlayer.Event.EndReached -> {
                    vlcPlayer.eventListener = { _, _, _, _, _ -> }
                    Log.d(LOGTAG, "EndReached")
                    playNextMedia()
                }

                MediaPlayer.Event.EncounteredError -> {
                    vlcPlayer.eventListener = { _, _, _, _, _ -> }
                    Log.e(LOGTAG, "EncounteredError")
                    playNextMedia()
                }
            }
        }

        val media = mediaItem.getVlcMedia(mLibVLC)

        if (timeStamp != -1)
            media.addOption(":start-time=$timeStamp")

        currentVlcMedia = media

        vlcPlayer.play(media)
    }

    private var reconnectingLiveStreamJob: Job? = null

    private fun playLiveStream(mediaItem: MediaItem) {

        if(mediaItem.path.endsWith("m3u8"))
        {
            playHlsExo(mediaItem)
        }
        else
        {
            playStreamVlc(mediaItem)
        }
    }

    private fun playStreamVlc(mediaItem: MediaItem)
    {
        val stream = mediaItem.getVlcMedia(mLibVLC)
        stream.addOption(":rtsp-tcp")

        vlcPlayer.play(stream)

        currentVlcMedia = stream

        vlcPlayer.eventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
            //Log.d(LOGTAG, "event: $event")
            when (event) {
                MediaPlayer.Event.EndReached -> {
                    Log.d(LOGTAG, "EndReached")
                    if (checkLiveStream(mediaItem, stream) && reconnectingLiveStreamJob == null)
                        tryReconnectLiveStream(mediaItem)
                    playerCallback?.onLiveError()
                }

                MediaPlayer.Event.EncounteredError -> {
                    Log.e(LOGTAG, "EncounteredError")
                    if (checkLiveStream(mediaItem, stream) && reconnectingLiveStreamJob == null)
                        tryReconnectLiveStream(mediaItem)
                    playerCallback?.onLiveError()
                }
            }
        }
    }

    private fun playHlsExo(mediaItem: MediaItem)
    {
        isUsingExoPlayer = true
        val em = com.google.android.exoplayer2.MediaItem.fromUri(mediaItem.path)
        exoPlayer.addListener(exoEventListener)
        exoPlayer.setMediaItem(em)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    private fun tryReconnectLiveStream(mediaItem: MediaItem) {
        reconnectingLiveStreamJob?.cancel()
        reconnectingLiveStreamJob = coroutineScope.launch(Dispatchers.Default) {
            var needReconnect = true
            while (needReconnect) {
                Log.d(LOGTAG, "reconnect: ${mediaItem.path}")
                playMedia(mediaItem)

                delay(10000)

                needReconnect = checkLiveStream(mediaItem, currentVlcMedia!!)
            }
            Log.d(LOGTAG, "reconnect: exit")
            reconnectingLiveStreamJob = null
        }
    }
    //check to see if there is an error when playing livestream
    private fun checkLiveStream(mediaItem: MediaItem, stream: Media): Boolean {
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration))
        }
        val check = (now < liveEndTime)
                && (stream.state != IMedia.State.Playing)

        Log.d(LOGTAG, "check: $check")

        return check
    }

    private fun checkHls(mediaItem: MediaItem): Boolean {
        val now = Calendar.getInstance()

        val liveEndTime = toCalendar(mediaItem.fixTime).apply {
            add(Calendar.SECOND, getDurationInSecond(mediaItem.duration))
        }
        val check = (now < liveEndTime)
                && (currentExoState != Player.STATE_READY)

        Log.d(LOGTAG, "check: $check")

        return check
    }


    fun setTimeFrame(timeFrame: String, resumePoint: Pair<Int, Int>?) {
        stopAllPlayer()
        playerCallback?.onStartTimeFrame(timeFrame)
        currentPlaylistKey = timeFrame

        currentPlaylist = playlistRepo.scheduledList[timeFrame] ?: listOf()

        if (resumePoint != null) {
            playlistIndex = resumePoint.first
            playMediaByIndex(playlistIndex, resumePoint.second)
        } else {
            playlistIndex = 0
            playMediaByIndex(playlistIndex)
        }

        isTimeOver = false
    }

    fun startFirstPlaylist() {
        isTimeOver = true
        val scheduledList = viewModel.mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            val now = Calendar.getInstance().timeInMillis

            if (now in start.timeInMillis..end.timeInMillis) {

                val resumePoint = calculateResumePoint(start, period)
                setTimeFrame(period, resumePoint)

                break
            }
        }

        checkTimeOver()
    }

    //return an index and timestamp of the media that would be play
    //when we jump in the middle of a time frame
    private fun calculateResumePoint(start: Calendar, timeFrame: String): Pair<Int, Int> {
        try {
            val mediaList = playlistRepo.scheduledList[timeFrame] ?: return Pair(0, 0)
            val now = Calendar.getInstance()
            val secondFromStart = ((now.timeInMillis - start.timeInMillis) / 1000).toInt()

            var t = 0

            for (i in mediaList.indices) {
                val media = mediaList[i]
                val mediaDuration = getDurationInSecond(media.duration)

                t += mediaDuration

                if (t > secondFromStart) {
                    return if(GlobalProperties.resumeFromStart) {
                        Pair(i, 0)
                    } else {
                        val startTimeStamp = mediaDuration - (t - secondFromStart)
                        Pair(i, startTimeStamp)
                    }
                }
            }

            return Pair(0, 0)
        } catch (e: Exception) {
            Log.e(LOGTAG, "Error calculate resume")
            e.printStackTrace()
            return Pair(0, 0)
        }
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != currentPlaylistKey) return
        currentPlaylistKey = ""
        stopAllPlayer()

        playerCallback?.onEndTimeFrame(cancelListKey)

        //check time over
        isTimeOver = true
        checkTimeOver()
    }

    private fun checkTimeOver() {
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerCallback?.onTimeOver()
        }
    }

    fun stopAllPlayer() {
        vlcPlayer.eventListener = { _, _, _, _, _ -> }

        try {
            if(isUsingExoPlayer)
            {
                exoPlayer.stop()
            }
            else
            {
                if(vlcPlayer.isPlaying)
                    vlcPlayer.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playForceMedia(forceMedia: ForceMedia) {
        currentPlaylistKey = PLAY_MODE_FORCE
        stopAllPlayer()
        playMedia(forceMedia.toMediaItem())
    }

    fun stopForceMedia() {
        if (currentPlaylistKey != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerCallback?.onEndForceMedia()
    }

    fun requestRescheduleMedias() {
        playerCallback?.rescheduleMedias()
    }

    fun onActivityStop() {
        //audioPlayer.stop()
        stopAllPlayer()
    }

    fun onActivityDestroy() {
        if (!vlcPlayer.isReleased) vlcPlayer.release()
        mLibVLC.release()
    }

    companion object {
        const val LOGTAG = "player"
        const val PLAY_MODE_RANDOM = "-1"
        const val PLAY_MODE_FORCE = "-2"
        var instance: PlayerManager? = null
    }
}

interface PlayerCallback {
    fun onPlayNewMedia(index: Int, mediaItem: MediaItem)
    fun onTimeOver()
    fun onStartTimeFrame(frame: String)
    fun onEndTimeFrame(frame: String)
    fun onPlayTuner(mediaItem: MediaItem)
    fun onEndForceMedia()
    fun rescheduleMedias()
    fun onLiveError()
}