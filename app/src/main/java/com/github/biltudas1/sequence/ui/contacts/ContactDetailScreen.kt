package com.github.biltudas1.sequence.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.local.CallLogEntity
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.repository.CallLogRepository
import com.github.biltudas1.sequence.data.repository.ContactRepository
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ContactDetailScreen(
    email: String,
    firstName: String?,
    lastName: String?,
    networkStatus: NetworkStatus,
    onBackClick: () -> Unit,
    onCallClick: (String, String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val contactRepository = remember { ContactRepository(context, authService) }
    val callLogRepository = remember { CallLogRepository(context) }

    val callLogs by callLogRepository.getCallLogsByEmail(email).collectAsStateWithLifecycle(initialValue = emptyList())
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
    val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = null)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedLogIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val isSelectionMode = selectedLogIds.isNotEmpty()
    val fullName = "${firstName ?: ""} ${lastName ?: ""}".trim().ifEmpty { email }

    BackHandler(enabled = isSelectionMode) {
        selectedLogIds = emptySet()
    }

    val groupedLogs = remember(callLogs) {
        callLogs.groupBy { log ->
            val calendar = Calendar.getInstance()
            val today = calendar.get(Calendar.DAY_OF_YEAR)
            val yesterday = today - 1
            val year = calendar.get(Calendar.YEAR)

            calendar.timeInMillis = log.timestamp
            val logDay = calendar.get(Calendar.DAY_OF_YEAR)
            val logYear = calendar.get(Calendar.YEAR)

            when {
                (today == logDay) && (year == logYear) -> "Today"
                (yesterday == logDay) && (year == logYear) -> "Yesterday"
                year == logYear -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(log.timestamp))
                else -> SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(log.timestamp))
            }
        }
    }

    Scaffold(
        topBar = {
            ContactDetailTopBar(
                isSelectionMode = isSelectionMode,
                selectedCount = selectedLogIds.size,
                isAllSelected = selectedLogIds.size == callLogs.size && callLogs.isNotEmpty(),
                fullName = fullName,
                email = email,
                onBackClick = onBackClick,
                onClearSelection = { selectedLogIds = emptySet() },
                onToggleSelectAll = { checked ->
                    selectedLogIds = if (checked) callLogs.map { it.id }.toSet() else emptySet()
                },
                onClearAllClick = { showClearAllDialog = true },
                showClearAll = callLogs.isNotEmpty()
            )
        },
        bottomBar = {
            ContactDetailBottomBar(
                isSelectionMode = isSelectionMode,
                networkStatus = networkStatus,
                onCallClick = { onCallClick(email, fullName) },
                onDeleteClick = { showDeleteDialog = true },
                onBatchRemoveClick = { showBatchDeleteDialog = true }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (callLogs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No call history", color = TextSecondary)
                    }
                }
            } else {
                groupedLogs.forEach { (date, logs) ->
                    item(key = "header_$date") {
                        Text(
                            text = date,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                bottom = 8.dp,
                                start = 8.dp,
                                top = if (date == groupedLogs.keys.first()) 0.dp else 16.dp
                            )
                        )
                    }
                    items(logs, key = { it.id }) { log ->
                        val index = logs.indexOf(log)
                        ContactCallLogItem(
                            log = log,
                            isSelected = selectedLogIds.contains(log.id),
                            isSelectionMode = isSelectionMode,
                            isFirst = index == 0,
                            isLast = index == logs.size - 1,
                            onToggleSelection = {
                                selectedLogIds = if (selectedLogIds.contains(log.id)) {
                                    selectedLogIds - log.id
                                } else {
                                    selectedLogIds + log.id
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedLogIds = setOf(log.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Contact") },
            text = { Text("Are you sure you want to remove $email from your contacts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            if ((accessToken != null) && (serverConfig != null)) {
                                val result = contactRepository.removeContact(serverConfig!!, accessToken!!, email)
                                if (result.isSuccess) {
                                    onDeleted()
                                } else {
                                    ToastUtils.show(
                                        context,
                                        result.exceptionOrNull()?.message ?: "Failed to remove",
                                        android.widget.Toast.LENGTH_SHORT
                                    )
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Remove Call Logs") },
            text = { Text("Are you sure you want to remove ${selectedLogIds.size} selected call logs?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedLogIds.toList()
                        selectedLogIds = emptySet()
                        showBatchDeleteDialog = false
                        scope.launch {
                            callLogRepository.deleteCallLogsByIds(idsToDelete)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear Call History") },
            text = { Text("Are you sure you want to clear all call logs for $fullName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        scope.launch {
                            callLogRepository.deleteCallLogsByEmail(email)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    isAllSelected: Boolean,
    fullName: String,
    email: String,
    onBackClick: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelectAll: (Boolean) -> Unit,
    onClearAllClick: () -> Unit,
    showClearAll: Boolean
) {
    if (isSelectionMode) {
        TopAppBar(
            title = { Text(text = "$selectedCount selected") },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection")
                }
            },
            actions = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text(text = "All", style = MaterialTheme.typography.bodyMedium)
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = onToggleSelectAll
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    } else {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = fullName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = email,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (showClearAll) {
                    IconButton(onClick = onClearAllClick) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all logs")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
private fun ContactDetailBottomBar(
    isSelectionMode: Boolean,
    networkStatus: NetworkStatus,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBatchRemoveClick: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailBottomAction(
                    icon = Icons.Default.Delete,
                    label = "Remove",
                    color = MaterialTheme.colorScheme.error,
                    onClick = onBatchRemoveClick
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailBottomAction(
                    icon = Icons.Default.Call,
                    label = "Call",
                    color = if (networkStatus == NetworkStatus.Unavailable) Color.Gray else Color(0xFF4CAF50),
                    onClick = onCallClick
                )
                DetailBottomAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    onClick = onDeleteClick
                )
            }
        }
    }
}

@Composable
private fun DetailBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ContactCallLogItem(
    log: CallLogEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val durationString = remember(log.duration) {
        if (log.duration != null && log.duration > 0) {
            val seconds = log.duration / 1000
            val mins = seconds / 60
            val secs = seconds % 60
            if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        } else null
    }

    val typeString = when (log.type) {
        "MISSED" -> "Missed call"
        "INCOMING" -> "Incoming call"
        "OUTGOING" -> "Outgoing call"
        else -> log.type
    }

    val fullDescription = if (durationString != null) "$typeString, $durationString" else typeString

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(durationMillis = 100),
        label = "SelectionBackground"
    )

    val currentOnToggleSelection by rememberUpdatedState(onToggleSelection)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentIsSelectionMode by rememberUpdatedState(isSelectionMode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = if (isFirst) 24.dp else 0.dp,
                    topEnd = if (isFirst) 24.dp else 0.dp,
                    bottomStart = if (isLast) 24.dp else 0.dp,
                    bottomEnd = if (isLast) 24.dp else 0.dp
                )
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (currentIsSelectionMode) currentOnToggleSelection() },
                    onLongPress = { currentOnLongClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(100)
                ) + fadeIn(animationSpec = tween(100)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(100)
                ) + fadeOut(animationSpec = tween(100))
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Icon(
                imageVector = when (log.type) {
                    "OUTGOING" -> Icons.AutoMirrored.Filled.CallMade
                    "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMissed
                },
                contentDescription = null,
                tint = when (log.type) {
                    "OUTGOING" -> Color(0xFF4CAF50)
                    "INCOMING" -> Color(0xFF2196F3)
                    else -> Color.Red
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeString,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (log.type == "MISSED") Color.Red else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (log.type == "MISSED") {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = fullDescription,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        if (!isLast) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
    }
}
