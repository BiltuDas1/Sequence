package com.github.biltudas1.sequence.data.remote

import android.util.Log
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.model.GitHubRelease
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class VersionService(private val client: OkHttpClient, private val dataStoreManager: DataStoreManager) {
    private val json = Json { ignoreUnknownKeys = true }
    private val releasesUrl = "https://api.github.com/repos/BiltuDas1/Sequence/releases"

    suspend fun getLatestVersion(): String? {
        val (cachedTag, lastCheck) = dataStoreManager.versionCacheFlow.first()
        val currentTime = System.currentTimeMillis()
        
        // 15 minutes = 15 * 60 * 1000 = 900,000 ms
        if (cachedTag != null && (currentTime - lastCheck) < 900_000L) {
            Log.d("VersionService", "Returning cached version: $cachedTag")
            return cachedTag
        }

        return try {
            Log.d("VersionService", "Fetching latest version from GitHub...")
            val request = Request.Builder().url(releasesUrl).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return cachedTag
                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                val latestTag = releases.firstOrNull()?.tag_name
                
                if (latestTag != null) {
                    dataStoreManager.saveVersionCache(latestTag, currentTime)
                    latestTag
                } else {
                    cachedTag
                }
            } else {
                cachedTag
            }
        } catch (e: Exception) {
            Log.e("VersionService", "Error fetching version", e)
            cachedTag
        }
    }
}
