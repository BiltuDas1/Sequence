package com.github.biltudas1.sequence.data.remote

import android.util.Log
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthService(val client: OkHttpClient, internal val dataStoreManager: DataStoreManager) {
    val json = Json { ignoreUnknownKeys = true }
    internal val mediaType = "application/json; charset=utf-8".toMediaType()
    internal val refreshMutex = Mutex()

    suspend fun getContacts(serverConfig: ServerConfig, accessToken: String): Result<ApiResponse<List<UserData>>> {
        return performGet(serverConfig, "contacts", accessToken)
    }

    suspend fun registerUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<UserData>> {
        return performPost(serverConfig, "users/register", null, RegistrationRequest(idToken))
    }

    suspend fun loginUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<LoginData>> {
        return performPost(serverConfig, "users/login", null, LoginRequest(idToken))
    }

    suspend fun addContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<UserData>> {
        return performPost(serverConfig, "contacts/add", accessToken, AddContactRequest(email))
    }

    suspend fun removeContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, "contacts/remove", accessToken, RemoveContactRequest(email))
    }

    suspend fun refreshToken(serverConfig: ServerConfig, refreshToken: String): Result<ApiResponse<JwtTokens>> {
        return performPost(serverConfig, "token/refresh", null, RefreshRequest(refreshToken))
    }

    suspend fun updateFcmToken(serverConfig: ServerConfig, accessToken: String, fcmToken: String?): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, "users/fcm-token", accessToken, FcmTokenRequest(fcmToken))
    }

    suspend fun sendVoiceCall(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<VoiceCallResponse>> {
        return performPost(serverConfig, "voicecall/send", accessToken, VoiceCallRequest(email))
    }

    suspend fun endVoiceCall(serverConfig: ServerConfig, accessToken: String, roomId: String): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, "voicecall/end", accessToken, EndCallRequest(roomId))
    }

    private suspend inline fun <reified RES : Any> performGet(
        serverConfig: ServerConfig,
        path: String,
        accessToken: String
    ): Result<RES> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverConfig.cleanEndpoint
                val protocol = if (serverConfig.useHttps) "https" else "http"
                val url = "$protocol://$baseUrl/$path"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                executeRequest<RES>(request, serverConfig)
            } catch (e: Exception) {
                Log.e("AuthService", "GET error", e)
                Result.failure(e)
            }
        }
    }

    private suspend inline fun <reified REQ : Any, reified RES : Any> performPost(
        serverConfig: ServerConfig,
        path: String,
        accessToken: String?,
        body: REQ
    ): Result<RES> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverConfig.cleanEndpoint
                val protocol = if (serverConfig.useHttps) "https" else "http"
                val url = "$protocol://$baseUrl/$path"

                val requestBody = json.encodeToString(body).toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .apply {
                        accessToken?.let { addHeader("Authorization", "Bearer $it") }
                    }
                    .post(requestBody)
                    .build()

                executeRequest<RES>(request, serverConfig)
            } catch (e: Exception) {
                Log.e("AuthService", "POST error", e)
                Result.failure(e)
            }
        }
    }

    @PublishedApi
    internal suspend inline fun <reified RES : Any> executeRequest(request: Request, serverConfig: ServerConfig): Result<RES> {
        return try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            response.use { resp ->
                val bodyString = resp.body.string()
                Log.d("AuthService", "Response [${resp.code}] from ${request.url}: $bodyString")

                if (resp.isSuccessful) {
                    val parsed = json.decodeFromString<RES>(bodyString)
                    if (parsed is ApiResponse<*> && !parsed.status) {
                        Result.failure(Exception(parsed.message))
                    } else {
                        Result.success(parsed)
                    }
                } else if (resp.code == 401 || (bodyString.contains("expired", true) && bodyString.contains("token", true))) {
                    val newAccessToken = tryRefresh(serverConfig)
                    if (newAccessToken != null) {
                        val newRequest = request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                        return executeRequestWithoutRetry(newRequest)
                    }
                    
                    try {
                        val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                        Result.failure(Exception(errorResponse.message))
                    } catch (e: Exception) {
                        Result.failure(Exception("Request failed: ${resp.code}"))
                    }
                } else {
                    try {
                        val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                        Result.failure(Exception(errorResponse.message))
                    } catch (e: Exception) {
                        Result.failure(Exception("Request failed: ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @PublishedApi
    internal suspend fun tryRefresh(serverConfig: ServerConfig): String? {
        return refreshMutex.withLock {
            val currentRefreshToken = dataStoreManager.refreshTokenFlow.firstOrNull() ?: return null
            val refreshResult = refreshToken(serverConfig, currentRefreshToken)
            if (refreshResult.isSuccess) {
                val newTokens = refreshResult.getOrNull()?.data
                if (newTokens != null) {
                    dataStoreManager.saveTokens(newTokens.access_token, newTokens.refresh_token)
                    return newTokens.access_token
                }
            } else {
                dataStoreManager.clearTokens()
            }
            null
        }
    }

    @PublishedApi
    internal suspend inline fun <reified RES : Any> executeRequestWithoutRetry(request: Request): Result<RES> {
        return try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            response.use { resp ->
                val bodyString = resp.body.string()
                if (resp.isSuccessful) {
                    val parsed = json.decodeFromString<RES>(bodyString)
                    if (parsed is ApiResponse<*> && !parsed.status) {
                        Result.failure(Exception(parsed.message))
                    } else {
                        Result.success(parsed)
                    }
                } else {
                    try {
                        val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                        Result.failure(Exception(errorResponse.message))
                    } catch (e: Exception) {
                        Result.failure(Exception("Request failed: ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
