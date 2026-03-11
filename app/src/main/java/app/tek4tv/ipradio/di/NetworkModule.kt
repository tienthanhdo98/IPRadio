package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.network.ApiService
import app.tek4tv.ipradio.utils.network.NetworkUtils
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val BASE_URL = "https://aiot.mobifone.vn"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = MoshiInst.moshi


    @Provides
    @Singleton
    fun provideRetrofit(moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

}