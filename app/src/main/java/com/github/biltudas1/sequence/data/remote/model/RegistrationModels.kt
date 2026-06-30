package com.github.biltudas1.sequence.data.remote.model

import kotlinx.serialization.SerialName
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
    val privacy_mode: Boolean = false,
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
    @SerialName("first_name") val firstname: String? = null,
    @SerialName("last_name") val lastname: String? = null,
    val privacy_mode: Boolean = false,
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
data class LogoutRequest(
    val refresh_token: String? = null
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
    val fcmToken: String?,
    @SerialName("fcm_token") val fcmTokenAlt: String? = fcmToken
)

@Serializable
data class PrivacyModeRequest(
    @SerialName("privacy_mode") val privacyMode: Boolean
)

@Serializable
data class PrivacyModeData(
    val privacy_mode: Boolean
)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
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
