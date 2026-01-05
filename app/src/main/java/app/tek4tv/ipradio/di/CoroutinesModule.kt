package app.tek4tv.ipradio.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

// Qualifier để phân biệt với các scope khác
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(handler: CoroutineExceptionHandler): CoroutineScope =
    // SupervisorJob: 1 child fail KHÔNG cancel các child khác
        // IO: nền / network / disk
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    @Provides
    @Singleton
    fun provideAppScopeExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, e ->
            // Log mọi exception “uncaught” trong AppScope
            Log.e("AppScope", "Uncaught in AppScope", e)
        }
}