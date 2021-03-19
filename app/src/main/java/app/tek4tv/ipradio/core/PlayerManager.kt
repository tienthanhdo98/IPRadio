package app.tek4tv.ipradio.core

import android.content.Context
import android.net.Uri
import android.util.LayoutDirection
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.children
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.model.MediaType
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.ui.CustomPlayer
import app.tek4tv.ipradio.utils.getDurationInSecond
import app.tek4tv.ipradio.utils.toCalendar
import app.tek4tv.ipradio.viewmodels.MainViewModel
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaList
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCUtil
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*
import kotlin.coroutines.resume

class PlayerManager(
    private val applicationContext: Context,
    private var viewModel: MainViewModel,
    private var rootLayout : View
) {
    private var playMutedMediaJob: Job? = null

    private val mLibVLC: LibVLC
    private val audioPlayer: CustomPlayer

    private var mainPlayer: CustomPlayer? = null
        set(value) {
            field = value
            setMainPlayerEventListener()
        }

    private val playlistRepo: MediaRepo
        get() = viewModel.mediaRepo

    var currentPlaylist = listOf<MediaItem>()

    private var audioIndex = 0
    var audioList = mutableListOf<Uri>()

    //-1 for random unscheduled media
    //0 for init
    //HH:mm:ss-HH:mm:ss for scheduled media
    private var currentPlaylistKey = "0"

    var rotationMode = 0

    init {
        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--aout=opensles")


        mLibVLC = LibVLC(applicationContext, args)
        audioPlayer = CustomPlayer(mLibVLC)

        audioList = viewModel.audioRepo.audioFileUri

        instance = this
    }


    fun playMediaByIndex(index: Int) {
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
            viewModel.currentMediaItem = mediaItem

            playMutedMediaJob?.cancel()

            mainPlayer?.eventListener = {_,_,_,_,_ ->}
            audioPlayer.eventListener = {_,_,_,_,_ ->}

            when (mediaItem.getMediaType()) {
                MediaType.AUDIO -> {
                    mainPlayer = audioPlayer
                    val media = mediaItem.getVlcMedia(mLibVLC)
                    media.parse()
                    Log.d("playermane",media.duration.toString())
                    audioPlayer.play(media)

                }
                else -> {
                }
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMediaDuration(mediaItem: MediaItem): Long {
        if (mediaItem.duration != null) {
            val mediaDuration = getDurationInSecond(mediaItem.duration!!)
            if (mediaDuration > 0) return mediaDuration * 1000
        }

        return 15000L
    }


    private fun playNextMedia() {
        if (currentPlaylistKey == "") return

        rootLayout.post {
            val playlist = currentPlaylist
            viewModel.playlistIndex++
            if (viewModel.playlistIndex >= playlist.size) viewModel.playlistIndex = 0

            playMediaByIndex(viewModel.playlistIndex)
        }
    }

    private fun setMainPlayerEventListener() {
        if (mainPlayer != null) {
            mainPlayer!!.eventListener = { event: Int, l: Long, l1: Long, fl: Float, s: String? ->
                when (event) {
                    MediaPlayer.Event.EndReached -> {
                        playNextMedia()
                        mainPlayer!!.eventListener = {_,_,_,_,_ ->}
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        playMutedMediaJob?.cancel()
                        playMutedMediaJob = null

                        playNextMedia()
                        mainPlayer!!.eventListener = {_,_,_,_,_ ->}
                    }
                    MediaPlayer.Event.Stopped -> viewModel.isPlaying = false
                    MediaPlayer.Event.Playing -> viewModel.isPlaying = true
                }
            }
        }
    }


    fun setPlaylist(playlistKey: String) {
        stopAllPlayer()

        if(playlistKey=="-1")
        {
            currentPlaylistKey = "-1"
            setPlaylistContent(playlistRepo.unscheduledList, audioList)
        }
        else
        {
            currentPlaylistKey = playlistKey
            setPlaylistContent(playlistRepo.scheduledList[playlistKey] ?: listOf(), audioList)
        }

    }

    private fun setPlaylistContent(playlist: List<MediaItem>, audios: List<Uri>) {
        this.currentPlaylist = playlist
        //this.audioList = audios

        viewModel.playlistIndex = 0

        if (currentPlaylist.isNotEmpty()) {

            //if the first item is scheduled, skip over and play the second media
            val firstItem = playlist[0]
            if (firstItem.fixTime != "00:00:00") {
                val now = Calendar.getInstance()
                val scheduledTime = toCalendar(firstItem.fixTime)

                if (now.timeInMillis < scheduledTime.timeInMillis && currentPlaylist.size > 2) {
                    playMediaByIndex(1)
                    viewModel.playlistIndex = 1
                    return
                }
            }


            playMediaByIndex(0)
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

            val now = Calendar.getInstance().timeInMillis

            if (now in start.timeInMillis..end.timeInMillis) {
                setPlaylist(period)
                found = true
                break
            }
        }

        //random playlist
        if(!found)
            setPlaylist("-1")
    }

    fun stopPlaylist(cancelListKey: String) {
        if (cancelListKey != currentPlaylistKey) return
        currentPlaylistKey = ""
        stopAllPlayer()
    }

    private fun stopAllPlayer() {
        audioPlayer.eventListener = {_,_,_,_,_ ->}

        playMutedMediaJob?.cancel()

        try {
            audioPlayer.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onActivityStop() {
        mainPlayer?.stop()
        audioPlayer.stop()
    }

    fun onActivityDestroy() {
        playMutedMediaJob?.cancel()

        mainPlayer?.release()
        if (!audioPlayer.isReleased) audioPlayer.release()
        mLibVLC.release()
    }


    companion object {
        var instance: PlayerManager? = null
    }
}