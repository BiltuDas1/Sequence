package com.github.biltudas1.sequence.worker

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.*
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.VersionService
import com.github.biltudas1.sequence.util.NotificationHelper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager(applicationContext)
        val versionService = VersionService(OkHttpClient(), dataStoreManager)

        return try {
            val latestRelease = versionService.getLatestRelease()
            if (latestRelease != null) {
                val currentVersion = getCurrentVersionName(applicationContext)
                if (isNewerVersion(latestRelease.tag_name, currentVersion)) {
                    NotificationHelper.showUpdateNotification(
                        applicationContext,
                        latestRelease.tag_name,
                        latestRelease.html_url
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Error checking for updates: ${e.message}")
            Result.retry()
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        // Very basic comparison: if they are different, assume there's an update if latest is not current
        return latest.removePrefix("v") != current.removePrefix("v")
    }

    companion object {
        private const val WORK_NAME = "UpdateCheckWork"
        private const val ONE_TIME_WORK_NAME = "UpdateCheckWorkOneTime"

        fun checkNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun schedule(context: Context, interval: String) {
            val workManager = WorkManager.getInstance(context)
            if (interval == "Never") {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val repeatInterval = when (interval) {
                "Daily" -> 1L
                "Weekly" -> 7L
                else -> 1L
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(repeatInterval, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
