package app.tek4tv.ipradio.viewmodels


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaInfoRepository
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.execSu
import app.tek4tv.ipradio.utils.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.AppOpsManager
import android.os.Process

@HiltViewModel
class MainViewModel @Inject constructor(
    val mediaRepo: MediaRepo,
    val mediaInfoRepo: MediaInfoRepository,
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()

    //MGW
    private val _schedulerUpdated = MutableSharedFlow<Unit>(replay = 1)
    val schedulerUpdated = _schedulerUpdated.asSharedFlow()


    fun updateScheduler(listMediaInfo: List<MediaInfo>?) {
        if (listMediaInfo == null) return
        viewModelScope.launch {
            listMediaInfo.forEach { mediaInfo ->
                //Tạo
                if (mediaInfo.action == 1 || mediaInfo.action == 8) {
                    mediaInfoRepo.upsertMediaInfo(mediaInfo)
                    mediaRepo.updateScheduler(mediaInfo)
                    Log.d("MediaInfo", "Media sync: $mediaInfo")
                }
                if (mediaInfo.action == 4) {
                    deleteLocalScheduler(mediaInfo)
                }
            }
            syncLocalWithServer(listMediaInfo.map { it.mid ?: 0L })

        }
    }

    fun getPlayerContentLocal() {
        viewModelScope.launch {
            val mediaInfoWithFile = mediaInfoRepo.allMedia.first()
            mediaInfoWithFile.forEach { item ->
                val mediaInfoModel = item.toModel()
                Log.d("MediaInfo", "getPlayerContentLocal: $mediaInfoModel")
                mediaRepo.updateScheduler(mediaInfoModel)
            }
            _schedulerUpdated.emit(Unit)
        }
    }


    fun syncLocalWithServer(serverMids: List<Long>) {
        viewModelScope.launch {
            try {
                // 1. Lấy dữ liệu hiện tại
                val mediaInfoWithFile = mediaInfoRepo.allMedia.first()
                var hasChanged = false

                // 2. Xóa các bản ghi không có trong danh sách server
                mediaInfoWithFile.forEach { item ->
                    val mid = item.toModel().mid ?: 0L
                    if (!serverMids.contains(mid)) {
                        mediaInfoRepo.deleteMediaInfoById(mid)
                        hasChanged = true
                        Log.d("MediaInfo", "Media sync: $mid")
                    }
                }

                // 3. Nếu có thay đổi, tiến hành làm mới lịch hẹn giờ
                if (hasChanged) {
                    // Làm sạch bộ nhớ đệm trong Repo
                    mediaRepo.clearAllScheduled()

                    // Nạp lại dữ liệu còn lại từ DB vào Repo
                    val remainingMedia = mediaInfoRepo.allMedia.first()
                    remainingMedia.forEach { item ->
                        mediaRepo.updateScheduler(item.toModel())
                    }

                    // Phát tín hiệu để dừng/đặt lại Alarm/Player
                    _schedulerUpdated.emit(Unit)
                    Log.d("MediaInfo", "Đồng bộ hoàn tất, đã cập nhật lại hẹn giờ")
                } else {
                    _schedulerUpdated.emit(Unit)
                    Log.d("MediaInfo", "Đồng bộ hoàn tất, Local = sync")
                }
            } catch (e: Exception) {
                Log.e("SYNC_DEBUG", "Lỗi đồng bộ: ${e.message}")
            }
        }
    }


    fun getPlayerContent() {
        viewModelScope.launch {
            _schedulerUpdated.emit(Unit)
        }
    }

    fun deleteLocalScheduler(mediaInfo: MediaInfo?) {
        // 1. Kiểm tra ID hợp lệ trước khi launch
        val mid = mediaInfo?.mid ?: return

        viewModelScope.launch {
            try {
                mediaInfoRepo.deleteMediaInfoById(mid)
                // 2. Sau khi xóa DB thành công, phải hủy luôn Alarm/Lịch phát liên quan
                // cancelAlarm(mid)
            } catch (e: Exception) {
                Log.e("DB_ERROR", "Xóa thất bại: ${e.message}")
                // Hiển thị thông báo lỗi cho người dùng nếu cần
            }
        }
    }

    fun deleteAllLocalScheduler() {
        viewModelScope.launch {
            try {
                mediaInfoRepo.clearAllLocalData()
                // 2. Sau khi xóa DB thành công, phải hủy luôn Alarm/Lịch phát liên quan
                // cancelAlarm(mid)
            } catch (e: Exception) {
                Log.e("DB_ERROR", "Xóa thất bại: ${e.message}")
                // Hiển thị thông báo lỗi cho người dùng nếu cần
            }
        }
    }


    fun getMediaInfoIdList(): List<Long> {
        return mediaRepo.listMediaInfoId
    }


    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

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