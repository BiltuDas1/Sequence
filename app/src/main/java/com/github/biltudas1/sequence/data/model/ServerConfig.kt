package com.github.biltudas1.sequence.data.model

data class ServerConfig(
    val endpoint: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = true,
    val useWss: Boolean = true
) {
    fun isValid(): Boolean {
        return endpoint.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }
}
