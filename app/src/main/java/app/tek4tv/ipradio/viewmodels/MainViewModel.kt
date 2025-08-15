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

    @OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.N)
    fun capture(playerView: PlayerView, logService: LogService) {
        // Lấy đúng video surface của PlayerView
        val surfaceView = (playerView.videoSurfaceView as? SurfaceView) ?: return

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // chỉ chụp khi surface đã valid
                doPixelCopy(surfaceView, logService)
                surfaceView.holder.removeCallback(this)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })

        // Nếu đã valid thì chụp luôn
        if (surfaceView.holder.surface?.isValid == true) {
            doPixelCopy(surfaceView, logService)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun doPixelCopy(surfaceView: SurfaceView, logService: LogService) {
        val w = surfaceView.width.coerceAtLeast(1)
        val h = surfaceView.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        PixelCopy.request(surfaceView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                saveBitmap(bitmap, logService)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveBitmap(bitmap: Bitmap, logService: LogService) {
        // Create directory for saving
        val captureModel = CaptureScreenModel(
            Imei = hubManager.deviceImei,
            SourceId = GlobalProperties.getInst().destinationId,
            SourceName = GlobalProperties.getInst().destinationName,
            Base64Image = bitmapToBase64(bitmap)
        )
        try {
            viewModelScope.launch {
                val res = logService.postScreenCapture(
                    captureModel,
                    "https://v2-provider-data-warehouse.tek4tv.vn/DigitalDataLog/digital/capture"
                )
                Log.d("captureInfo", "saveBitmap S: ${captureModel}")
                Log.d("captureInfo", "saveBitmap S: ${res}")
            }

        } catch (e: Exception) {
            Log.d("captureInfo", "saveBitmap ERR: ${e.message}")
        }
    }
}