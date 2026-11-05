package app.tek4tv.ipradio.viewmodels

import android.app.Activity
import android.util.Log
import android.view.View
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null
    private var retryJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)


    fun getPlayerContent(isFromUpdateMonthly: Boolean = false) {
        viewModelScope.launch {
            getContentJob?.join()
            mediaWatchdog.cancelCheck()

            getContentJob = launch {
                val mediaList = mediaRepo.getDataDayScenario(isFromUpdateMonthly)
                content.value = Pair(mediaList, listOf())

                if (mediaList.isNotEmpty()) {
                    Log.d("MEDIA_REPO", "Successfully loaded schedule, canceling retry job if any.")
                    retryJob?.cancel()
                    retryJob = null
                } else {
                    if (mediaRepo.lastUpdateFailed) {
                        Log.w("MEDIA_REPO", "Schedule is empty due to update failure, starting retry job.")
                        startScheduleRetryLoop()
                    } else {
                        Log.d("MEDIA_REPO", "Schedule is empty but fetch was successful. Canceling retry job.")
                        retryJob?.cancel()
                        retryJob = null
                    }
                }

                getContentJob = null
            }
        }
    }

    private fun startScheduleRetryLoop() {
        if (retryJob?.isActive == true) return

        retryJob = viewModelScope.launch(Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = 40
            while (isActive && attempt < maxAttempts) {
                val delayMinutes = when {
                    attempt < 10 -> 3L
                    attempt < 15 -> 5L
                    attempt < 20 -> 10L
                    else -> 20L
                }
                val delayMillis = delayMinutes * 60 * 1000L
                Log.d("MEDIA_REPO", "Schedule Retry in ${delayMinutes}min (attempt ${attempt + 1}/$maxAttempts)")
                delay(delayMillis)
                val mediaList = mediaRepo.getDataDayScenario()
                withContext(Dispatchers.Main) {
                    content.value = Pair(mediaList, listOf())
                    if (mediaList.isNotEmpty()) {
                        Log.d("MEDIA_REPO", "Successfully loaded schedule, breaking retry loop.")
                        retryJob?.cancel()
                    }
                }
                attempt++
            }
        }
    }


    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    fun updateMonthlyScenarioAndGetContent(url: String) {
        viewModelScope.launch {
            mediaRepo.getMonthlyScenario(url)
            getPlayerContent()
        }
    }

    fun updateMonthlyScenario(url: String) {
        viewModelScope.launch {
            mediaRepo.getMonthlyScenario(url)
            getPlayerContent(isFromUpdateMonthly = true)
        }
    }

    fun hideSystemUI(activity: Activity) {
        val decorView: View = activity.window.decorView
        val uiOptions = decorView.systemUiVisibility
        var newUiOptions = uiOptions
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE
        newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        decorView.systemUiVisibility = newUiOptions
    }

    fun clearDatastore(appStorageManager: AppStorageManager) {
        val limit = 524288000L
        val totalStorage = appStorageManager.getTotalRomStorage()
        val usedStorage = appStorageManager.getUsedRomStorage()
        if (totalStorage - usedStorage <= limit) {
            Log.d("clearDatastore", "clearDatastore: Safe cleaning logs, records, and unused media")
            appStorageManager.deleteAllLog()
            appStorageManager.deleteAllRecord()

            val todayFileNames = mediaRepo.broadcastList.mapNotNull { it.body?.let { path -> java.io.File(path).name } }.toSet()
            val allMediaFiles = java.io.File(mediaRepo.mediaStoragePath).listFiles() ?: emptyArray()
            for (file in allMediaFiles) {
                if (file.isFile && !todayFileNames.contains(file.name)) {
                    file.delete()
                }
            }
        } else {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
        }
    }

}
