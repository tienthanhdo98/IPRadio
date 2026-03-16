package app.tek4tv.ipradio.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaInfoRepository
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.media.MediaWatchDog
import app.tek4tv.ipradio.utils.toEntity
import app.tek4tv.ipradio.utils.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val mediaRepo: MediaRepo,
    val mediaInfoRepo: MediaInfoRepository,
) : ViewModel() {
    val content = MutableLiveData<Pair<List<MediaItem>, List<String>>>()


    private var mediaWatchdog = MediaWatchDog(viewModelScope, mediaRepo)

    //MGW
    private val _schedulerUpdated = MutableSharedFlow<Unit>(replay = 1)
    val schedulerUpdated = _schedulerUpdated.asSharedFlow()


    fun updateScheduler(mediaInfo: MediaInfo?) {
        if (mediaInfo == null) return
        viewModelScope.launch {
            mediaInfoRepo.upsertMediaInfo(mediaInfo)
            mediaRepo.updateScheduler(mediaInfo)
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
                        Log.d("SYNC_DEBUG", "Đã xóa MediaInfo ID: $mid")
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
                    Log.d("SYNC_DEBUG", "Đồng bộ hoàn tất, đã cập nhật lại hẹn giờ")
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

    fun getMediaInfoIdList(): List<Long> {
        return mediaRepo.listMediaInfoId
    }

    //
    fun checkPlaylist() {
        mediaWatchdog.checkPlaylist()
    }

    fun downloadMedias() {
        mediaRepo.startDownloadMedia()
    }


}