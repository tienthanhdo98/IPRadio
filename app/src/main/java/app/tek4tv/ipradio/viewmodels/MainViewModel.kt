package app.tek4tv.ipradio.viewmodels

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.execSu
import app.tek4tv.ipradio.utils.getDestinationApk
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainViewModel @ViewModelInject constructor(
    internal val mediaRepo: MediaRepo
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    private var getContentJob: Job? = null

    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    //
    fun updateMonthlyScenario(url: String) {
        viewModelScope.launch {
            mediaRepo.getMonthlyScenario(url)
            getPlayerContent()
        }
    }

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

    //

    fun rescheduleMonthlyPlaylist() {
        val jsonMonthlyListData = ConfigUtil.getString(
            MyApp.instance!!.baseContext,
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

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun grantPermissionThoughAdb(context: Context) {
        val packageName = "app.tek4tv.digital"

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

        if (!isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)) {
            Log.d("PermissionCheck", "Granting RECORD_AUDIO...")
            execSu("pm grant $packageName android.permission.RECORD_AUDIO")
        } else {
            Log.d("PermissionCheck", "RECORD_AUDIO already granted.")
        }
    }

    fun checkDeleteFileUpdate() {
        val destination = getDestinationApk()
        val file = File(destination)
        if (file.exists()) {
            file.delete()
            Log.d("deletefile", "checkDeleteFileUpdate: delete success")
        } else {
            Log.d("deletefile", "checkDeleteFileUpdate: file not exists")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun capture(context: Context, vlcVideoLayout: VLCVideoLayout) {
        try {
            val surfaceView = (vlcVideoLayout.getChildAt(
                0
            ) as FrameLayout).children.filter { it is SurfaceView }
                .first() as SurfaceView

            val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)

            PixelCopy.request(surfaceView, bitmap, {
                saveBitmap(bitmap, context)
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveBitmap(bitmap: Bitmap, context: Context) {
        // Create directory for saving
        val path: File =
            File(Environment.getExternalStorageDirectory().toString() + "/Screenshots/")
        if (!path.exists()) {
            path.mkdirs()
        }

        // Create the file for saving
        val imageFile = File(path, "screenshot_" + System.currentTimeMillis() + ".png")
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // Compress to PNG
                Toast.makeText(context, "Screenshot saved: " + imageFile.path, Toast.LENGTH_SHORT)
                    .show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearDatastore(appStorageManager: AppStorageManager) {
        //Luon de trong 500mb
        //Xoa file khong co trong lich khong du se dung download
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