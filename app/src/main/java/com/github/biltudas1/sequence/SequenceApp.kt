package com.github.biltudas1.sequence

import android.app.Application
import com.github.biltudas1.sequence.util.AppLogger
import timber.log.Timber

class SequenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(AppLogger.InMemoryTree())
        } else {
            // You might want to plant a production tree here (e.g., Crashlytics)
            Timber.plant(AppLogger.InMemoryTree()) // Still plant in-memory tree for viewing logs in app
        }
    }
}
