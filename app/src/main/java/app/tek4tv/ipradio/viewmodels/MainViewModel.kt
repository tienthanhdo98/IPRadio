package app.tek4tv.ipradio.viewmodels


import android.content.Context
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.MediaWatchDog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    fun getPlayerContent(listUrl: String, needUpdate: Boolean)
    {
        viewModelScope.launch {
            getContentJob?.join()

            getContentJob = launch {
                val mediaList = mediaRepo.getBroadcastList(needUpdate, listUrl)

                content.value = Pair(mediaList, listOf())
            }
        }
    }

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    override fun onCleared() {
        super.onCleared()
        mediaRepo.cancelAllJob()
    }
}