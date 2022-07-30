package app.tek4tv.ipradio.viewmodels

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import app.tek4tv.ipradio.utils.toCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.withTestContext
import kotlinx.coroutines.withContext
import java.util.*


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    var currentMedia: MediaItem? = null

    fun getPlayerContent(listUrl: String, needUpdate: Boolean) {
        viewModelScope.launch {
            getContentJob?.join()

            //cancel media check here
            mediaWatchdog.cancelCheck()

            getContentJob = launch {
                val mediaList = mediaRepo.getScenario(needUpdate, listUrl)

                content.value = Pair(mediaList, listOf())

                getContentJob = null
            }
        }
    }

    fun checkTimeAndLevel(url: String) =
        flow {
            val mediaItem = mediaRepo.getPlaylist(url)
            val start = toCalendar(mediaItem.start!!)
            val end = toCalendar(mediaItem.end!!)
            val now = Calendar.getInstance().timeInMillis
            val result = now in start.timeInMillis..end.timeInMillis
            emit(result)

        }

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    fun updateMonthlyScenario(url: String, forceNewScenario: Boolean = false) {
        viewModelScope.launch {
            mediaRepo.updateMonthlyScenario(url, forceNewScenario)
            getPlayerContent("", false)
        }
    }
}