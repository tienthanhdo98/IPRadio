package app.tek4tv.ipradio.core

import android.content.Context
import android.util.Log
import android.view.View
import app.tek4tv.ipradio.model.ForceMedia
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.lang.ref.WeakReference
import java.util.*

class PlayerManager(
    applicationContext: Context,
    private var viewModel: MainViewModel,
    private var rootLayout: View
) {
    private val mLibVLC: LibVLC
    private val audioPlayer: CustomPlayer
    private val playlistRepo: MediaRepo
        get() = viewModel.mediaRepo

    var currentPlaylist = listOf<MediaItem>()

    //-2 for force media
    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var currentPlaylistKey = "0"

    var playerCallback: PlayerCallback? = null

    var playlistIndex = 0

    var isTimeOver = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        val args = ArrayList<String>()
        args.add("-vvv")

        mLibVLC = LibVLC(applicationContext, args)
        audioPlayer = CustomPlayer(mLibVLC)

        instance = this
    }


    private fun playMediaByIndex(index: Int) {
        try {
            val playlist = currentPlaylist

            if (playlist.isEmpty()) return
            val mediaItem = playlist[index]

            playMedia(mediaItem)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playScheduledMediaByIndex(mediaIndex: Int) {
        val scheduledItems = viewModel.mediaRepo.scheduledMediaItems
        if (mediaIndex >= scheduledItems.size) return

        playMedia(scheduledItems[mediaIndex])
    }

    private fun playMedia(mediaItem: MediaItem) {
        try {
            audioPlayer.eventListener = { _, _, _, _, _ -> }

            when (mediaItem.getMediaType()) {
                MediaType.AUDIO -> {
                    setMainPlayerEventListener()
                    val media = mediaItem.getVlcMedia(mLibVLC)
                    /*media.parse()
                    Log.d(LOGTAG,media.duration.toString())*/
                    audioPlayer.play(media)
                }
                MediaType.LIVE -> {
                    val liveUri = mediaItem.getVlcMedia(mLibVLC)
                    audioPlayer.play(liveUri)
                }
                MediaType.TUNER -> {
                    playerCallback?.onPlayTuner(mediaItem)
                }
                else -> {
                    playNextMedia()
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
            val playlist = currentPlaylist
            playlistIndex++
            if (playlistIndex >= playlist.size) playlistIndex = 0

            playMediaByIndex(playlistIndex)
        }
    }

    private fun setMainPlayerEventListener() {
        audioPlayer.eventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
            when (event) {
                MediaPlayer.Event.EndReached -> {
                    playNextMedia()
                    audioPlayer.eventListener = { _, _, _, _, _ -> }
                }

                MediaPlayer.Event.EncounteredError -> {
                    playNextMedia()
                    audioPlayer.eventListener = { _, _, _, _, _ -> }
                }
            }
        }
    }


    fun setPlaylistByKey(playlistKey: String) {
        stopAllPlayer()
        playerCallback?.onStartTimeFrame(playlistKey)
        if (playlistKey == "-1") {
            currentPlaylistKey = "-1"
            setPlaylistContent(playlistRepo.unscheduledList)
        } else {
            currentPlaylistKey = playlistKey
            setPlaylistContent(playlistRepo.scheduledList[playlistKey] ?: listOf())
        }
        Log.d(LOGTAG, currentPlaylistKey)
    }

    private fun setPlaylistContent(playlist: List<MediaItem>) {
        this.currentPlaylist = playlist

        playlistIndex = 0

        if (currentPlaylist.isNotEmpty()) {

            //if the first item is scheduled, skip over and play the second media
            val firstItem = playlist[0]
            if (firstItem.fixTime != "00:00:00") {
                val now = Calendar.getInstance()
                val scheduledTime = toCalendar(firstItem.fixTime)

                if (now.timeInMillis < scheduledTime.timeInMillis && currentPlaylist.size > 2) {
                    playMediaByIndex(1)
                    playlistIndex = 1
                    return
                }
            }

            playMediaByIndex(0)
            isTimeOver = false
        }
    }

    fun startFirstPlaylist() {
        var found = false
        val scheduledList = viewModel.mediaRepo.scheduledList
        for (i in scheduledList.keys.indices) {
            val period = scheduledList.keys.elementAt(i)
            val t = period.split("-")
            val start = toCalendar(t[0])
            val end = toCalendar(t[1])

            /*var isNewDayStarted = false
            if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)
            ) isNewDayStarted = true

            if (isNewDayStarted) {
                if (start.get(Calendar.HOUR_OF_DAY) > end.get(Calendar.HOUR_OF_DAY)) end.add(
                    Calendar.DAY_OF_MONTH, 1)
                else {
                    start.add(Calendar.DAY_OF_MONTH, 1)
                    end.add(Calendar.DAY_OF_MONTH, 1)
                }
            }*/

            val now = Calendar.getInstance().timeInMillis

            if (now in start.timeInMillis..end.timeInMillis) {
                setPlaylistByKey(period)
                found = true
                break
            }
        }

        //random playlist
        if (!found)
            setPlaylistByKey("-1")
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != currentPlaylistKey) return
        currentPlaylistKey = ""
        stopAllPlayer()

        playerCallback?.onEndTimeFrame(cancelListKey)

        //check time over
        isTimeOver = true
        coroutineScope.launch {
            delay(15000)
            if (isTimeOver) playerCallback?.onTimeOver()
        }
    }

    private fun stopAllPlayer() {
        audioPlayer.eventListener = { _, _, _, _, _ -> }

        try {
            audioPlayer.stop()
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
        if(currentPlaylistKey != PLAY_MODE_FORCE) return

        stopAllPlayer()
        playerCallback?.onEndForceMedia()
    }

    fun requestRescheduleMedias()
    {
        playerCallback?.rescheduleMedias()
    }

    fun onActivityStop() {
        audioPlayer.stop()
    }

    fun onActivityDestroy() {
        if (!audioPlayer.isReleased) audioPlayer.release()
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
}