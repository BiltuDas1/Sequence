package com.github.biltudas1.sequence.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.github.biltudas1.sequence.media.AudioOutput
import com.github.biltudas1.sequence.ui.theme.Crimson
import com.github.biltudas1.sequence.ui.theme.DeepGreen
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import com.github.biltudas1.sequence.ui.theme.TextSecondary

@Composable
fun CallScreenContent(
    roomId: String,
    callerName: String,
    callerEmail: String,
    isMuted: Boolean,
    audioOutput: AudioOutput,
    isUsingRelay: Boolean = false,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onCallStopped: () -> Unit,
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    
    var secondsElapsed by remember { mutableIntStateOf(0) }
    val isConnected = statusMessage == "Connected"

    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                secondsElapsed++
            }
        }
    }

    val durationDisplay = remember(secondsElapsed) {
        val mins = secondsElapsed / 60
        val secs = secondsElapsed % 60
        String.format("%02d:%02d", mins, secs)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Top Buttons (Info & Relay Indicator)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isUsingRelay) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = "Relay Active",
                    tint = if (LocalIsDarkTheme.current) Color.Yellow.copy(alpha = 0.6f) else Crimson.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 8.dp).size(24.dp)
                )
            }

            IconButton(
                onClick = { showInfoDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Call Info",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        // Center Content -> Moved to Top Center
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp), // Position it in the top center
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(140.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = callerName.ifEmpty { "Unknown" },
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = callerEmail,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            if (isConnected) {
                Text(
                    text = durationDisplay,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val displayStatus = when (statusMessage) {
                "Receiver is talking with somebody" -> "On another call"
                else -> statusMessage ?: "Connecting"
            }

            Text(
                text = displayStatus,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (displayStatus == "On another call") {
                    if (LocalIsDarkTheme.current) Color.Yellow.copy(alpha = 0.8f) else Crimson.copy(alpha = 0.8f)
                } else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            IconButton(
                onClick = onMuteToggle,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Stop Call Button
            FloatingActionButton(
                onClick = { onCallStopped() },
                containerColor = if (LocalIsDarkTheme.current) Color(0xFFF44336) else MaterialTheme.colorScheme.error,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "Stop Call",
                    modifier = Modifier.size(36.dp)
                )
            }

            // Speaker Button
            IconButton(
                onClick = onSpeakerToggle,
                modifier = Modifier.size(56.dp)
            ) {
                val (icon, tint) = when (audioOutput) {
                    AudioOutput.EARPIECE -> Icons.AutoMirrored.Filled.VolumeOff to MaterialTheme.colorScheme.onBackground
                    AudioOutput.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to if (LocalIsDarkTheme.current) Color.Green else DeepGreen
                    AudioOutput.HEADSET -> Icons.Default.Headset to if (LocalIsDarkTheme.current) Color.Cyan else Color.Blue
                }

                Icon(
                    imageVector = icon,
                    contentDescription = "Toggle Audio Output",
                    tint = tint,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Call Details") },
                text = {
                    Column {
                        InfoRow(label = "Room ID", value = roomId)
                        InfoRow(label = "Audio Output", value = audioOutput.name.lowercase().replaceFirstChar { it.uppercase() })
                        InfoRow(label = "Microphone", value = if (isMuted) "Muted" else "Active")
                        InfoRow(label = "Connection Status", value = statusMessage ?: "Unknown")
                        
                        if (isUsingRelay) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Note: This call is being routed through a relay server (TURN) because a direct connection could not be established.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(text = value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
