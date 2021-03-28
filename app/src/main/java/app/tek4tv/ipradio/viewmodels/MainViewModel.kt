package app.tek4tv.ipradio.viewmodels


import android.content.Context
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.AudioRepo
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.MediaWatchDog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo, val audioRepo: AudioRepo
) : ViewModel() {

    var isPlaying: Boolean = false
    /*val broadcastList = MutableLiveData<List<MediaItem>>()
    val audioList = MutableLiveData<List<String>>()*/
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()


    /*private var getPlaylistJob: Job? = null
    private var getAudioListJob: Job? = null*/
    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo, audioRepo)

    var playlistIndex = 0

    var currentMediaItem: MediaItem? = null

    var currentAudioName = ""


    /*fun getPlaylist(needUpdate: Boolean, listUrl: String) {
        viewModelScope.launch {
            getPlaylistJob?.join()
            getPlaylistJob = viewModelScope.launch {
                val res = mediaRepo.getBroadcastList(needUpdate, listUrl)
                broadcastList.value = res
            }
        }
    }*/

    fun getPlayerContent(listUrl: String, musicUrl : String, needUpdate: Boolean)
    {
        viewModelScope.launch {
            getContentJob?.join()

            getContentJob = launch {
                val mediaList = mediaRepo.getBroadcastList(needUpdate, listUrl).shuffled()
                val musicList = audioRepo.getAudioUrls(musicUrl, needUpdate)

                content.value = Pair(mediaList, musicList)
            }
        }
    }

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
        mediaWatchdog.checkAudio()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    /*fun getAudioList(needUpdate: Boolean, url: String) {
        viewModelScope.launch {
            getAudioListJob?.join()
            getAudioListJob = launch {
                val audioUrls = audioRepo.getAudioUrls(url, needUpdate)
                audioList.postValue(audioUrls)
            }
        }
    }*/

    override fun onCleared() {
        super.onCleared()
        audioRepo.cancelAllJob()
        mediaRepo.cancelAllJob()
    }
}