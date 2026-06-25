package com.github.biltudas1.sequence.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.github.biltudas1.sequence.ui.theme.DeepGreen
import com.github.biltudas1.sequence.ui.utils.PermissionUtils
import com.github.biltudas1.sequence.ui.theme.LocalIsDarkTheme
import timber.log.Timber

@Composable
fun PermissionGatewayScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    var hasMicPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasPhoneStatePermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var isBatteryOptimizedIgnored by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }

    val allGranted = hasMicPermission && hasPhoneStatePermission && hasNotificationPermission && isBatteryOptimizedIgnored

    // Refresh when returning to foreground
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                Timber.d("PermissionGatewayScreen: Refreshing permission states on resume")
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasPhoneStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                isBatteryOptimizedIgnored = PermissionUtils.isIgnoringBatteryOptimizations(context)
                
                Timber.d("Permissions: Mic=$hasMicPermission, Phone=$hasPhoneStatePermission, Notifications=$hasNotificationPermission, BatteryOptimizedIgnored=$isBatteryOptimizedIgnored")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Timber.i("Microphone permission result: $isGranted")
        hasMicPermission = isGranted
    }
    
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Timber.i("Phone State permission result: $isGranted")
        hasPhoneStatePermission = isGranted
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Timber.i("Notification permission result: $isGranted")
        hasNotificationPermission = isGranted
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Permissions Required",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Please grant the following permissions to ensure the app works correctly.",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            PermissionItem(
                title = "Microphone",
                description = "Required to make and receive voice calls.",
                icon = Icons.Default.Mic,
                isGranted = hasMicPermission,
                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            PermissionItem(
                title = "Phone State",
                description = "Required to handle calls and manage audio focus.",
                icon = Icons.Default.PhoneAndroid,
                isGranted = hasPhoneStatePermission,
                onClick = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notifications",
                    description = "Required to notify you of incoming calls.",
                    icon = Icons.Default.Notifications,
                    isGranted = hasNotificationPermission,
                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            PermissionItem(
                title = "Battery Optimization",
                description = "App must be set to 'Unrestricted' to receive calls reliably in the background.",
                icon = Icons.Default.BatteryAlert,
                isGranted = isBatteryOptimizedIgnored,
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { 
                    if (allGranted) {
                        Timber.i("All permissions granted. Continuing...")
                        onAllPermissionsGranted()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = allGranted
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color.Green.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isGranted && LocalIsDarkTheme.current -> Color.Green
                    isGranted && !LocalIsDarkTheme.current -> DeepGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isGranted && LocalIsDarkTheme.current -> Color.Green
                        isGranted && !LocalIsDarkTheme.current -> DeepGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
            
            if (!isGranted) {
                TextButton(onClick = onClick) {
                    Text("Grant", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = if (LocalIsDarkTheme.current) Color.Green else DeepGreen
                )
            }
        }
    }
}
