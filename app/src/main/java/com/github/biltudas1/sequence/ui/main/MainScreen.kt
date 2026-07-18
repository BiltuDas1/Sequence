package com.github.biltudas1.sequence.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.repository.CallLogRepository
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.model.UserData
import com.github.biltudas1.sequence.ui.call.DialerScreen
import com.github.biltudas1.sequence.ui.contacts.ContactsScreen
import com.github.biltudas1.sequence.ui.theme.Crimson
import com.github.biltudas1.sequence.ui.theme.DarkOrange
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import com.github.biltudas1.sequence.util.NetworkStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServerIncompatible: Boolean,
    networkStatus: NetworkStatus,
    onContactClick: (UserData) -> Unit,
    onDialerCallClick: (String) -> Unit,
    onInfoClick: (String, String?, String?) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val lastSelectedTab by dataStoreManager.lastSelectedTabFlow.collectAsStateWithLifecycle(initialValue = 0)
    val versionCache by dataStoreManager.versionCacheFlow.collectAsStateWithLifecycle(initialValue = DataStoreManager.VersionCache(null, null, null, 0L))
    
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val currentVersion = packageInfo.versionName ?: ""
    val hasUpdate = versionCache.tag?.removePrefix("v") != null && versionCache.tag?.removePrefix("v") != currentVersion.removePrefix("v")
    val updateColor = if (LocalIsDarkTheme.current) DarkOrange else Crimson

    var showAddContactDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (lastSelectedTab) {
                            0 -> "Dialer"
                            1 -> "Recents"
                            else -> "Contacts"
                        }
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                            if (hasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(updateColor, CircleShape)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 1.dp, y = 1.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = lastSelectedTab == 0,
                    onClick = { scope.launch { dataStoreManager.saveLastSelectedTab(0) } },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Dialer") },
                    label = { Text("Dialer") }
                )
                NavigationBarItem(
                    selected = lastSelectedTab == 1,
                    onClick = { scope.launch { dataStoreManager.saveLastSelectedTab(1) } },
                    icon = { Icon(Icons.Default.History, contentDescription = "Recents") },
                    label = { Text("Recents") }
                )
                NavigationBarItem(
                    selected = lastSelectedTab == 2,
                    onClick = { scope.launch { dataStoreManager.saveLastSelectedTab(2) } },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Contacts") },
                    label = { Text("Contacts") }
                )
            }
        },
        floatingActionButton = {
            if (lastSelectedTab == 2 && !isServerIncompatible) {
                FloatingActionButton(
                    onClick = { showAddContactDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isServerIncompatible) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val serverIncompatibleText = androidx.compose.ui.res.stringResource(com.github.biltudas1.sequence.R.string.server_incompatible)
                    Text(
                        text = serverIncompatibleText,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (lastSelectedTab) {
                    0 -> DialerScreen(
                        networkStatus = networkStatus,
                        onCallClick = onDialerCallClick
                    )
                    1 -> RecentsScreen(
                        networkStatus = networkStatus,
                        onCallClick = { email, name ->
                            onContactClick(UserData(id = "", email = email, first_name = name, last_name = "", created_at = ""))
                        },
                        onInfoClick = { email, name ->
                            onInfoClick(email, name, null)
                        }
                    )
                    2 -> ContactsScreen(
                        isServerIncompatible = isServerIncompatible,
                        networkStatus = networkStatus,
                        onContactClick = onContactClick,
                        onInfoClick = { contact ->
                            onInfoClick(contact.email, contact.first_name, contact.last_name)
                        },
                        onSettingsClick = onSettingsClick,
                        showAddDialogExternally = showAddContactDialog,
                        onAddDialogDismiss = { showAddContactDialog = false }
                    )
                }
            }
        }
    }
}
