package app.tek4tv.ipradio.model.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tek4tv.ipradio.di.MoshiInst

@Database(entities = [MediaLogModel::class], version = 1, exportSchema = false)
@TypeConverters(MoshiInst::class)
abstract class MediaLogDatabase : RoomDatabase(){
    abstract fun mediaLogDao(): MediaLogDao

    companion object {
        @Volatile
        private var INSTANCE: MediaLogDatabase? = null

        fun getDatabase(context: Context): MediaLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediaLogDatabase::class.java,
                    "media_log_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}