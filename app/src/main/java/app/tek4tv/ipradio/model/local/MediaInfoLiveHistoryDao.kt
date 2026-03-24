package app.tek4tv.ipradio.model.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaInfoLiveHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: MediaInfoLiveHistoryEntity)

    @Query("SELECT * FROM live_media_info")
    fun getAllHistory(): Flow<List<MediaInfoLiveHistoryEntity>>

    @Query("DELETE FROM live_media_info")
    suspend fun clearAllLiveHistory()

    @Query("DELETE FROM live_media_info WHERE mid = :mid")
    suspend fun deleteMediaInfoById(mid: Long)
}