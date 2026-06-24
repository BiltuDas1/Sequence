package com.github.biltudas1.sequence.data.model

import com.github.biltudas1.sequence.util.AppConstants
import kotlinx.serialization.Serializable

@Serializable
data class IceServerConfig(
    val url: String,
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class WebRTCConfig(
    val stunServers: List<IceServerConfig> = listOf(IceServerConfig(AppConstants.DEFAULT_STUN_SERVER)),
    val turnServers: List<IceServerConfig> = emptyList()
)
