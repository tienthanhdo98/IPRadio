package app.tek4tv.ipradio.model.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MediaInfoEntity::class, MediaInfoLiveHistoryEntity::class, MediaFileEntity::class],
    version = 1
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaInfoDao
    abstract fun mediaLiveInfoHistoryDao(): MediaInfoLiveHistoryDao
}