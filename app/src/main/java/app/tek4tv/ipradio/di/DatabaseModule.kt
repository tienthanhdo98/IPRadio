package app.tek4tv.ipradio.di

import android.content.Context
import androidx.room.Room
import app.tek4tv.ipradio.model.local.MediaLogDao
import app.tek4tv.ipradio.model.local.MediaLogDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): MediaLogDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            MediaLogDatabase::class.java,
            "media_logs"
        ).build()
    }

    @Provides
    fun provideMediaLogDao(database: MediaLogDatabase): MediaLogDao {
        return database.mediaLogDao()
    }
}