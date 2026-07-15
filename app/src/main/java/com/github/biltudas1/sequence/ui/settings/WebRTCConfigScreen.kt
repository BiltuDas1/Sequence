package com.github.biltudas1.sequence.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.IceServerConfig
import com.github.biltudas1.sequence.data.model.WebRTCConfig
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebRTCConfigScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val webrtcConfig by dataStoreManager.webrtcConfigFlow.collectAsStateWithLifecycle(initialValue = WebRTCConfig())
    val scope = rememberCoroutineScope()

    val tabs = listOf("STUN Servers", "TURN Servers")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val selectedTabIndex = pagerState.currentPage

    var serverToEdit by remember { mutableStateOf<IceServerConfig?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                            onClick = { 
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                val currentServers = if (page == 0) webrtcConfig.stunServers else webrtcConfig.turnServers
                
                if (currentServers.isEmpty()) {
                    Text(
                        text = "No ${tabs[page]} configured",
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
                                onInfoClick = {
                                    serverToEdit = server
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || serverToEdit != null) {
        val type = if (selectedTabIndex == 0) IceServerType.STUN else IceServerType.TURN
        IceServerDialog(
            type = type,
            existingServer = serverToEdit,
            onDismiss = { 
                showAddDialog = false
                serverToEdit = null
            },
            onSave = { url, user, cred ->
                scope.launch {
                    val newServer = IceServerConfig(url, user, cred)
                    val updatedConfig = if (serverToEdit != null) {
                        // Update
                        if (type == IceServerType.STUN) {
                            webrtcConfig.copy(stunServers = webrtcConfig.stunServers.map { if (it == serverToEdit) newServer else it })
                        } else {
                            webrtcConfig.copy(turnServers = webrtcConfig.turnServers.map { if (it == serverToEdit) newServer else it })
                        }
                    } else {
                        // Add
                        if (type == IceServerType.STUN) {
                            webrtcConfig.copy(stunServers = webrtcConfig.stunServers + newServer)
                        } else {
                            webrtcConfig.copy(turnServers = webrtcConfig.turnServers + newServer)
                        }
                    }
                    dataStoreManager.saveWebRTCConfig(updatedConfig)
                    showAddDialog = false
                    serverToEdit = null
                }
            },
            onDelete = if (serverToEdit != null) {
                {
                    scope.launch {
                        if (type == IceServerType.STUN && webrtcConfig.stunServers.size <= 1) {
                            Toast.makeText(context, "At least one STUN server is mandatory", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedConfig = if (type == IceServerType.STUN) {
                                webrtcConfig.copy(stunServers = webrtcConfig.stunServers.filter { it != serverToEdit })
                            } else {
                                webrtcConfig.copy(turnServers = webrtcConfig.turnServers.filter { it != serverToEdit })
                            }
                            dataStoreManager.saveWebRTCConfig(updatedConfig)
                            serverToEdit = null
                        }
                    }
                }
            } else null
        )
    }
}

@Composable
fun IceServerItem(
    server: IceServerConfig,
    onInfoClick: () -> Unit
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
            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Server Info", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun IceServerDialog(
    type: IceServerType,
    existingServer: IceServerConfig? = null,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var url by remember { mutableStateOf(existingServer?.url ?: if (type == IceServerType.STUN) "stun:" else "turn:") }
    var username by remember { mutableStateOf(existingServer?.username ?: "") }
    var credential by remember { mutableStateOf(existingServer?.credential ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingServer != null) "Edit ${type.name} Server" else "Add ${type.name} Server") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                
                TextButton(
                    onClick = { onSave(url, username.ifBlank { null }, credential.ifBlank { null }) },
                    enabled = url.isNotBlank() && url != "stun:" && url != "turn:"
                ) {
                    Text(if (existingServer != null) "Update" else "Add")
                }
            }
        },
        dismissButton = null
    )
}

enum class IceServerType { STUN, TURN }
