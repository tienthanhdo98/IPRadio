package app.tek4tv.ipradio.di

import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.network.PlaylistService
import app.tek4tv.ipradio.utils.network.NetworkUtils
import app.tek4tv.ipradio.utils.network.OkHttpClientUtil.getUnsafeOkHttpClient
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = MoshiInst.moshi

    // Configure OkHttpClient with timeout
    var okHttpClient: OkHttpClient =
        OkHttpClient.Builder().connectTimeout(40, TimeUnit.SECONDS) // Increase connection timeout
            .readTimeout(40, TimeUnit.SECONDS) // Increase read timeout
            .writeTimeout(40, TimeUnit.SECONDS) // Increase write timeout
            .retryOnConnectionFailure(true) // Retry failed requests
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(moshi: Moshi): Retrofit = Retrofit.Builder().baseUrl(NetworkUtils.BASE_URL)
        .client(okHttpClient)
        //.client(getUnsafeOkHttpClient())
        .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
        .addCallAdapterFactory(CoroutineCallAdapterFactory()).build()

    @Provides
    @Singleton
    fun providePlaylistService(retrofit: Retrofit): PlaylistService =
        retrofit.create(PlaylistService::class.java)

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService =
        retrofit.create(AuthService::class.java)

    @Provides
    @Singleton
    fun provideLogService(retrofit: Retrofit): LogService = retrofit.create(LogService::class.java)

    fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            // TrustManager "tin tất cả"
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(
                    sslContext.socketFactory,
                    trustAllCerts[0] as X509TrustManager
                )
                .hostnameVerifier { _, _ -> true } // bỏ qua kiểm tra tên miền (tuỳ chọn)
                .build()

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}