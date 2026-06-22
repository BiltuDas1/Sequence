package com.github.biltudas1.sequence.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
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
import com.github.biltudas1.sequence.ui.theme.TextSecondary

@Composable
fun CallScreenContent(
    roomId: String,
    callerName: String,
    callerEmail: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onCallStopped: () -> Unit,
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Top Right Info Button
        IconButton(
            onClick = { showInfoDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Call Info",
                tint = Color.White.copy(alpha = 0.6f)
            )
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
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = callerName.ifEmpty { "Unknown" },
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = callerEmail,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            val displayStatus = when (statusMessage) {
                "Receiver is talking with somebody" -> "On another call"
                else -> statusMessage ?: "Connecting"
            }

            Text(
                text = displayStatus,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (displayStatus == "On another call") Color.Yellow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f),
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
                    tint = if (isMuted) Color.Red else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Stop Call Button
            FloatingActionButton(
                onClick = { onCallStopped() },
                containerColor = Color.Red,
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
                Icon(
                    imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isSpeakerOn) "Speaker Off" else "Speaker On",
                    tint = if (isSpeakerOn) Color.Green else Color.White,
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
                        Text("ID: $roomId")
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
