package com.scanrift.android

import android.app.Application
import timber.log.Timber

class ScanRiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
