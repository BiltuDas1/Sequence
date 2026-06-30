package com.github.biltudas1.sequence.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.components.ServerConfigDialog
import com.github.biltudas1.sequence.ui.components.SettingsCategoryItem
import com.github.biltudas1.sequence.ui.theme.Crimson
import com.github.biltudas1.sequence.ui.theme.DarkOrange
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isServerIncompatible: Boolean,
    networkStatus: NetworkStatus,
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onCallSettingsClick: () -> Unit,
    onDataUsageClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val okHttpClient = remember { OkHttpClient() }
    val authService = remember { AuthService(okHttpClient, dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val updateInterval by dataStoreManager.updateIntervalFlow.collectAsStateWithLifecycle(initialValue = "Daily")
    val appTheme by dataStoreManager.appThemeFlow.collectAsStateWithLifecycle(initialValue = com.github.biltudas1.sequence.data.model.AppTheme.SYSTEM)
    val versionCache by dataStoreManager.versionCacheFlow.collectAsStateWithLifecycle(initialValue = DataStoreManager.VersionCache(null, null, null, 0L))
    val scope = rememberCoroutineScope()

    val serverIncompatibleText = stringResource(R.string.server_incompatible)
    val noInternetText = stringResource(R.string.no_internet)

    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val currentVersion = packageInfo.versionName ?: ""
    val hasUpdate = versionCache.tag?.removePrefix("v") != null && versionCache.tag?.removePrefix("v") != currentVersion.removePrefix("v")
    val updateColor = if (LocalIsDarkTheme.current) DarkOrange else Crimson

    var showConfigDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings") },
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
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isServerIncompatible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = serverIncompatibleText,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
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
                        title = "Call Settings",
                        description = "Configure Calls and Privacy Mode",
                        icon = Icons.Default.Call,
                        onClick = onCallSettingsClick
                    )
                }
                item {
                    SettingsCategoryItem(
                        title = "Theme",
                        description = "Theme: ${appTheme.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        icon = Icons.Default.DarkMode,
                        onClick = { showThemeDialog = true }
                    )
                }
                item {
                    SettingsCategoryItem(
                        title = "App Updates",
                        description = "Check for updates: $updateInterval",
                        icon = Icons.Default.SystemUpdate,
                        onClick = { 
                            if (networkStatus == NetworkStatus.Unavailable) {
                                ToastUtils.show(context, noInternetText, Toast.LENGTH_SHORT)
                            } else {
                                showUpdateIntervalDialog = true 
                            }
                        }
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
                        onClick = onAboutClick,
                        trailingContent = if (hasUpdate) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(updateColor, CircleShape)
                                )
                            }
                        } else null
                    )
                }
                item {
                    SettingsCategoryItem(
                        title = "Logout",
                        description = "Clear session and local data",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout? This will clear your session and local data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                ) {
                    Text("Logout", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfigDialog) {
        ServerConfigDialog(
            config = serverConfig,
            authService = authService,
            onDismiss = { showConfigDialog = false },
            onSave = {
                scope.launch {
                    dataStoreManager.saveServerConfig(it)
                }
                showConfigDialog = false
            }
        )
    }

    if (showUpdateIntervalDialog) {
        val options = listOf("Daily", "Weekly", "Never")
        AlertDialog(
            onDismissRequest = { showUpdateIntervalDialog = false },
            title = { Text("App Updates") },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        dataStoreManager.saveUpdateInterval(option)
                                        showUpdateIntervalDialog = false
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = updateInterval == option,
                                onClick = {
                                    scope.launch {
                                        dataStoreManager.saveUpdateInterval(option)
                                        showUpdateIntervalDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdateIntervalDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showThemeDialog) {
        val themes = com.github.biltudas1.sequence.data.model.AppTheme.values()
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    themes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        dataStoreManager.saveAppTheme(theme)
                                        showThemeDialog = false
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appTheme == theme,
                                onClick = {
                                    scope.launch {
                                        dataStoreManager.saveAppTheme(theme)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = theme.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
