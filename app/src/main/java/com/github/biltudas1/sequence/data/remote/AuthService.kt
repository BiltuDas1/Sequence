package com.github.biltudas1.sequence.data.remote

import android.util.Log
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthService(val client: OkHttpClient) {
    val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

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

                executeRequest<RES>(request)
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

                executeRequest<RES>(request)
            } catch (e: Exception) {
                Log.e("AuthService", "POST error", e)
                Result.failure(e)
            }
        }
    }

    @PublishedApi
    internal inline fun <reified RES : Any> executeRequest(request: Request): Result<RES> {
        return try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body.string()
                Log.d("AuthService", "Response: $bodyString")

                if (response.isSuccessful) {
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
                        Result.failure(Exception("Request failed: ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
