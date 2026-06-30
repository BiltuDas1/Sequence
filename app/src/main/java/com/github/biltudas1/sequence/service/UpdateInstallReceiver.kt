package com.github.biltudas1.sequence.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.biltudas1.sequence.util.UpdateDownloadManager

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val filePath = intent.getStringExtra("file_path")
        if (filePath != null) {
            // Dismiss the notifications
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1001) // NOTIFICATION_ID from UpdateDownloadService
            notificationManager.cancel(1002) // COMPLETE_NOTIFICATION_ID from UpdateDownloadService

            val downloadManager = UpdateDownloadManager(context)
            downloadManager.installUpdate(filePath)
        }
    }
}
