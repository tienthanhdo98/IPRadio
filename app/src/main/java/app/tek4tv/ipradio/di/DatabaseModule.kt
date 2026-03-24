package app.tek4tv.ipradio.di

import android.content.Context
import androidx.room.Room
import app.tek4tv.ipradio.model.local.AppDatabase
import app.tek4tv.ipradio.model.local.MediaInfoDao
import app.tek4tv.ipradio.model.local.MediaInfoLiveHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "scheduler_db"
        ).build()
    }

    @Provides
    fun provideSchedulerDao(db: AppDatabase): MediaInfoDao {
        return db.mediaDao()
    }

    @Provides
    fun provideMediaInfoLiveHistoryDao(db: AppDatabase): MediaInfoLiveHistoryDao {
        return db.mediaLiveInfoHistoryDao()
    }
}

