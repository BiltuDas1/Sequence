package com.github.biltudas1.sequence.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.biltudas1.sequence.R
import com.github.biltudas1.sequence.util.AppLogger
import android.content.Intent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.content.FileProvider
import com.github.biltudas1.sequence.ui.theme.Crimson
import com.github.biltudas1.sequence.ui.theme.DarkOrange
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(AppLogger.logs) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppLogger.newLogFlow.collect {
            logs = AppLogger.logs
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear Logs") },
            text = { Text("Are you sure you want to clear all logs? This will delete the underlying log file.") },
            confirmButton = {
                TextButton(onClick = {
                    AppLogger.clearLogs()
                    showDeleteDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Logs") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showDeleteDialog = true
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                    IconButton(onClick = {
                        try {
                            val logFile = AppLogger.getLogFile() ?: return@IconButton
                            if (!logFile.exists()) return@IconButton

                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val exportFileName = "sequence-logs-$timeStamp.log"
                            val cacheDir = File(context.cacheDir, "logs")
                            if (!cacheDir.exists()) cacheDir.mkdirs()
                            
                            val exportFile = File(cacheDir, exportFileName)
                            logFile.copyTo(exportFile, overwrite = true)

                            val contentUri = FileProvider.getUriForFile(
                                context,
                                "com.github.biltudas1.sequence.fileprovider",
                                exportFile
                            )

                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                type = "text/plain"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Logs")
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(logs) { log ->
                    val annotatedLog = buildAnnotatedString {
                        if (log.length >= 23 && log[4] == '-' && log[7] == '-') { // Basic check for timestamp
                            val timestamp = log.take(23)
                            val rest = log.drop(23)
                            withStyle(SpanStyle(color = if (LocalIsDarkTheme.current) DarkOrange else Crimson)) {
                                append(timestamp)
                            }
                            append(rest)
                        } else {
                            append(log)
                        }
                    }
                    Text(
                        text = annotatedLog,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        }
    }
}
