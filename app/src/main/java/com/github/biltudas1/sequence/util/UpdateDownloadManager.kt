package com.github.biltudas1.sequence.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.service.UpdateDownloadService
import kotlinx.coroutines.flow.Flow
import java.io.File

class UpdateDownloadManager(private val context: Context) {

    private val dataStoreManager = DataStoreManager.getInstance(context)

    val downloadInfoFlow: Flow<DataStoreManager.DownloadInfo> = dataStoreManager.downloadInfoFlow

    suspend fun startDownload(url: String, versionTag: String) {
        val fileName = "sequence_update_${versionTag.replace(".", "_")}.apk"
        val file = File(context.cacheDir, "updates/$fileName")
        file.parentFile?.mkdirs()

        dataStoreManager.saveDownloadDetails(url, file.absolutePath, versionTag)
        UpdateDownloadService.start(context, url, file.absolutePath, versionTag)
    }

    suspend fun pauseDownload() {
        context.stopService(Intent(context, UpdateDownloadService::class.java))
        dataStoreManager.saveDownloadStatus("PAUSED")
    }

    fun resumeDownload(info: DataStoreManager.DownloadInfo) {
        if (info.url != null && info.filePath != null && info.versionTag != null) {
            UpdateDownloadService.start(context, info.url, info.filePath, info.versionTag)
        }
    }

    fun installUpdate(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun clearDownload() {
        dataStoreManager.clearDownloadData()
    }
}
