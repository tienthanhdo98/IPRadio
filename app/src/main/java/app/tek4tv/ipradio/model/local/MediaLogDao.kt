package app.tek4tv.ipradio.model.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MediaLogDao {
    @Insert
    suspend fun insertLog(videoLog: MediaLogModel)

    @Query("SELECT * FROM media_logs")
    suspend fun getUnsentMediaLogs(): List<MediaLogModel>

    @Update
    suspend fun updateLog(videoLog: MediaLogModel)

    @Query("DELETE FROM media_logs")
    suspend fun deleteSentLogs()

    @Query("DELETE FROM media_logs WHERE id = :id")
    suspend fun deleteMediaItemById(id: Int)
}