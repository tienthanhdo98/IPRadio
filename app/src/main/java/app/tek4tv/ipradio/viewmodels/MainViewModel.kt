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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<List<MediaItem>>()

    private var getContentJob: Job? = null

    private var checkPlaylistJob: Job? = null

    private var retryJob: Job? = null


    fun updateMonthlyScenario(url: String) {
        mediaRepo.clearMonthlyLocalData()
        viewModelScope.launch {
            mediaRepo.safeUpdateMonthlyScenario(url)
            getPlayerContent(isFromUpdateMonthly = true)
        }
    }

    fun getPlayerContent(isFromUpdateMonthly: Boolean = false) {
        viewModelScope.launch {

            getContentJob?.cancel()
            checkPlaylistJob?.cancel()

            getContentJob = launch {
                val mediaList = mediaRepo.getScenario(isFromUpdateMonthly)

                content.value = mediaList

                if (mediaList.isNotEmpty()) {
                    Log.d("MEDIA_REPO", "Successfully loaded schedule, canceling retry job if any.")
                    retryJob?.cancel()
                    retryJob = null
                } else {
                    if (mediaRepo.lastUpdateFailed) {
                        Log.w(
                            "MEDIA_REPO",
                            "Schedule is empty due to update failure, starting or maintaining retry job."
                        )
                        startScheduleRetryLoop()
                    } else {
                        Log.d(
                            "MEDIA_REPO",
                            "Schedule is empty but fetch was successful (no schedule for today). Canceling retry job."
                        )
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
                // Tăng dần thời gian chờ
                val delayMinutes = when {
                    attempt < 10 -> 3L   // 2 phút/lần
                    attempt < 15 -> 5L //  5 phút/lần
                    attempt < 20 -> 10L //  10 phút/lần
                    else -> 20L         // 20 phút/lần
                }

                val delayMillis = delayMinutes * 60 * 1000L
                Log.d(
                    "MEDIA_REPO",
                    "Retry in ${delayMinutes}min (attempt ${attempt + 1}/$maxAttempts)"
                )
                delay(delayMillis)
                val mediaList = mediaRepo.getScenario()
                withContext(Dispatchers.Main) {
                    content.value = mediaList

                    if (mediaList.isNotEmpty()) {
                        Log.d("MEDIA_REPO", "Successfully loaded schedule, breaking retry loop.")
                        retryJob?.cancel() // Có dữ liệu thì tự hủy vòng lặp
                        attempt = 0
                    }
                }
                attempt++
            }
        }
    }


    fun checkPlaylist(appStorageManager: AppStorageManager) {
        checkPlaylistJob?.cancel()
        checkPlaylistJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60 * 60_000L) // 60 phút mới kiểm tra lại
                clearDatastore(appStorageManager)
                Log.d("MEDIA_REPO", "Checking media...")
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
            Log.d("PermissionCheck", "ACCESS_WIFI_STATE already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)) {
            Log.d("PermissionCheck", "Granting RECORD_AUDIO...")
            execSu("pm grant $packageName android.permission.RECORD_AUDIO")
        } else {
            Log.d("PermissionCheck", "RECORD_AUDIO already granted.")
        }

        if (!isPermissionGranted(context, Manifest.permission.CHANGE_WIFI_STATE)) {
            Log.d("PermissionCheck", "Granting CHANGE_WIFI_STATE...")
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