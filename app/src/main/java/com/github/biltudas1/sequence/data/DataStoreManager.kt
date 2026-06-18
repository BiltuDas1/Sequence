package com.github.biltudas1.sequence.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.github.biltudas1.sequence.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

class DataStoreManager(private val context: Context) {

    private val cryptoManager = CryptoManager()

    companion object {
        private val ENDPOINT = stringPreferencesKey("endpoint")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val USE_HTTPS = booleanPreferencesKey("use_https")
        private val USE_WSS = booleanPreferencesKey("use_wss")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
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

    val accessTokenFlow: Flow<String?> = context.dataStore.data.map { it[ACCESS_TOKEN]?.decrypt() }
    val refreshTokenFlow: Flow<String?> = context.dataStore.data.map { it[REFRESH_TOKEN]?.decrypt() }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { preferences ->
            preferences[ENDPOINT] = config.endpoint.encrypt()
            preferences[USERNAME] = config.username.encrypt()
            preferences[PASSWORD] = config.password.encrypt()
            preferences[USE_HTTPS] = config.useHttps
            preferences[USE_WSS] = config.useWss
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
