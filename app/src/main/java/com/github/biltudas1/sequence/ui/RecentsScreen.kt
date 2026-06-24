package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.CallLogRepository
import com.github.biltudas1.sequence.data.local.CallLogEntity
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecentsScreen(
    onCallClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    val callLogs by repository.allCallLogs.collectAsStateWithLifecycle(initialValue = emptyList())

    if (callLogs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No recent calls", color = TextSecondary)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(callLogs) { log ->
                RecentCallItem(
                    log = log,
                    onCallClick = { onCallClick(log.email, log.name ?: log.email) }
                )
            }
        }
    }
}

@Composable
fun RecentCallItem(
    log: CallLogEntity,
    onCallClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }
    
    val durationString = remember(log.duration) {
        if (log.duration != null && log.duration > 0) {
            val seconds = log.duration / 1000
            val mins = seconds / 60
            val secs = seconds % 60
            if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.name?.ifBlank { log.email } ?: log.email,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (log.type == "MISSED") Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (log.type) {
                            "OUTGOING" -> Icons.Default.CallMade
                            "INCOMING" -> Icons.Default.CallReceived
                            else -> Icons.Default.CallMissed
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = when (log.type) {
                            "OUTGOING" -> Color(0xFF4CAF50)
                            "INCOMING" -> Color(0xFF2196F3)
                            else -> Color.Red
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$timeString${durationString?.let { " • $it" } ?: ""}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            
            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF4CAF50))
            }
        }
    }
}
