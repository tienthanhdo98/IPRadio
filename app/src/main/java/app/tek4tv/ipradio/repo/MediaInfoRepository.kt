package app.tek4tv.ipradio.repo

import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.local.MediaInfoDao
import app.tek4tv.ipradio.model.local.MediaInfoEntity
import app.tek4tv.ipradio.model.local.MediaWithFiles
import app.tek4tv.ipradio.utils.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaInfoRepository @Inject constructor(
    private val mediaDao: MediaInfoDao,
) {
    // Lấy toàn bộ dữ liệu dưới dạng Flow để UI tự động cập nhật
    val allMedia: Flow<List<MediaWithFiles>> = mediaDao.getAllMediaWithFiles()

    /**
     * Lưu hoặc cập nhật thông tin Media cùng với các Files liên quan
     */
    suspend fun upsertMediaInfo(mediaInfo: MediaInfo) {
        val mid = mediaInfo.mid ?: return

        // 1. Chuyển đổi sang Entity
        val mediaEntity = mediaInfo.toEntity()
        val fileEntities = mediaInfo.files.map { it.toEntity(mid) }

        // 2. Thực hiện lưu vào DB (Nên chạy trên Dispatchers.IO)
        withContext(Dispatchers.IO) {
            // Room sẽ thực thi các lệnh này
            mediaDao.insertMediaInfo(mediaEntity)
            mediaDao.insertFiles(fileEntities)
        }
    }

    suspend fun clearAllLocalData() {
        withContext(Dispatchers.IO) {
            mediaDao.deleteAllMedia()
        }
    }

    suspend fun getMediaById(mid: Long): MediaWithFiles? {
        return withContext(Dispatchers.IO) {
            mediaDao.getMediaWithFiles(mid)
        }
    }

    suspend fun deleteMediaInfoById(mid: Long) {
        withContext(Dispatchers.IO) {
            mediaDao.deleteMediaInfoById(mid)
        }
    }

    suspend fun deleteMediaInfo(mediaInfo: MediaInfoEntity) {
        withContext(Dispatchers.IO) {
            mediaDao.deleteMediaInfo(mediaInfo)
        }
    }
}