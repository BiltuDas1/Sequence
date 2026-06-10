package com.github.biltudas1.sequence.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.ui.utils.LastCharPasswordVisualTransformation
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ServerConfigDialog(
    config: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }
    var useHttps by remember { mutableStateOf(config.useHttps) }
    var useWss by remember { mutableStateOf(config.useWss) }

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
                    onValueChange = { endpoint = it },
                    label = { Text("Server Endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
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
                    Switch(checked = useHttps, onCheckedChange = { useHttps = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("WSS")
                    Switch(checked = useWss, onCheckedChange = { useWss = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ServerConfig(endpoint, username, password, useHttps, useWss))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
