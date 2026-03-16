package app.tek4tv.ipradio.viewmodels


import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.MediaItem
import app.tek4tv.ipradio.repo.MediaInfoRepository
import app.tek4tv.ipradio.repo.MediaRepo
import app.tek4tv.ipradio.utils.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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


}