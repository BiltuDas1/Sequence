package com.github.biltudas1.sequence.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SettingsInputComponent
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
import com.github.biltudas1.sequence.ui.components.SettingsCategoryItem
import com.github.biltudas1.sequence.util.NetworkStatus
import com.github.biltudas1.sequence.util.ToastUtils
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    networkStatus: NetworkStatus,
    onBackClick: () -> Unit,
    onWebRTCConfigClick: () -> Unit,
    onAudioQualityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val privacyMode by dataStoreManager.privacyModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val callRingtoneUri by dataStoreManager.callRingtoneUriFlow.collectAsStateWithLifecycle(initialValue = null)
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = ServerConfig())
    val accessToken by dataStoreManager.accessTokenFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val noInternetText = stringResource(R.string.no_internet)
    val defaultRingtoneText = stringResource(R.string.default_ringtone)
    val unknownRingtoneText = stringResource(R.string.unknown_ringtone)
    val selectRingtoneText = stringResource(R.string.select_ringtone)

    val ringtoneTitle = remember(callRingtoneUri, defaultRingtoneText, unknownRingtoneText) {
        if (callRingtoneUri.isNullOrEmpty()) {
            defaultRingtoneText
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(context, Uri.parse(callRingtoneUri))
                ringtone?.getTitle(context) ?: unknownRingtoneText
            } catch (_: Exception) {
                defaultRingtoneText
            }
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            scope.launch {
                dataStoreManager.saveCallRingtoneUri(uri?.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Call Settings") },
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    title = stringResource(R.string.ringtone),
                    description = ringtoneTitle,
                    icon = Icons.Default.MusicNote,
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, selectRingtoneText)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (callRingtoneUri != null) Uri.parse(callRingtoneUri) else null)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        }
                        ringtonePickerLauncher.launch(intent)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Privacy Mode",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "If enabled, only contacts can call you.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = privacyMode,
                            onCheckedChange = { enabled ->
                                if (networkStatus == NetworkStatus.Unavailable) {
                                    ToastUtils.show(context, noInternetText, Toast.LENGTH_SHORT)
                                    return@Switch
                                }
                                scope.launch {
                                    val originalValue = privacyMode
                                    
                                    dataStoreManager.savePrivacyMode(enabled)
                                    
                                    if ((accessToken != null) && serverConfig.isValid()) {
                                        val result = authService.updatePrivacyMode(serverConfig, accessToken!!, enabled)
                                        if (result.isFailure) {
                                            dataStoreManager.savePrivacyMode(originalValue)
                                            ToastUtils.show(context, result.exceptionOrNull()?.message ?: "Failed to update privacy mode", Toast.LENGTH_SHORT)
                                        }
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.onSurface,
                        trailingIconColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}
