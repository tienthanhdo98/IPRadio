package app.tek4tv.ipradio.repo

import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.local.MediaInfoDao
import app.tek4tv.ipradio.model.local.MediaInfoEntity
import app.tek4tv.ipradio.model.local.MediaInfoLiveHistoryDao
import app.tek4tv.ipradio.model.local.MediaInfoLiveHistoryEntity
import app.tek4tv.ipradio.model.local.MediaWithFiles
import app.tek4tv.ipradio.utils.toEntity
import app.tek4tv.ipradio.utils.toLiveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaInfoLiveHistoryRepository @Inject constructor(
    private val mediaInfoLiveHistoryDao: MediaInfoLiveHistoryDao,
) {
    // Lấy toàn bộ dữ liệu dưới dạng Flow để UI tự động cập nhật
    val allMedia: Flow<List<MediaInfoLiveHistoryEntity>> = mediaInfoLiveHistoryDao.getAllHistory()

    /**
     * Lưu hoặc cập nhật thông tin Media cùng với các Files liên quan
     */
    suspend fun upsertMediaLiveInfo(mediaInfo: MediaInfo) {
        val mid = mediaInfo.mid ?: return

        // 1. Chuyển đổi sang Entity
        val mediaEntity = mediaInfo.toLiveEntity()

        // 2. Thực hiện lưu vào DB (Nên chạy trên Dispatchers.IO)
        withContext(Dispatchers.IO) {
            // Room sẽ thực thi các lệnh này
            mediaInfoLiveHistoryDao.insertHistory(mediaEntity)
        }
    }

    suspend fun deleteMediaInfoById(mid: Long) {
        withContext(Dispatchers.IO) {
            mediaInfoLiveHistoryDao.deleteMediaInfoById(mid)
        }
    }

    suspend fun clearAllLocalLiveHistory() {
        withContext(Dispatchers.IO) {
            mediaInfoLiveHistoryDao.clearAllLiveHistory()
        }
    }

}