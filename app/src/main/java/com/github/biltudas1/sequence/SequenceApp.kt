package com.github.biltudas1.sequence

import android.app.Application
import com.github.biltudas1.sequence.util.AppLogger
import timber.log.Timber

class SequenceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(AppLogger.InMemoryTree())
        } else {
            // In production, only plant InMemoryTree (which also handles file logging)
            // but without DebugTree, so no Logcat logs.
            Timber.plant(AppLogger.InMemoryTree())
        }
    }
}
