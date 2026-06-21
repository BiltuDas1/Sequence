package com.github.biltudas1.sequence.data.model

import kotlinx.serialization.Serializable

@Serializable
data class IceServerConfig(
    val url: String,
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class WebRTCConfig(
    val stunServers: List<IceServerConfig> = listOf(IceServerConfig("stun:stun.l.google.com:19302")),
    val turnServers: List<IceServerConfig> = emptyList()
)
