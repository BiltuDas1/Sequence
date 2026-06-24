package com.github.biltudas1.sequence.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val status: Boolean,
    val message: String,
    val data: T? = null
)

@Serializable
data class RegistrationRequest(
    val idToken: String
)

@Serializable
data class UserData(
    val id: String,
    val email: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val created_at: String
)

@Serializable
data class LoginRequest(
    val idToken: String
)

@Serializable
data class LoginData(
    val id: String,
    val email: String,
    val firstname: String? = null,
    val lastname: String? = null,
    val jwt: JwtTokens
)

@Serializable
data class JwtTokens(
    val access_token: String,
    val refresh_token: String
)

@Serializable
data class RefreshRequest(
    val refresh_token: String
)

@Serializable
data class AddContactRequest(
    val email: String
)

@Serializable
data class RemoveContactRequest(
    val email: String
)

@Serializable
data class FcmTokenRequest(
    val fcmToken: String?
)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String
)

@Serializable
data class VoiceCallRequest(
    val email: String
)

@Serializable
data class VoiceCallResponse(
    val roomId: String,
    val callee: CalleeData
)

@Serializable
data class EndCallRequest(
    val roomId: String
)

@Serializable
data class CalleeData(
    val id: String,
    val email: String,
    val first_name: String? = null,
    val last_name: String? = null
)

@Serializable
data class ServerVersionData(
    val version: String
)
