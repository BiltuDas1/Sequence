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

class AuthService(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun registerUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<UserData>> {
        return performPost(serverConfig, "users/register", RegistrationRequest(idToken))
    }

    suspend fun loginUser(serverConfig: ServerConfig, idToken: String): Result<ApiResponse<LoginData>> {
        return performPost(serverConfig, "users/login", LoginRequest(idToken))
    }

    private suspend inline fun <reified REQ : Any, reified RES : Any> performPost(
        serverConfig: ServerConfig,
        path: String,
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
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body.string()
                    Log.d("AuthService", "Response from $path: $bodyString")
                    
                    if (response.isSuccessful) {
                        val parsed = json.decodeFromString<RES>(bodyString)
                        // If it's an ApiResponse, check the status field
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
                Log.e("AuthService", "Network error", e)
                Result.failure(e)
            }
        }
    }
}
