package app.tek4tv.ipradio.viewmodels

import android.app.Activity
import android.view.View
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    var currentMedia: MediaItem? = null

    fun rescheduleMonthlyPlaylist()
    {
        val jsonMonthlyListData = ConfigUtil.getString(appContext,
            ConfigUtil.MONTHLY_SCENARIO_WSS_DATA, "") ?: ""
        updateMonthlyScenarioWss(jsonMonthlyListData)
    }


    fun updateMonthlyScenarioWss(strJsonMonthSchedule: String)
    {
        viewModelScope.launch {
            mediaRepo.updateMonthlyScenarioWss(strJsonMonthSchedule)
            getPlayerContentWss()
        }
    }
    fun getPlayerContentWss()
    {
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



    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
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
}