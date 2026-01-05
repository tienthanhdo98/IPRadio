package app.tek4tv.ipradio.viewmodels

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.view.children
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.tek4tv.ipradio.MyApp.Companion.hubManager
import app.tek4tv.ipradio.model.CaptureScreenModel
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils.Companion.bitmapToBase64
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)


    fun getPlayerContent() {
        viewModelScope.launch {
            getContentJob?.join()

            //cancel media check here
            mediaWatchdog.cancelCheck()

            getContentJob = launch {
                val mediaList = mediaRepo.getDataDayScenario()

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

    fun updateMonthlyScenarioAndGetContent(url: String) {
        viewModelScope.launch {
            mediaRepo.getMonthlyScenario(url)
            getPlayerContent()
        }
    }

    fun updateMonthlyScenario(url: String) {
        viewModelScope.launch {
            mediaRepo.getMonthlyScenario(url)
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
        //limit free data 1 gb
//        val limit = 1000000000
        //limit free data 500 mb
        val limit = 524288000
        val totalStorage = appStorageManager.getTotalRomStorage()
        val usedStorage = appStorageManager.getUsedRomStorage()
        if (totalStorage - usedStorage <= limit) {
            Log.d("clearDatastore", "clearDatastore: Must Clear")
            appStorageManager.deleteAllMedia()
        } else {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
        }
    }


}