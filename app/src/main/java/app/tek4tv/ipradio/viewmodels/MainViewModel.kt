package app.tek4tv.ipradio.viewmodels


import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val mediaRepo: MediaRepo,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null
    private var retryJob: Job? = null

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
            context,
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

                if (mediaList.isNotEmpty()) {
                    Log.d("MEDIA_REPO", "Successfully loaded schedule, canceling retry job if any.")
                    retryJob?.cancel()
                    retryJob = null
                } else {
                    Log.d(
                        "MEDIA_REPO",
                        "Schedule is empty (no schedule for today). Starting periodic check in case schedule is updated later."
                    )
                    startScheduleRetryLoop()
                }

                getContentJob = null
            }
        }
    }

    fun startPeriodicScheduleCheckLoop() {
        startScheduleRetryLoop()
    }

    private fun startScheduleRetryLoop() {
        if (retryJob?.isActive == true) return

        retryJob = viewModelScope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                val delayMinutes = 30L
                val delayMillis = delayMinutes * 60 * 1000L
                Log.d(
                    "MEDIA_REPO",
                    "Periodic schedule check in ${delayMinutes}min (cycle ${attempt + 1})"
                )
                delay(delayMillis)
                attempt++
                val mediaList = mediaRepo.getScenarioWss()
                withContext(Dispatchers.Main) {
                    if (mediaList.isNotEmpty()) {
                        Log.d("MEDIA_REPO", "Successfully loaded schedule, breaking periodic retry loop.")
                        content.value = Pair(mediaList, listOf())
                        retryJob?.cancel()
                        retryJob = null
                    }
                }
            }
        }
    }
}
