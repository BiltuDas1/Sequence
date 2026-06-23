package com.github.biltudas1.sequence.ui.contacts

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.ContactRepository
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.remote.model.UserData
import com.github.biltudas1.sequence.ui.theme.Crimson
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import com.github.biltudas1.sequence.ui.theme.SurfaceContainerHigh
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (UserData) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val repository = remember { ContactRepository(context, authService) }
    
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = null)
    val contacts by repository.contactsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val versionCache by dataStoreManager.versionCacheFlow.collectAsStateWithLifecycle(initialValue = Triple(null, null, 0L))

    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val currentVersion = packageInfo.versionName ?: ""
    val hasUpdate = versionCache.first?.removePrefix("v") != null && versionCache.first?.removePrefix("v") != currentVersion.removePrefix("v")
    val updateColor = if (LocalIsDarkTheme.current) Color.Yellow else Crimson

    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<UserData?>(null) }
    var emailToAdd by remember { mutableStateOf("") }
    
    var expandedContactId by remember { mutableStateOf<String?>(null) }

    val refreshContacts = {
        scope.launch {
            if (accessToken != null && serverConfig.isValid()) {
                isLoading = true
                val result = repository.refreshContacts(serverConfig, accessToken!!)
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Sync failed"
                    if (!msg.contains("resolve host", ignoreCase = true)) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                isLoading = false
            }
        }
    }

    LaunchedEffect(accessToken, serverConfig) {
        if (contacts.isEmpty()) {
            refreshContacts()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contacts") },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!serverConfig.isValid()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Server not configured",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onSettingsClick) {
                        Text("Go to Settings")
                    }
                }
            } else if (isLoading && contacts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (contacts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No contacts yet",
                        color = TextSecondary
                    )
                    TextButton(onClick = { refreshContacts() }) {
                        Text("Refresh")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactItem(
                            contact = contact,
                            isExpanded = expandedContactId == contact.id,
                            onExpandClick = {
                                expandedContactId = if (expandedContactId == contact.id) null else contact.id
                            },
                            onCallClick = { onContactClick(contact) },
                            onDeleteClick = { contactToDelete = contact },
                            onInfoClick = { /* TODO: Info screen */ }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Contact") },
            text = {
                OutlinedTextField(
                    value = emailToAdd,
                    onValueChange = { emailToAdd = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (accessToken != null && emailToAdd.isNotBlank()) {
                                val result = repository.addContact(serverConfig, accessToken!!, emailToAdd)
                                if (result.isSuccess) {
                                    showAddDialog = false
                                    emailToAdd = ""
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed to add", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Remove Contact") },
            text = { Text("Are you sure you want to remove ${contactToDelete?.email} from your contacts?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val contact = contactToDelete!!
                        contactToDelete = null
                        scope.launch {
                            if (accessToken != null) {
                                val result = repository.removeContact(serverConfig, accessToken!!, contact.email)
                                if (result.isFailure) {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed to remove", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ContactItem(
    contact: UserData,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // Snap expand (spring)
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
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${contact.first_name ?: ""} ${contact.last_name ?: ""}".trim().ifEmpty { contact.email },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp
                    )
                    Text(
                        text = contact.email,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = EnterTransition.None, // snappier expand
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) // smooth shrink
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactActionButton(
                            icon = Icons.Default.Call,
                            label = "Call",
                            color = Color(0xFF4CAF50),
                            onClick = onCallClick
                        )
                        ContactActionButton(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            onClick = onDeleteClick
                        )
                        ContactActionButton(
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
fun ContactActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
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
