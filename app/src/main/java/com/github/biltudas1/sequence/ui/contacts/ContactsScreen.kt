package com.github.biltudas1.sequence.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import com.github.biltudas1.sequence.data.ContactRepository
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.ServerConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.data.remote.model.UserData
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
    
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<UserData?>(null) }
    var emailToAdd by remember { mutableStateOf("") }

    val refreshContacts = {
        scope.launch {
            if (accessToken != null && serverConfig.isValid()) {
                isLoading = true
                val result = repository.refreshContacts(serverConfig, accessToken!!)
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Sync failed"
                    // Only show toast for real errors, not configuration issues
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
                title = { Text("Contacts", color = Color.White) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
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
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onClick = { onContactClick(contact) },
                            onDelete = {
                                contactToDelete = contact
                            }
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
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${contact.first_name ?: ""} ${contact.last_name ?: ""}".trim().ifEmpty { contact.email },
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (contact.first_name != null) {
                    Text(text = contact.email, fontSize = 12.sp, color = TextSecondary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
