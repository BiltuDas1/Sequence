package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.ui.components.ServerConfigDialog
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onWebRTCConfigClick: () -> Unit,
    onDataUsageClick: () -> Unit,
    onAudioQualityClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val scope = rememberCoroutineScope()

    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                SettingsCategoryItem(
                    title = "Server Configuration",
                    description = "Change backend server settings",
                    icon = Icons.Default.Storage,
                    onClick = { showConfigDialog = true }
                )
            }
            item {
                SettingsCategoryItem(
                    title = "WebRTC Configuration",
                    description = "Configure STUN/TURN servers",
                    icon = Icons.Default.SettingsInputComponent,
                    onClick = onWebRTCConfigClick
                )
            }
            item {
                SettingsCategoryItem(
                    title = "Audio Quality",
                    description = "Choose your preferred bitrate",
                    icon = Icons.Default.GraphicEq,
                    onClick = onAudioQualityClick
                )
            }
            item {
                SettingsCategoryItem(
                    title = "Data Usage",
                    description = "View network statistics",
                    icon = Icons.Default.QueryStats,
                    onClick = onDataUsageClick
                )
            }
            item {
                SettingsCategoryItem(
                    title = "About",
                    description = "About Sequence",
                    icon = Icons.Default.Info,
                    onClick = onAboutClick
                )
            }
            item {
                SettingsCategoryItem(
                    title = "Logout",
                    description = "Clear session and local data",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = onLogoutClick
                )
            }
        }
    }

    if (showConfigDialog) {
        ServerConfigDialog(
            config = serverConfig,
            onDismiss = { showConfigDialog = false },
            onSave = {
                scope.launch {
                    dataStoreManager.saveServerConfig(it)
                }
                showConfigDialog = false
            }
        )
    }
}

@Composable
fun SettingsCategoryItem(
    title: String,
    description: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = title, 
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            ) 
        },
        supportingContent = description?.let {
            {
                Text(
                    text = it,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
