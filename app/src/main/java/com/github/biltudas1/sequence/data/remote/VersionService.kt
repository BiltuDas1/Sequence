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

    suspend fun getLatestRelease(ignoreCache: Boolean = false): GitHubRelease? {
        val (cachedTag, cachedUrl, lastCheck) = dataStoreManager.versionCacheFlow.first()
        val currentTime = System.currentTimeMillis()

        if (!ignoreCache && cachedTag != null && cachedUrl != null && (currentTime - lastCheck) < 900_000L) {
            Log.d("VersionService", "Returning cached version: $cachedTag")
            return GitHubRelease(cachedTag, cachedUrl)
        }

        return try {
            Log.d("VersionService", "Fetching latest version from GitHub...")
            val request = Request.Builder().url(releasesUrl).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                val latest = releases.firstOrNull()

                if (latest != null) {
                    dataStoreManager.saveVersionCache(latest.tag_name, latest.html_url, currentTime)
                    latest
                } else {
                    null
                }
            } else {
                if (cachedTag != null && cachedUrl != null) GitHubRelease(cachedTag, cachedUrl) else null
            }
        } catch (e: Exception) {
            Log.e("VersionService", "Error fetching version", e)
            if (cachedTag != null && cachedUrl != null) GitHubRelease(cachedTag, cachedUrl) else null
        }
    }

    suspend fun getLatestVersion(): String? {
        return getLatestRelease()?.tag_name
    }
}
