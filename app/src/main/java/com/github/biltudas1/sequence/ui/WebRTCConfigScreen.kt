package com.github.biltudas1.sequence.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.IceServerConfig
import com.github.biltudas1.sequence.data.model.WebRTCConfig
import com.github.biltudas1.sequence.ui.theme.SurfaceContainerHigh
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebRTCConfigScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val webrtcConfig by dataStoreManager.webrtcConfigFlow.collectAsStateWithLifecycle(initialValue = WebRTCConfig())
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val tabs = listOf("STUN Servers", "TURN Servers")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("WebRTC Configuration") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val currentServers = if (selectedTabIndex == 0) webrtcConfig.stunServers else webrtcConfig.turnServers
            
            if (currentServers.isEmpty()) {
                Text(
                    text = "No ${tabs[selectedTabIndex]} configured",
                    modifier = Modifier.align(Alignment.Center),
                    color = TextSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentServers) { server ->
                        IceServerItem(
                            server = server,
                            onDelete = {
                                scope.launch {
                                    if (selectedTabIndex == 0) {
                                        if (webrtcConfig.stunServers.size <= 1) {
                                            Toast.makeText(context, "At least one STUN server is mandatory", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val newStun = webrtcConfig.stunServers.filter { it != server }
                                        dataStoreManager.saveWebRTCConfig(webrtcConfig.copy(stunServers = newStun))
                                    } else {
                                        val newTurn = webrtcConfig.turnServers.filter { it != server }
                                        dataStoreManager.saveWebRTCConfig(webrtcConfig.copy(turnServers = newTurn))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val type = if (selectedTabIndex == 0) IceServerType.STUN else IceServerType.TURN
        AddIceServerDialog(
            type = type,
            onDismiss = { showAddDialog = false },
            onAdd = { url, user, cred ->
                scope.launch {
                    val newServer = IceServerConfig(url, user, cred)
                    if (type == IceServerType.STUN) {
                        dataStoreManager.saveWebRTCConfig(
                            webrtcConfig.copy(stunServers = webrtcConfig.stunServers + newServer)
                        )
                    } else {
                        dataStoreManager.saveWebRTCConfig(
                            webrtcConfig.copy(turnServers = webrtcConfig.turnServers + newServer)
                        )
                    }
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun IceServerItem(
    server: IceServerConfig,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.url, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (!server.username.isNullOrBlank()) {
                    Text(text = "User: ${server.username}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AddIceServerDialog(
    type: IceServerType,
    onDismiss: () -> Unit,
    onAdd: (String, String?, String?) -> Unit
) {
    var url by remember { mutableStateOf(if (type == IceServerType.STUN) "stun:" else "turn:") }
    var username by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${type.name} Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (type == IceServerType.TURN) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        label = { Text("Credential (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url, username.ifBlank { null }, credential.ifBlank { null }) },
                enabled = url.isNotBlank() && url != "stun:" && url != "turn:"
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class IceServerType { STUN, TURN }
