package app.tek4tv.ipradio.viewmodels

import android.app.Activity
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.MyApp.Companion.appContext
import app.tek4tv.ipradio.di.RetrofitInstance.moshi
import app.tek4tv.ipradio.model.MediaItemModel
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel : ViewModel() {
    val mediaRepo: MediaRepo = MediaRepo(moshi)
    val content = MutableLiveData<Pair<List<MediaItemModel>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)


    fun rescheduleMonthlyPlaylist() {
        val scenarioData = ConfigUtil.getString(
            appContext,
            ConfigUtil.SCENARIO_DATA, ""
        ) ?: ""
        updateMonthlyScenarioWss(scenarioData)
    }


    fun updateMonthlyScenarioWss(message: String) {
        viewModelScope.launch {
            mediaRepo.updateMonthlyScenarioWss(message)
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

    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
        mediaRepo.startDownloadDefaultMedia()
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