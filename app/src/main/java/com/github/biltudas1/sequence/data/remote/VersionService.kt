package com.github.biltudas1.sequence.data.remote

import android.util.Log
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.model.GitHubAsset
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
        val cache = dataStoreManager.versionCacheFlow.first()
        val currentTime = System.currentTimeMillis()

        // Ignore cache if tag matches but apkUrl is missing (stale cache from old app version)
        val isCacheStale = cache.tag != null && cache.apkUrl == null && currentVersion != null && isNewerVersion(cache.tag, currentVersion)

        if (!ignoreCache && !isCacheStale && cache.tag != null && cache.htmlUrl != null && (currentTime - cache.lastCheck) < 900_000L) {
            if (currentVersion == null || getReleaseType(cache.tag) == getReleaseType(currentVersion)) {
                Log.d("VersionService", "Returning cached version: ${cache.tag}")
                val assets = cache.apkUrl?.let { listOf(GitHubAsset("update.apk", it, 0)) } ?: emptyList<GitHubAsset>()
                return GitHubRelease(cache.tag, cache.htmlUrl, assets)
            }
        }

        return try {
            Log.d("VersionService", "Fetching latest version from GitHub...")
            val request = Request.Builder().url(AppConstants.GITHUB_RELEASES_API_URL).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body.string()
                Log.d("VersionService", "GitHub Response: $body")
                val releases = json.decodeFromString<List<GitHubRelease>>(body)
                
                val latest = if (currentVersion != null) {
                    val targetType = getReleaseType(currentVersion)
                    Log.d("VersionService", "Filtering for release type: $targetType (current: $currentVersion)")
                    releases.firstOrNull { getReleaseType(it.tag_name) == targetType }
                } else {
                    releases.firstOrNull()
                }

                if (latest != null) {
                    val apkAsset = latest.assets.find { it.name.lowercase().endsWith(".apk") }
                    val apkUrl = apkAsset?.browser_download_url
                    Log.d("VersionService", "Found APK URL: $apkUrl from ${latest.assets.size} assets")
                    dataStoreManager.saveVersionCache(latest.tag_name, latest.html_url, apkUrl, currentTime)
                    
                    // Ensure the returned object has the assets populated even if they were somehow filtered
                    if (latest.assets.isEmpty() && apkUrl != null) {
                        latest.copy(assets = listOf(GitHubAsset("update.apk", apkUrl, 0)))
                    } else {
                        latest
                    }
                } else {
                    null
                }
            } else {
                if (cache.tag != null && cache.htmlUrl != null) {
                    val assets = cache.apkUrl?.let { listOf(GitHubAsset("update.apk", it, 0)) } ?: emptyList<GitHubAsset>()
                    GitHubRelease(cache.tag, cache.htmlUrl, assets)
                } else null
            }
        } catch (e: Exception) {
            Log.e("VersionService", "Error fetching version", e)
            if (cache.tag != null && cache.htmlUrl != null) {
                val assets = cache.apkUrl?.let { listOf(GitHubAsset("update.apk", it, 0)) } ?: emptyList<GitHubAsset>()
                GitHubRelease(cache.tag, cache.htmlUrl, assets)
            } else null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return latest.removePrefix("v") != current.removePrefix("v")
    }

    suspend fun getLatestVersion(): String? {
        return getLatestRelease()?.tag_name
    }
}
