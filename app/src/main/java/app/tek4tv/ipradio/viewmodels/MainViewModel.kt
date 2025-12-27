package app.tek4tv.ipradio.viewmodels


import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.execSu
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null


    var currentMedia: MediaItem? = null
    private var checkPlaylistJob: Job? = null

    fun getPlayerContent() {
        viewModelScope.launch {
            getContentJob?.join()

            //cancel media check here
            checkPlaylistJob?.cancel()

            getContentJob = launch {
                val mediaList = mediaRepo.getScenario()

                content.value = Pair(mediaList, listOf())

                getContentJob = null
            }
        }
    }

    fun checkPlaylist(appStorageManager: AppStorageManager) {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60 * 60_000L) // 60 phút mới kiểm tra lại
                clearDatastore(appStorageManager)
                Log.d("MediaRepo", "Checking media...")
                mediaRepo.checkPlaylistBroken()
            }
        }
    }

    fun downloadMedias() {
        viewModelScope.launch {
            mediaRepo.startDownloadMedia()
        }
    }

    fun stopDownloadMedia() {
        mediaRepo.stopDownloadMedia()
    }

    fun updateMonthlyScenario(url: String) {
        viewModelScope.launch {
            mediaRepo.safeUpdateMonthlyScenario(url)
            getPlayerContent()
        }
    }

    fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun clearDatastore(appStorageManager: AppStorageManager) {
        //limit free data 1 gb
        val limit = 1000000000
        //limit free data 500 mb
        //val limit = 524288000
        val totalStorage = appStorageManager.getTotalRomStorage()
        val usedStorage = appStorageManager.getUsedRomStorage()
        if (totalStorage - usedStorage <= limit) {
            Log.d("clearDatastore", "clearDatastore: Must Clear")
            appStorageManager.deleteAllMedia()
        } else {
            Log.d("clearDatastore", "clearDatastore: Dont have to clear")
        }
    }


    @Suppress("DEPRECATION")
    fun grantPermissionThoughAdb(context: Context) {
        val packageName = "app.tek4tv.ipradio"

        if (!isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Log.d("PermissionCheck", "Granting READ_EXTERNAL_STORAGE...")
            execSu("pm grant $packageName android.permission.READ_EXTERNAL_STORAGE")
        } else {
            Log.d("PermissionCheck", "READ_EXTERNAL_STORAGE already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("PermissionCheck", "Granting WRITE_EXTERNAL_STORAGE...")
            execSu("pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE")
        } else {
            Log.d("PermissionCheck", "WRITE_EXTERNAL_STORAGE already granted.")
        }

        if (!isUsageStatsPermissionGranted(context)) {
            Log.d("PermissionCheck", "Granting GET_USAGE_STATS via appops...")
            execSu("appops set $packageName GET_USAGE_STATS allow")
        } else {
            Log.d("PermissionCheck", "GET_USAGE_STATS already allowed.")
        }

        if (!isPermissionGranted(context, Manifest.permission.READ_PHONE_STATE)) {
            Log.d("PermissionCheck", "Granting READ_PHONE_STATE...")
            execSu("pm grant $packageName android.permission.READ_PHONE_STATE")
        } else {
            Log.d("PermissionCheck", "READ_PHONE_STATE already granted.")
        }
        if (!isPermissionGranted(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
            Log.d("PermissionCheck", "Granting ACCESS_NETWORK_STATE...")
            execSu("pm grant $packageName android.permission.ACCESS_NETWORK_STATE")
        } else {
            Log.d("PermissionCheck", "ACCESS_NETWORK_STATE already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.ACCESS_WIFI_STATE)) {
            Log.d("PermissionCheck", "Granting ACCESS_WIFI_STATE...")
            execSu("pm grant $packageName android.permission.ACCESS_WIFI_STATE")
        } else {
            Log.d("PermissionCheck", "READ_PHONE_STATE already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)) {
            Log.d("PermissionCheck", "Granting RECORD_AUDIO...")
            execSu("pm grant $packageName android.permission.RECORD_AUDIO")
        } else {
            Log.d("PermissionCheck", "RECORD_AUDIO already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.CHANGE_WIFI_STATE)) {
            Log.d("PermissionCheck", "Granting READ_PHONE_STATE...")
            execSu("pm grant $packageName android.permission.CHANGE_WIFI_STATE")
        } else {
            Log.d("PermissionCheck", "CHANGE_WIFI_STATE already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.d("PermissionCheck", "Granting ACCESS_COARSE_LOCATION...")
            execSu("pm grant $packageName android.permission.ACCESS_COARSE_LOCATION")
            //execSu("appops set $packageName android.permission.ACCESS_COARSE_LOCATION allow")
        } else {
            Log.d("PermissionCheck", "ACCESS_COARSE_LOCATION already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            Log.d("PermissionCheck", "Granting ACCESS_FINE_LOCATION...")
            execSu("pm grant $packageName android.permission.ACCESS_FINE_LOCATION")
            //execSu("appops set $packageName android.permission.ACCESS_FINE_LOCATION allow")
        } else {
            Log.d("PermissionCheck", "ACCESS_FINE_LOCATION already granted.")
        }

    }


}