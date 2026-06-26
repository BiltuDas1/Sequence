package com.github.biltudas1.sequence.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.utils.LastCharPasswordVisualTransformation
import com.github.biltudas1.sequence.util.AppConstants
import com.github.biltudas1.sequence.util.ToastUtils
import com.github.biltudas1.sequence.util.VersionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ServerConfigDialog(
    config: ServerConfig,
    authService: AuthService,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }
    var useHttps by remember { mutableStateOf(config.useHttps) }
    var useWss by remember { mutableStateOf(config.useWss) }

    var isTesting by remember { mutableStateOf(false) }
    var isValidated by remember { mutableStateOf(config.isValid()) }
    var isError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Testing animation state
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(isTesting) {
        if (isTesting) {
            while (true) {
                delay(500)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    val testConnection = {
        val currentConfig = ServerConfig(endpoint, username, password, useHttps, useWss)
        if (currentConfig.isValid()) {
            isTesting = true
            isValidated = false
            isError = false
            scope.launch {
                val result = authService.getServerVersion(currentConfig)
                isTesting = false
                val versionData = result.getOrNull()?.data
                if (versionData != null) {
                    val major = VersionUtils.extractMajorVersion(versionData.version)
                    if (major == AppConstants.COMPATIBLE_SERVER_MAJOR_VERSION) {
                        isValidated = true
                        ToastUtils.show(context, "Server version ${versionData.version} is compatible", Toast.LENGTH_SHORT)
                    } else if (major != null) {
                        isError = true
                        val msg = if (major > AppConstants.COMPATIBLE_SERVER_MAJOR_VERSION) {
                            context.getString(R.string.client_outdated, versionData.version)
                        } else {
                            context.getString(R.string.server_outdated, versionData.version)
                        }
                        ToastUtils.show(context, msg, Toast.LENGTH_LONG)
                    } else {
                        isError = true
                        ToastUtils.show(context, "Invalid server version format: ${versionData.version}", Toast.LENGTH_LONG)
                    }
                } else {
                    isError = true
                    val errorMsg = result.exceptionOrNull()?.message ?: "Connection failed"
                    ToastUtils.show(context, errorMsg, Toast.LENGTH_LONG)
                }
            }
        } else {
            isError = true
            ToastUtils.show(context, "Please enter a valid endpoint", Toast.LENGTH_SHORT)
        }
    }

    var maskLast by remember { mutableStateOf(true) }
    var lastPasswordLength by remember { mutableIntStateOf(password.length) }

    LaunchedEffect(password) {
        if (password.length > lastPasswordLength) {
            maskLast = false
            delay(1500.milliseconds) // Delay before masking the last character
            maskLast = true
        }
        lastPasswordLength = password.length
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Configuration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { 
                        endpoint = it
                        isValidated = false 
                    },
                    label = { Text("Server Endpoint") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && !isValidated && !isTesting && endpoint.isNotBlank()) {
                                testConnection()
                            }
                        },
                    singleLine = true,
                    isError = isError && endpoint.isBlank()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                        if (!focusState.isFocused && !isValidated && !isTesting && endpoint.isNotBlank()) {
                            testConnection()
                        }
                    },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                        if (!focusState.isFocused && !isValidated && !isTesting && endpoint.isNotBlank()) {
                            testConnection()
                        }
                    },
                    singleLine = true,
                    visualTransformation = LastCharPasswordVisualTransformation(maskLast),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("HTTPS")
                    Switch(checked = useHttps, onCheckedChange = { 
                        useHttps = it 
                        isValidated = false
                        testConnection()
                    })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("WSS")
                    Switch(checked = useWss, onCheckedChange = { 
                        useWss = it 
                        isValidated = false
                    })
                }
            }
        },
        confirmButton = {
            if (isTesting) {
                TextButton(onClick = { }, enabled = false) {
                    Text(stringResource(R.string.testing) + ".".repeat(dotCount))
                }
            } else if (isValidated) {
                TextButton(onClick = {
                    onSave(ServerConfig(endpoint, username, password, useHttps, useWss))
                }) {
                    Text("Save")
                }
            } else {
                TextButton(onClick = { testConnection() }) {
                    Text("Test Connection")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
