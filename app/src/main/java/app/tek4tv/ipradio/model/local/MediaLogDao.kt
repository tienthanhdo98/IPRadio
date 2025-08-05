package app.tek4tv.ipradio.model.local

import android.provider.MediaStore.Audio.Media
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.tek4tv.ipradio.model.MediaItem

@Dao
interface MediaLogDao {
    @Insert
    suspend fun insertLog(videoLog: MediaItem)

    @Query("SELECT * FROM media_logs")
    suspend fun getUnsentMediaLogs(): List<MediaItem>

    @Update
    suspend fun updateLog(videoLog: MediaItem)

    @Query("DELETE FROM media_logs")
    suspend fun deleteSentLogs()

    @Query("DELETE FROM media_logs WHERE id = :id")
    suspend fun deleteMediaItemById(id: Int)
}