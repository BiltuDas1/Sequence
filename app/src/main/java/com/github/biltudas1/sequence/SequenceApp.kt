package com.github.biltudas1.sequence

import android.app.Application
import androidx.work.Configuration
import com.github.biltudas1.sequence.util.AppLogger
import com.google.firebase.FirebaseApp
import timber.log.Timber

class SequenceApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)

        // Initialize Firebase explicitly to prevent IllegalStateException
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Firebase")
        }
        
        // Ensure ML Kit is initialized
        try {
            com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(this)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ML Kit")
        }

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
