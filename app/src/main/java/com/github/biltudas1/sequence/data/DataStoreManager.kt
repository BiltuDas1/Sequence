package com.github.biltudas1.sequence.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.github.biltudas1.sequence.data.model.DataUsage
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.model.WebRTCConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

class DataStoreManager(private val context: Context) {

    private val cryptoManager = CryptoManager()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val ENDPOINT = stringPreferencesKey("endpoint")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val USE_HTTPS = booleanPreferencesKey("use_https")
        private val USE_WSS = booleanPreferencesKey("use_wss")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val WEBRTC_CONFIG = stringPreferencesKey("webrtc_config")
        private val LATEST_VERSION = stringPreferencesKey("latest_version")
        private val LAST_VERSION_CHECK = longPreferencesKey("last_version_check")
        
        private val STUN_BYTES_SENT = longPreferencesKey("stun_bytes_sent")
        private val STUN_BYTES_RECEIVED = longPreferencesKey("stun_bytes_received")
        private val TURN_BYTES_SENT = longPreferencesKey("turn_bytes_sent")
        private val TURN_BYTES_RECEIVED = longPreferencesKey("turn_bytes_received")
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

    val versionCacheFlow: Flow<Pair<String?, Long>> = context.dataStore.data.map { preferences ->
        preferences[LATEST_VERSION] to (preferences[LAST_VERSION_CHECK] ?: 0L)
    }

    val dataUsageFlow: Flow<DataUsage> = context.dataStore.data.map { preferences ->
        DataUsage(
            stunSent = preferences[STUN_BYTES_SENT] ?: 0L,
            stunReceived = preferences[STUN_BYTES_RECEIVED] ?: 0L,
            turnSent = preferences[TURN_BYTES_SENT] ?: 0L,
            turnReceived = preferences[TURN_BYTES_RECEIVED] ?: 0L
        )
    }

    suspend fun saveServerConfig(config: ServerConfig) {
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

    suspend fun saveVersionCache(tag: String, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_VERSION] = tag
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

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken.encrypt()
            preferences[REFRESH_TOKEN] = refreshToken.encrypt()
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(REFRESH_TOKEN)
        }
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
