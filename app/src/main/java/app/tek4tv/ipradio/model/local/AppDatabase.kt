package app.tek4tv.ipradio.model.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tek4tv.ipradio.model.local.log.MgwLogDao
import app.tek4tv.ipradio.model.local.log.MgwLogEntity

@Database(
    entities = [MediaInfoEntity::class, MediaInfoLiveHistoryEntity::class, MediaFileEntity::class, MgwLogEntity::class],
    version = 2
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaInfoDao
    abstract fun logDao(): MgwLogDao
    abstract fun mediaLiveInfoHistoryDao(): MediaInfoLiveHistoryDao
}