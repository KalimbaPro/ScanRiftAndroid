package com.scanrift.android.di

import android.content.Context
import com.scanrift.android.BuildConfig
import com.scanrift.android.core.Constants
import com.scanrift.android.data.remote.api.RiftboundApi
import com.scanrift.android.data.remote.datasource.BundledCardDataSource
import com.scanrift.android.data.remote.datasource.RemoteRiftboundDataSource
import com.scanrift.android.data.remote.datasource.RiftboundDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Remote

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class Bundled

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * The old build handed Retrofit no client at all, so every call ran on OkHttp's
     * defaults with **no timeouts** — a captive portal could hang the initial sync
     * screen indefinitely. A full catalogue sync is ~15 pages, so the read timeout is
     * per-request rather than per-sync.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Constants.Api.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.Api.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.Api.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(Constants.Api.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(Cache(context.cacheDir.resolve("http"), Constants.Api.HTTP_CACHE_BYTES))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, @ApiJson json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(Constants.Api.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideRiftboundApi(retrofit: Retrofit): RiftboundApi =
        retrofit.create(RiftboundApi::class.java)

    @Provides @Singleton @Remote
    fun provideRemoteDataSource(source: RemoteRiftboundDataSource): RiftboundDataSource = source

    @Provides @Singleton @Bundled
    fun provideBundledDataSource(source: BundledCardDataSource): RiftboundDataSource = source
}
