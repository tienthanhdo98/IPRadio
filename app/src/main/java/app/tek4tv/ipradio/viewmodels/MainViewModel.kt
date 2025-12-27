package app.tek4tv.ipradio.viewmodels


import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    var currentMedia: MediaItem? = null

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    fun rescheduleMonthlyPlaylist() {
        val jsonMonthlyListData = ConfigUtil.getString(
            appContext,
            ConfigUtil.MONTHLY_SCENARIO_WSS_DATA, ""
        ) ?: ""
        updateMonthlyScenarioWss(jsonMonthlyListData)
    }


    fun updateMonthlyScenarioWss(strJsonMonthSchedule: String) {
        viewModelScope.launch {
            mediaRepo.updateMonthlyScenarioWss(strJsonMonthSchedule)
            getPlayerContentWss()
        }
    }

    fun getPlayerContentWss() {
        viewModelScope.launch {
            getContentJob?.join()

            //cancel media check here
            mediaWatchdog.cancelCheck()

            getContentJob = launch {
                val mediaList = mediaRepo.getScenarioWss()

                content.value = Pair(mediaList, listOf())

                getContentJob = null
            }
        }
    }

}