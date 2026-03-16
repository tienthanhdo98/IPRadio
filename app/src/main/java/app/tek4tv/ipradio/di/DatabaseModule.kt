package app.tek4tv.ipradio.di

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.tek4tv.ipradio.model.local.AppDatabase
import app.tek4tv.ipradio.model.local.MediaDao
import app.tek4tv.ipradio.model.local.MediaFileEntity
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
    fun provideSchedulerDao(db: AppDatabase): MediaDao {
        return db.mediaDao()
    }
}

