package com.github.biltudas1.sequence.ui.main

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.repository.CallLogRepository
import com.github.biltudas1.sequence.data.local.CallLogEntity
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecentsScreen(
    networkStatus: NetworkStatus,
    onCallClick: (String, String) -> Unit,
    onInfoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { CallLogRepository(context) }
    val callLogs by repository.allCallLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    
    val noInternetText = stringResource(R.string.no_internet)
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var logToDelete by remember { mutableStateOf<CallLogEntity?>(null) }

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
            items(callLogs, key = { it.id }) { log ->
                RecentCallItem(
                    log = log,
                    isExpanded = expandedLogId == log.id,
                    networkStatus = networkStatus,
                    onExpandClick = {
                        expandedLogId = if (expandedLogId == log.id) null else log.id
                    },
                    onCallClick = { 
                        if (networkStatus == NetworkStatus.Unavailable) {
                            ToastUtils.show(context, noInternetText, Toast.LENGTH_SHORT)
                        } else {
                            onCallClick(log.email, log.name ?: log.email)
                        }
                    },
                    onDeleteClick = { logToDelete = log },
                    onInfoClick = { onInfoClick(log.email, log.name) }
                )
            }
        }
    }

    if (logToDelete != null) {
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text("Delete Call Log") },
            text = { Text("Are you sure you want to delete this call record?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val log = logToDelete!!
                        logToDelete = null
                        scope.launch { repository.deleteCallLog(log) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecentCallItem(
    log: CallLogEntity,
    isExpanded: Boolean,
    networkStatus: NetworkStatus,
    onExpandClick: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }
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
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onExpandClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = (when (log.type) {
                        "OUTGOING" -> Color(0xFF4CAF50)
                        "INCOMING" -> Color(0xFF2196F3)
                        else -> Color.Red
                    }).copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (log.type) {
                                "OUTGOING" -> Icons.AutoMirrored.Filled.CallMade
                                "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived
                                else -> Icons.AutoMirrored.Filled.CallMissed
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = when (log.type) {
                                "OUTGOING" -> Color(0xFF4CAF50)
                                "INCOMING" -> Color(0xFF2196F3)
                                else -> Color.Red
                            }
                        )
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
                    Text(
                        text = log.email,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                IconButton(
                    onClick = onCallClick,
                    enabled = networkStatus != NetworkStatus.Unavailable
                ) {
                    Icon(
                        Icons.Default.Call, 
                        contentDescription = "Call", 
                        tint = if (networkStatus == NetworkStatus.Unavailable) Color.Gray else Color(0xFF4CAF50)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (log.type) {
                                "OUTGOING" -> Icons.AutoMirrored.Filled.CallMade
                                "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived
                                else -> Icons.AutoMirrored.Filled.CallMissed
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (log.type) {
                                "OUTGOING" -> Color(0xFF4CAF50)
                                "INCOMING" -> Color(0xFF2196F3)
                                else -> Color.Red
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${log.type.lowercase().replaceFirstChar { it.uppercase() }} • $timeString${durationString?.let { " • $it" } ?: ""}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RecentsActionButton(
                            icon = Icons.Default.Call,
                            label = "Call",
                            color = if (networkStatus == NetworkStatus.Unavailable) Color.Gray else Color(0xFF4CAF50),
                            onClick = onCallClick,
                            enabled = networkStatus != NetworkStatus.Unavailable
                        )
                        RecentsActionButton(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            onClick = onDeleteClick
                        )
                        RecentsActionButton(
                            icon = Icons.Default.Info,
                            label = "Info",
                            color = Color(0xFF2196F3),
                            onClick = onInfoClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentsActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = true) { onClick() } // Clickable always true for Toast, but icon visual changes
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = color)
    }
}
