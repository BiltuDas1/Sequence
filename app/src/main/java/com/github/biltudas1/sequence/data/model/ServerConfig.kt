package com.github.biltudas1.sequence.data.model

data class ServerConfig(
    val endpoint: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = true,
    val useWss: Boolean = true
) {
    /**
     * Strips protocols (http://, https://, ws://, wss://) and trailing slashes
     */
    val cleanEndpoint: String
        get() = endpoint
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("wss://")
            .removePrefix("ws://")
            .removeSuffix("/")

    fun isValid(): Boolean {
        return endpoint.isNotBlank()
    }
}
