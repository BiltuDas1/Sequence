package com.github.biltudas1.sequence.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.github.biltudas1.sequence.data.model.*
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

class DataStoreManager private constructor(private val context: Context) {

    private val cryptoManager = CryptoManager()
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    companion object {
        @Volatile
        private var INSTANCE: DataStoreManager? = null

        fun getInstance(context: Context): DataStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataStoreManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val ENDPOINT = stringPreferencesKey("endpoint")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val USE_HTTPS = booleanPreferencesKey("use_https")
        private val USE_WSS = booleanPreferencesKey("use_wss")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val WEBRTC_CONFIG = stringPreferencesKey("webrtc_config")
        private val LATEST_VERSION = stringPreferencesKey("latest_version")
        private val LATEST_RELEASE_URL = stringPreferencesKey("latest_release_url")
        private val LATEST_APK_URL = stringPreferencesKey("latest_apk_url")
        private val LAST_VERSION_CHECK = longPreferencesKey("last_version_check")
        
        private val STUN_BYTES_SENT = longPreferencesKey("stun_bytes_sent")
        private val STUN_BYTES_RECEIVED = longPreferencesKey("stun_bytes_received")
        private val TURN_BYTES_SENT = longPreferencesKey("turn_bytes_sent")
        private val TURN_BYTES_RECEIVED = longPreferencesKey("turn_bytes_received")
        private val AUDIO_QUALITY_LEVEL = stringPreferencesKey("audio_quality_level")
        private val UPDATE_CHECK_INTERVAL = stringPreferencesKey("update_check_interval")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val LAST_SELECTED_TAB = intPreferencesKey("last_selected_tab")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")

        private val DOWNLOAD_STATUS = stringPreferencesKey("download_status")
        private val DOWNLOAD_PROGRESS = floatPreferencesKey("download_progress")
        private val DOWNLOADED_BYTES = longPreferencesKey("downloaded_bytes")
        private val TOTAL_BYTES = longPreferencesKey("total_bytes")
        private val DOWNLOAD_URL = stringPreferencesKey("download_url")
        private val DOWNLOAD_FILE_PATH = stringPreferencesKey("download_file_path")
        private val DOWNLOAD_VERSION_TAG = stringPreferencesKey("download_version_tag")
    }

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map { preferences ->
        ServerConfig(
            endpoint = preferences[ENDPOINT]?.decrypt() ?: "",
            username = preferences[USERNAME]?.decrypt() ?: "",
            password = preferences[PASSWORD]?.decrypt() ?: "",
            useHttps = preferences[USE_HTTPS] ?: true,
            useWss = preferences[USE_WSS] ?: true
        )
    }

    val webrtcConfigFlow: Flow<WebRTCConfig> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[WEBRTC_CONFIG]?.decrypt()
        if (jsonStr != null) {
            try {
                json.decodeFromString<WebRTCConfig>(jsonStr)
            } catch (e: Exception) {
                WebRTCConfig()
            }
        } else {
            WebRTCConfig()
        }
    }

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN]?.decrypt() }
    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN]?.decrypt() }

    val versionCacheFlow: Flow<VersionCache> = context.dataStore.data.map { preferences ->
        VersionCache(
            tag = preferences[LATEST_VERSION],
            htmlUrl = preferences[LATEST_RELEASE_URL],
            apkUrl = preferences[LATEST_APK_URL],
            lastCheck = preferences[LAST_VERSION_CHECK] ?: 0L
        )
    }

    data class VersionCache(
        val tag: String?,
        val htmlUrl: String?,
        val apkUrl: String?,
        val lastCheck: Long
    )

    val dataUsageFlow: Flow<DataUsage> = context.dataStore.data.map { preferences ->
        DataUsage(
            stunSent = preferences[STUN_BYTES_SENT] ?: 0L,
            stunReceived = preferences[STUN_BYTES_RECEIVED] ?: 0L,
            turnSent = preferences[TURN_BYTES_SENT] ?: 0L,
            turnReceived = preferences[TURN_BYTES_RECEIVED] ?: 0L
        )
    }

    val audioQualityFlow: Flow<AudioQualityLevel> = context.dataStore.data.map { preferences ->
        val name = preferences[AUDIO_QUALITY_LEVEL]
        if (name != null) {
            try {
                AudioQualityLevel.valueOf(name)
            } catch (e: Exception) {
                AudioQualityLevel.STANDARD
            }
        } else {
            AudioQualityLevel.STANDARD
        }
    }

    val appThemeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val name = preferences[APP_THEME]
        if (name != null) {
            try {
                AppTheme.valueOf(name)
            } catch (e: Exception) {
                AppTheme.SYSTEM
            }
        } else {
            AppTheme.SYSTEM
        }
    }

    val updateIntervalFlow: Flow<String> = context.dataStore.data.map { it[UPDATE_CHECK_INTERVAL] ?: "Daily" }

    val lastSelectedTabFlow: Flow<Int> = context.dataStore.data.map { it[LAST_SELECTED_TAB] ?: 0 }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[USER_EMAIL]?.decrypt() }

    val privacyModeFlow: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_MODE] ?: false }

    data class DownloadInfo(
        val status: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val url: String?,
        val filePath: String?,
        val versionTag: String?
    )

    val downloadInfoFlow: Flow<DownloadInfo> = context.dataStore.data
        .map { preferences ->
            DownloadInfo(
                status = preferences[DOWNLOAD_STATUS] ?: "IDLE",
                progress = preferences[DOWNLOAD_PROGRESS] ?: 0f,
                downloadedBytes = preferences[DOWNLOADED_BYTES] ?: 0L,
                totalBytes = preferences[TOTAL_BYTES] ?: 0L,
                url = preferences[DOWNLOAD_URL],
                filePath = preferences[DOWNLOAD_FILE_PATH],
                versionTag = preferences[DOWNLOAD_VERSION_TAG]
            )
        }

    suspend fun saveServerConfig(config: ServerConfig) {
        Timber.i("Saving Server Config: endpoint=${config.cleanEndpoint}, useHttps=${config.useHttps}")
        context.dataStore.edit { preferences ->
            preferences[ENDPOINT] = config.endpoint.encrypt()
            preferences[USERNAME] = config.username.encrypt()
            preferences[PASSWORD] = config.password.encrypt()
            preferences[USE_HTTPS] = config.useHttps
            preferences[USE_WSS] = config.useWss
        }
    }

    suspend fun saveWebRTCConfig(config: WebRTCConfig) {
        context.dataStore.edit { preferences ->
            preferences[WEBRTC_CONFIG] = json.encodeToString(config).encrypt()
        }
    }

    suspend fun saveVersionCache(tag: String, htmlUrl: String, apkUrl: String?, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_VERSION] = tag
            preferences[LATEST_RELEASE_URL] = htmlUrl
            if (apkUrl != null) preferences[LATEST_APK_URL] = apkUrl
            preferences[LAST_VERSION_CHECK] = timestamp
        }
    }

    suspend fun addDataUsage(stunSent: Long, stunReceived: Long, turnSent: Long, turnReceived: Long) {
        context.dataStore.edit { preferences ->
            val currentStunSent = preferences[STUN_BYTES_SENT] ?: 0L
            val currentStunRecv = preferences[STUN_BYTES_RECEIVED] ?: 0L
            val currentTurnSent = preferences[TURN_BYTES_SENT] ?: 0L
            val currentTurnRecv = preferences[TURN_BYTES_RECEIVED] ?: 0L
            
            preferences[STUN_BYTES_SENT] = currentStunSent + stunSent
            preferences[STUN_BYTES_RECEIVED] = currentStunRecv + stunReceived
            preferences[TURN_BYTES_SENT] = currentTurnSent + turnSent
            preferences[TURN_BYTES_RECEIVED] = currentTurnRecv + turnReceived
        }
    }

    suspend fun resetDataUsage() {
        context.dataStore.edit { preferences ->
            preferences[STUN_BYTES_SENT] = 0L
            preferences[STUN_BYTES_RECEIVED] = 0L
            preferences[TURN_BYTES_SENT] = 0L
            preferences[TURN_BYTES_RECEIVED] = 0L
        }
    }

    suspend fun saveAudioQuality(level: AudioQualityLevel) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_QUALITY_LEVEL] = level.name
        }
    }

    suspend fun saveAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme.name
        }
    }

    suspend fun saveUpdateInterval(interval: String) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_CHECK_INTERVAL] = interval
        }
    }

    suspend fun saveLastSelectedTab(tab: Int) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SELECTED_TAB] = tab
        }
    }

    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_EMAIL] = email.encrypt()
        }
    }

    suspend fun savePrivacyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRIVACY_MODE] = enabled
        }
    }

    suspend fun saveDownloadStatus(status: String) {
        context.dataStore.edit { preferences -> preferences[DOWNLOAD_STATUS] = status }
    }

    suspend fun saveDownloadProgress(progress: Float, downloaded: Long, total: Long) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PROGRESS] = progress
            preferences[DOWNLOADED_BYTES] = downloaded
            preferences[TOTAL_BYTES] = total
        }
    }

    suspend fun saveDownloadDetails(url: String, filePath: String, versionTag: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_URL] = url
            preferences[DOWNLOAD_FILE_PATH] = filePath
            preferences[DOWNLOAD_VERSION_TAG] = versionTag
        }
    }

    suspend fun clearDownloadData() {
        context.dataStore.edit { preferences ->
            preferences.remove(DOWNLOAD_STATUS)
            preferences.remove(DOWNLOAD_PROGRESS)
            preferences.remove(DOWNLOADED_BYTES)
            preferences.remove(TOTAL_BYTES)
            preferences.remove(DOWNLOAD_URL)
            preferences.remove(DOWNLOAD_FILE_PATH)
            preferences.remove(DOWNLOAD_VERSION_TAG)
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        Timber.i("Saving new JWT tokens. AccessToken: ${AppLogger.redact(accessToken)}")
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken.encrypt()
            preferences[REFRESH_TOKEN] = refreshToken.encrypt()
        }
    }

    suspend fun clearTokens() {
        Timber.i("Clearing JWT tokens")
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
        }
    }

    suspend fun notifySessionExpired() {
        Timber.w("Session expired notification triggered")
        clearTokens()
        _sessionExpiredEvent.emit(Unit)
    }

    private fun String.encrypt(): String {
        if (this.isEmpty()) return ""
        val bytes = cryptoManager.encrypt(this)
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun String.decrypt(): String {
        if (this.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(this, Base64.DEFAULT)
            cryptoManager.decrypt(bytes)
        } catch (e: Exception) {
            ""
        }
    }
}
