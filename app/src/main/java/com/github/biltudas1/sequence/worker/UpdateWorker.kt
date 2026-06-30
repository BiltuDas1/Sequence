package com.github.biltudas1.sequence.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.*
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.VersionService
import com.github.biltudas1.sequence.util.NotificationHelper
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class UpdateWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager.getInstance(applicationContext)
        val versionService = VersionService(OkHttpClient(), dataStoreManager)

        return try {
            val currentVersion = getCurrentVersionName(applicationContext)
            Timber.d("Checking for updates. Current version: $currentVersion")
            val latestRelease = versionService.getLatestRelease(currentVersion = currentVersion)
            
            if (latestRelease != null) {
                Timber.d("Latest release: ${latestRelease.tag_name}")
                if (isNewerVersion(latestRelease.tag_name, currentVersion)) {
                    val downloadInfo = dataStoreManager.downloadInfoFlow.first()
                    val filePath = downloadInfo.filePath
                    
                    if (downloadInfo.status == "COMPLETED" && 
                        downloadInfo.versionTag == latestRelease.tag_name && 
                        filePath != null && java.io.File(filePath).exists()) {
                        
                        Timber.i("New version already downloaded: ${latestRelease.tag_name}. Showing install notification.")
                        NotificationHelper.showInstallNotification(
                            applicationContext,
                            latestRelease.tag_name,
                            downloadInfo.filePath
                        )
                    } else {
                        Timber.i("New version found: ${latestRelease.tag_name}. Showing update notification.")
                        NotificationHelper.showUpdateNotification(
                            applicationContext,
                            latestRelease.tag_name,
                            latestRelease.html_url
                        )
                    }
                } else {
                    Timber.d("Already on the latest version.")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
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
