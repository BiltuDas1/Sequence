package com.github.biltudas1.sequence.data.remote

import android.util.Log
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.model.GitHubRelease
import com.github.biltudas1.sequence.util.AppConstants
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class VersionService(private val client: OkHttpClient, private val dataStoreManager: DataStoreManager) {
    private val json = Json { ignoreUnknownKeys = true }

    enum class ReleaseType { ALPHA, BETA, STABLE }

    private fun getReleaseType(tag: String): ReleaseType {
        val t = tag.lowercase()
        return when {
            t.contains("alpha") || (t.contains("a") && Regex("\\da\\d").containsMatchIn(t)) -> ReleaseType.ALPHA
            t.contains("beta") || (t.contains("b") && Regex("\\db\\d").containsMatchIn(t)) || t.contains("rc") -> ReleaseType.BETA
            else -> ReleaseType.STABLE
        }
    }

    suspend fun getLatestRelease(currentVersion: String? = null, ignoreCache: Boolean = false): GitHubRelease? {
        val (cachedTag, cachedUrl, lastCheck) = dataStoreManager.versionCacheFlow.first()
        val currentTime = System.currentTimeMillis()

        if (!ignoreCache && cachedTag != null && cachedUrl != null && (currentTime - lastCheck) < 900_000L) {
            if (currentVersion == null || getReleaseType(cachedTag) == getReleaseType(currentVersion)) {
                Log.d("VersionService", "Returning cached version: $cachedTag")
                return GitHubRelease(cachedTag, cachedUrl)
            }
        }

        return try {
            Log.d("VersionService", "Fetching latest version from GitHub...")
            val request = Request.Builder().url(AppConstants.GITHUB_RELEASES_API_URL).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                
                val latest = if (currentVersion != null) {
                    val targetType = getReleaseType(currentVersion)
                    Log.d("VersionService", "Filtering for release type: $targetType (current: $currentVersion)")
                    releases.firstOrNull { getReleaseType(it.tag_name) == targetType }
                } else {
                    releases.firstOrNull()
                }

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
