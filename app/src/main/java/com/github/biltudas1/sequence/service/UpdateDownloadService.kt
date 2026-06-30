package com.github.biltudas1.sequence.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.github.biltudas1.sequence.MainActivity
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class UpdateDownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStoreManager: DataStoreManager
    private val client = OkHttpClient()

    private var downloadJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETE_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "update_download_channel_v2"
        
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_VERSION_TAG = "extra_version_tag"

        fun start(context: Context, url: String, filePath: String, versionTag: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_VERSION_TAG, versionTag)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val filePath = intent?.getStringExtra(EXTRA_FILE_PATH)
        val versionTag = intent?.getStringExtra(EXTRA_VERSION_TAG)

        if (url != null && filePath != null && versionTag != null) {
            startForeground(NOTIFICATION_ID, createNotification(0))
            downloadJob?.cancel()
            downloadJob = serviceScope.launch {
                downloadFile(url, filePath)
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(url: String, filePath: String) {
        val file = File(filePath)
        try {
            dataStoreManager.saveDownloadStatus("DOWNLOADING")
            val downloadedBytes = if (file.exists()) file.length() else 0L

            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$downloadedBytes-")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    Timber.w("Range not satisfiable - file might be complete or corrupted. currentLength=${file.length()}")
                    val savedInfo = dataStoreManager.downloadInfoFlow.first()
                    if (savedInfo.totalBytes > 0 && file.length() >= savedInfo.totalBytes) {
                        dataStoreManager.saveDownloadStatus("COMPLETED")
                        showDownloadCompleteNotification(filePath)
                    } else {
                        dataStoreManager.saveDownloadStatus("FAILED")
                        updateNotification(text = "Download failed (416)", showProgress = false)
                    }
                } else {
                    Timber.e("Download failed with code: ${response.code}")
                    dataStoreManager.saveDownloadStatus("FAILED")
                    updateNotification(text = "Download failed", showProgress = false)
                }
                stopSelf()
                return
            }

            val body = response.body
            val totalBytes = body.contentLength() + downloadedBytes
            
            if (downloadedBytes >= totalBytes && totalBytes > 0) {
                Timber.i("File already fully downloaded. downloadedBytes=$downloadedBytes, totalBytes=$totalBytes")
                dataStoreManager.saveDownloadProgress(1.0f, totalBytes, totalBytes)
                dataStoreManager.saveDownloadStatus("COMPLETED")
                showDownloadCompleteNotification(filePath)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                return
            }

            dataStoreManager.saveDownloadProgress(
                progress = (downloadedBytes.toFloat() / totalBytes.toFloat()),
                downloaded = downloadedBytes,
                total = totalBytes
            )

            val inputStream: InputStream = body.byteStream()
            val outputStream = withContext(Dispatchers.IO) { FileOutputStream(file, true) }
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var currentDownloaded = downloadedBytes
            var lastUpdatePercent = -1
            var lastUpdateTime = 0L

            while (withContext(Dispatchers.IO) { inputStream.read(buffer) }.also { bytesRead = it } != -1) {
                withContext(Dispatchers.IO) { outputStream.write(buffer, 0, bytesRead) }
                currentDownloaded += bytesRead
                val progress = (currentDownloaded.toFloat() / totalBytes.toFloat())
                val percent = (progress * 100).toInt()
                val currentTime = System.currentTimeMillis()

                if (percent > lastUpdatePercent || (currentTime - lastUpdateTime) > 500L) {
                    lastUpdatePercent = percent
                    lastUpdateTime = currentTime
                    dataStoreManager.saveDownloadProgress(
                        progress = progress,
                        downloaded = currentDownloaded,
                        total = totalBytes
                    )
                    updateNotification(
                        text = "Downloading update... $percent%",
                        showProgress = true,
                        progress = percent
                    )
                }
            }

            outputStream.close()
            inputStream.close()

            dataStoreManager.saveDownloadStatus("COMPLETED")
            showDownloadCompleteNotification(filePath)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            stopSelf()

        } catch (e: Exception) {
            Timber.e(e, "Error downloading update")
            if (e is CancellationException) {
                dataStoreManager.saveDownloadStatus("PAUSED")
            } else {
                dataStoreManager.saveDownloadStatus("FAILED")
            }
            stopSelf()
        }
    }

    private fun createNotification(progress: Int, showProgress: Boolean = true): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("targetPage", "about")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("App Update")
            .setContentText(if (showProgress) "Downloading update... $progress%" else "Preparing download...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true) // THIS MAKES IT NON-SWIPEABLE
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (showProgress) {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, showProgress: Boolean, progress: Int = 0) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("targetPage", "about")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("App Update")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(showProgress) // TRUE during download, FALSE when failed
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        if (showProgress) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showDownloadCompleteNotification(filePath: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("targetPage", "about")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val installIntent = Intent(this, UpdateInstallReceiver::class.java).apply {
            putExtra("file_path", filePath)
        }
        val installPendingIntent = PendingIntent.getBroadcast(
            this, 1, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("App Update")
            .setContentText("Download complete. Ready to install.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Install Now", installPendingIntent)

        notificationManager.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Update Download"
            val descriptionText = "Notifications for app update downloads"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
