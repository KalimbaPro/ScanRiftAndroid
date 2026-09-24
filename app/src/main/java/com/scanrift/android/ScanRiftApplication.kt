package com.scanrift.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.scanrift.android.core.Constants
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber

@HiltAndroidApp
class ScanRiftApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var okHttpClient: Lazy<OkHttpClient>

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get().newBuilder().cache(null).build() }))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(Constants.ImageCache.DIRECTORY).toOkioPath())
                    .maxSizeBytes(Constants.ImageCache.DISK_CACHE_BYTES)
                    .build()
            }
            .build()
}
