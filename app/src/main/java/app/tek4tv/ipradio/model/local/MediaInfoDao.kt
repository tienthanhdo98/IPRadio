package app.tek4tv.ipradio.model.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaInfo(media: MediaInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<MediaFileEntity>)

    @Delete
    suspend fun deleteMediaInfo(media: MediaInfoEntity)

    @Query("DELETE FROM media_info WHERE mid = :mid")
    suspend fun deleteMediaInfoById(mid: Long)

    @Transaction // Bắt buộc có @Transaction khi lấy dữ liệu quan hệ
    @Query("SELECT * FROM media_info WHERE mid = :mid")
    suspend fun getMediaWithFiles(mid: Long): MediaWithFiles?

    @Transaction
    @Query("SELECT * FROM media_info")
    fun getAllMediaWithFiles(): Flow<List<MediaWithFiles>>

    @Query("DELETE FROM media_info")
    suspend fun deleteAllMedia()

    @Delete
    suspend fun deleteMedia(media: MediaFileEntity)
}