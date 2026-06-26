package com.github.biltudas1.sequence.data.remote

import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.model.*
import com.github.biltudas1.sequence.util.AppConstants
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class ForbiddenException(message: String) : Exception(message)

class AuthService(val client: OkHttpClient, internal val dataStoreManager: DataStoreManager) {
    val json = Json { ignoreUnknownKeys = true }
    internal val mediaType = "application/json; charset=utf-8".toMediaType()
    internal val refreshMutex = Mutex()

    suspend fun getServerVersion(serverConfig: ServerConfig): Result<ApiResponse<ServerVersionData>> {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverConfig.cleanEndpoint
                val protocol = if (serverConfig.useHttps) "https" else "http"
                val url = "$protocol://$baseUrl/${AppConstants.Api.VERSION}"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                executeRequest<ApiResponse<ServerVersionData>>(request, serverConfig)
            } catch (e: Exception) {
                Timber.e(e, "Version check error")
                Result.failure(e)
            }
        }
    }

    suspend fun getContacts(serverConfig: ServerConfig, accessToken: String): Result<ApiResponse<List<UserData>>> {
        return performGet(serverConfig, AppConstants.Api.CONTACTS, accessToken)
    }

    suspend fun registerUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<UserData>> {
        return performPost(serverConfig, AppConstants.Api.USERS_REGISTER, null, RegistrationRequest(idToken))
    }

    suspend fun loginUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<LoginData>> {
        return performPost(serverConfig, AppConstants.Api.USERS_LOGIN, null, LoginRequest(idToken))
    }

    suspend fun addContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<UserData>> {
        return performPost(serverConfig, AppConstants.Api.CONTACTS_ADD, accessToken, AddContactRequest(email))
    }

    suspend fun removeContact(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, AppConstants.Api.CONTACTS_REMOVE, accessToken, RemoveContactRequest(email))
    }

    suspend fun refreshToken(serverConfig: ServerConfig, refreshToken: String): Result<ApiResponse<JwtTokens>> {
        return performPost(serverConfig, AppConstants.Api.TOKEN_REFRESH, null, RefreshRequest(refreshToken))
    }

    suspend fun updateFcmToken(serverConfig: ServerConfig, accessToken: String, fcmToken: String?): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, AppConstants.Api.USERS_FCM_TOKEN, accessToken, FcmTokenRequest(fcmToken))
    }

    suspend fun updatePrivacyMode(serverConfig: ServerConfig, accessToken: String, enabled: Boolean): Result<ApiResponse<PrivacyModeData>> {
        return performPost(serverConfig, AppConstants.Api.USERS_PRIVACY, accessToken, PrivacyModeRequest(enabled))
    }

    suspend fun sendVoiceCall(serverConfig: ServerConfig, accessToken: String, email: String): Result<ApiResponse<VoiceCallResponse>> {
        return performPost(serverConfig, AppConstants.Api.VOICECALL_SEND, accessToken, VoiceCallRequest(email))
    }

    suspend fun endVoiceCall(serverConfig: ServerConfig, accessToken: String, roomId: String): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, AppConstants.Api.VOICECALL_END, accessToken, EndCallRequest(roomId))
    }

    suspend fun sendBusySignal(serverConfig: ServerConfig, accessToken: String, roomId: String): Result<ApiResponse<Unit>> {
        return performPost(serverConfig, AppConstants.Api.VOICECALL_BUSY, accessToken, EndCallRequest(roomId))
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
                Timber.e(e, "GET error at $path")
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
                Timber.e(e, "POST error at $path")
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
                val redactedUrl = request.url.toString().replace(Regex("token=[^&]*"), "token=REDACTED")
                Timber.d("Response [${resp.code}] from $redactedUrl")
                Timber.v("Response body: $bodyString")

                if (resp.isSuccessful) {
                    val parsed = json.decodeFromString<RES>(bodyString)
                    if (parsed is ApiResponse<*> && !parsed.status) {
                        Result.failure(Exception(parsed.message))
                    } else {
                        Result.success(parsed)
                    }
                } else if (resp.code == 401 || (bodyString.contains("expired", true) && bodyString.contains("token", true))) {
                    Timber.w("401 Unauthorized or expired token at ${request.url}")
                    // Only attempt refresh if the request had a token. 
                    // Prevents infinite loops if refresh/login themselves return 401.
                    if (request.header("Authorization") == null) {
                        try {
                            val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                            return Result.failure(Exception(errorResponse.message))
                        } catch (e: Exception) {
                            return Result.failure(Exception("Request failed: ${resp.code}"))
                        }
                    }

                    Timber.i("Attempting token refresh...")
                    val newAccessToken = tryRefresh(serverConfig)
                    if (newAccessToken != null) {
                        Timber.i("Token refresh successful. Retrying request.")
                        val newRequest = request.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            .build()
                        return executeRequestWithoutRetry<RES>(newRequest)
                    }
                    
                    Timber.e("Token refresh failed.")
                    try {
                        val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                        Result.failure(Exception(errorResponse.message))
                    } catch (e: Exception) {
                        Result.failure(Exception("Request failed: ${resp.code}"))
                    }
                } else if (resp.code == 403) {
                    try {
                        val errorResponse = json.decodeFromString<ApiResponse<Unit>>(bodyString)
                        Result.failure(ForbiddenException(errorResponse.message))
                    } catch (e: Exception) {
                        Result.failure(ForbiddenException("Access forbidden"))
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
                dataStoreManager.notifySessionExpired()
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
                val redactedUrl = request.url.toString().replace(Regex("token=[^&]*"), "token=REDACTED")
                Timber.d("Retry Response [${resp.code}] from $redactedUrl")
                Timber.v("Retry Response body: $bodyString")

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
