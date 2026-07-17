package com.github.biltudas1.sequence.worker

import android.content.Context
import androidx.work.*
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

class FcmTokenWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager.getInstance(applicationContext)
        val authService = AuthService(OkHttpClient(), dataStoreManager)

        val serverConfig = dataStoreManager.serverConfigFlow.firstOrNull()
        val accessToken = dataStoreManager.accessTokenFlow.firstOrNull()

        if (serverConfig == null || !serverConfig.isValid() || accessToken == null || accessToken == "UNDEFINED") {
            Timber.w("FcmTokenWorker: Missing configuration or token. Cannot sync.")
            return Result.failure()
        }

        return try {
            Timber.d("FcmTokenWorker: Fetching FCM token from Firebase...")
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            
            Timber.d("FcmTokenWorker: Syncing token to server: ${serverConfig.cleanEndpoint}")
            val result = authService.updateFcmToken(serverConfig, accessToken, fcmToken)
            
            if (result.isSuccess) {
                Timber.i("FcmTokenWorker: FCM token synced successfully.")
                dataStoreManager.saveFcmToken(fcmToken)
                Result.success()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Timber.e("FcmTokenWorker: Failed to sync token: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "FcmTokenWorker: Error during token sync")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "FcmTokenSyncWork"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FcmTokenWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
