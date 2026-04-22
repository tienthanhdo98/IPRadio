package app.tek4tv.ipradio.viewmodels


import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.webserver.LocalWebConsole
import app.tek4tv.ipradio.model.McuLogData
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.repo.MediaInfoLiveHistoryRepository
import app.tek4tv.ipradio.repo.MediaInfoRepository
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.repo.MgwLogRepository
import app.tek4tv.ipradio.utils.execSu
import app.tek4tv.ipradio.utils.storage.getFileNameFromUrl
import app.tek4tv.ipradio.utils.toModel
import app.tek4tv.ipradio.utils.unixSecondsToCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Calendar
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    val mediaRepo: MediaRepo,
    val mediaInfoRepo: MediaInfoRepository,
    val mediaInfoLiveHistoryRepo: MediaInfoLiveHistoryRepository,
    val logRepo: MgwLogRepository,
) : ViewModel() {

    //MGW
    private val _schedulerUpdated = MutableSharedFlow<Unit>(replay = 1)
    val schedulerUpdated = _schedulerUpdated.asSharedFlow()

    private var flushJob: Job? = null
    private val flushMutex = Mutex()


    fun updateSingleMedia(mediaInfo: MediaInfo?) {
        if (mediaInfo == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("MediaInfo", "Nhận bản tin lẻ: ${mediaInfo.mid}")

                // 1. Lưu hoặc cập nhật bản tin này vào Database
                // (Nếu đã có trong DB thì nó sẽ ghi đè bản mới nhất)
                mediaInfoRepo.upsertMediaInfo(mediaInfo)

                // 2. Thêm hoặc cập nhật bản tin này vào RAM (Map scheduledList)
                // Hàm updateScheduler của bạn dùng `scheduledList[period] = mediaInfo`
                // nên nó sẽ CỘNG DỒN một cách an toàn, b1 vẫn giữ nguyên.
                mediaRepo.updateScheduler(mediaInfo)

                // 3. Ra hiệu cho Player kiểm tra lại
                // Vì b1 vẫn còn trong scheduledList, Player sẽ thấy khung giờ đang phát
                // vẫn hợp lệ -> Không làm gián đoạn nhạc!
                _schedulerUpdated.emit(Unit)

            } catch (e: Exception) {
                Log.e("MediaInfo", "Lỗi khi xử lý bản tin lẻ: ${e.message}")
            }
        }
    }

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
            }
            syncLocalWithServer(listMediaInfo.map { it.mid ?: 0L })

        }
    }


    fun syncLocalWithServer(serverMids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localMedias = mediaInfoRepo.allMedia.first()

                // 1. Xóa trong DB những gì server đã bỏ
                localMedias.forEach { item ->
                    val mid = item.toModel().mid ?: 0L
                    if (!serverMids.contains(mid)) {
                        Log.d("MediaInfo", "Media sync local delete: $mid")
                        mediaInfoRepo.deleteMediaInfoById(mid)
                        mediaRepo.syncListMediaInfoId(mid)
                    }
                }

                // 2. Lấy lại list "sạch" từ DB
                val remainingMedia = mediaInfoRepo.allMedia.first().map { it.toModel() }

                // 3. Cập nhật RAM bằng cách ghi đè nguyên khối (Không lo dừng nhạc)
                mediaRepo.replaceAllSchedules(remainingMedia)

                _schedulerUpdated.emit(Unit)
            } catch (e: Exception) {
                Log.e("SYNC_ERROR", "${e.message}")
            }
        }
    }

    fun updateWebsocketListLocal() {
        viewModelScope.launch {
            val mediaInfoWithFile = mediaInfoRepo.allMedia.first()
            LocalWebConsole.updateMediaList(
                mediaInfoWithFile.toMutableList().map { it.toModel() })
        }
    }

    fun getPlayerContentLocal() {
        viewModelScope.launch {
            val mediaInfoWithFile = mediaInfoRepo.allMedia.first()
            LocalWebConsole.updateMediaList(
                mediaInfoWithFile.toMutableList().map { it.toModel() })
            mediaInfoWithFile.forEach { item ->
                val mediaInfoModel = item.toModel()
                Log.d("MediaInfo", "getPlayerContentLocal: $mediaInfoModel")
                mediaRepo.updateScheduler(mediaInfoModel)
            }
            _schedulerUpdated.emit(Unit)
        }
    }


    fun saveSchedulerToLocal(listMediaInfo: List<MediaInfo>?) {
        if (listMediaInfo == null) return
        viewModelScope.launch {
            listMediaInfo.forEach { mediaInfo ->
                //Chỉ lưu ko phát
                mediaInfoRepo.upsertMediaInfo(mediaInfo)
            }
        }
    }

    fun getPlayerContent() {
        viewModelScope.launch {
            _schedulerUpdated.emit(Unit)
        }
    }

    fun deleteMediaInfInRepo(mediaInfo: MediaInfo?) {
        if (mediaInfo == null) return

        val keyToRemove =
            mediaRepo.scheduledList.entries.find { it.value.mid == mediaInfo.mid }?.key

        if (keyToRemove != null) {
            mediaRepo.scheduledList.remove(keyToRemove)
            Log.e(
                "MediaInfo", "Xóa repo: ${mediaInfo.mid}"
            )
        }
    }

    fun deleteLocalScheduler(mediaInfo: MediaInfo?) {
        // 1. Kiểm tra ID hợp lệ trước khi launch
        val mid = mediaInfo?.mid ?: return

        viewModelScope.launch {
            try {
                mediaInfoRepo.deleteMediaInfoById(mid)
                Log.e(
                    "MediaInfo", "Xóa local: ${mediaInfo.mid}"
                )
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

    fun getMediaInfoIdList(): Set<Long> {
        return mediaRepo.listMediaInfoId
    }

    fun getScheduleList(): List<MediaInfo> {
        return mediaRepo.scheduledList.values.toList()
    }


    suspend fun getOnlyNewMedia(listMediaInfo: List<MediaInfo>?): List<MediaInfo> {
        if (listMediaInfo.isNullOrEmpty()) return emptyList()

        val localHistory = mediaInfoLiveHistoryRepo.allMedia.first()
        localHistory.forEach {
            Log.d("MediaInfo", "getOnlyNewMedia: All item history ${it.mid} - ${it.summary}")
        }
        val localKeys = localHistory.map { "${it.mid}_${it.version}" }.toSet()

        return listMediaInfo.filter { serverItem ->
            val serverKey = "${serverItem.mid}_${serverItem.version}"
            !localKeys.contains(serverKey)
        }
    }

    fun insertMediaInfoLiveHistory(mediaInfo: MediaInfo) {
        viewModelScope.launch {
            mediaInfoLiveHistoryRepo.upsertMediaLiveInfo(mediaInfo)
        }
    }

    fun clearLocalMediaInfoExpired(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val listLiveHistory = mediaInfoLiveHistoryRepo.allMedia.first()
                val listLocalMediaInfo = mediaInfoRepo.allMedia.first()
                val now = Calendar.getInstance()

                listLiveHistory.forEach {
                    if (it.expired != null) {
                        val expiredTime = unixSecondsToCalendar(it.expired)
                        if (!now.before(expiredTime)) {
                            mediaInfoLiveHistoryRepo.deleteMediaInfoById(it.mid)
                        }
                    }
                }
                listLocalMediaInfo.forEach { item ->
                    val mediaInfo = item.toModel()
                    if (mediaInfo.expired != null && mediaInfo.mid != null) {
                        val expiredTime = unixSecondsToCalendar(mediaInfo.expired!!)
                        if (!now.before(expiredTime)) {
                            mediaInfoRepo.deleteMediaInfoById(mediaInfo.mid)
                            val listFile = mediaInfo.files
                            listFile.forEach { file ->
                                val fileName = getFileNameFromUrl(file.url ?: "")
                                if (fileName.isBlank()) return@forEach
                                val fileLocal = File(context.filesDir.path, fileName)
                                if (fileLocal.exists()) {
                                    fileLocal.delete()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaInfo", "clearLocalMediaInfoExpired error: ${e.message}", e)
            }
        }
    }

    //Log

    fun addLog(type: Int, detail: String, hubManager: HubManager) {
        logRepo.addLog(
            McuLogData(
                id = System.currentTimeMillis(),
                time = System.currentTimeMillis() / 1000.0,
                type = type,
                detail = detail
            )
        )

        if (logRepo.size() >= 10) {
            flushLogsNow(hubManager)
        } else {
            scheduleFlushLogs(hubManager)
        }
    }

    fun scheduleFlushLogs(hubManager: HubManager, delayMs: Long = 60000L) {
        flushJob?.cancel()
        flushJob = viewModelScope.launch {
            delay(delayMs)
            flushLogsNow(hubManager)
        }
    }

    fun flushLogsNow(hubManager: HubManager) {
        viewModelScope.launch {
            flushMutex.withLock {
                while (true) {
                    val batch = logRepo.getTopPendingLogs(10)
                    if (batch.isEmpty()) break

                    val success = try {
                        hubManager.sendLog(batch)
                        true
                    } catch (e: Exception) {
                        false
                    }

                    if (success) {
                        logRepo.removeLogs(batch)
                    } else {
                        break
                    }
                }
            }
        }
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