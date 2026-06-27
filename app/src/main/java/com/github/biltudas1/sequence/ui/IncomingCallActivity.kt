package com.github.biltudas1.sequence.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.MainActivity
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.fcm.MyFirebaseMessagingService
import com.github.biltudas1.sequence.ui.theme.SequenceTheme
import com.github.biltudas1.sequence.ui.utils.CallRingtonePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlin.math.sqrt

class IncomingCallActivity : ComponentActivity() {
    
    private var ringtone: Ringtone? = null
    
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyFirebaseMessagingService.ACTION_CANCEL_CALL) {
                Timber.d("Received cancel broadcast, finishing activity")
                finishAndRemoveTask()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("onNewIntent: cancel=${intent.getBooleanExtra("cancel", false)}")
        if (intent.getBooleanExtra("cancel", false)) {
            stopRingtone()
            finishAndRemoveTask()
        }
    }

    private fun stopRingtone() {
        try {
            CallRingtonePlayer.stop(this)
            ringtone = null
        } catch (e: Exception) {
            Timber.e(e, "Error stopping ringtone")
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
        stopRingtone()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate IncomingCallActivity")
        
        // Register cancel receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, IntentFilter(MyFirebaseMessagingService.ACTION_CANCEL_CALL), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cancelReceiver, IntentFilter(MyFirebaseMessagingService.ACTION_CANCEL_CALL))
        }
        
        if (intent.getBooleanExtra("cancel", false)) {
            Timber.d("Finish immediately due to cancel flag")
            finishAndRemoveTask()
            return
        }

        // Ensure audio is routed to speaker
        CallRingtonePlayer.ensureSpeaker(this)
        // Ensure it's playing (covers cases where activity starts after service)
        CallRingtonePlayer.start(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val roomId = intent.getStringExtra("roomId") ?: ""
        val callerName = intent.getStringExtra("callerName") ?: "Someone"
        val callerEmail = intent.getStringExtra("callerEmail") ?: ""

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dataStoreManager = remember { DataStoreManager.getInstance(context) }
            val appTheme by dataStoreManager.appThemeFlow.collectAsStateWithLifecycle(initialValue = com.github.biltudas1.sequence.data.model.AppTheme.SYSTEM)

            SequenceTheme(appTheme = appTheme) {
                IncomingCallContent(
                    callerName = callerName,
                    callerEmail = callerEmail,
                    onAccept = {
                        stopRingtone()
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)
                        val launchIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("roomId", roomId)
                            putExtra("callerName", callerName)
                            putExtra("callerEmail", callerEmail)
                        }
                        startActivity(launchIntent)
                        finishAndRemoveTask()
                    },
                    onReject = {
                        stopRingtone()
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(MyFirebaseMessagingService.CALL_NOTIFICATION_ID)
                        val dataStoreManager = DataStoreManager.getInstance(applicationContext)
                        val authService = AuthService(OkHttpClient(), dataStoreManager)
                        val repository = com.github.biltudas1.sequence.data.CallLogRepository(applicationContext)
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.markAsMissed(roomId)
                            val config = dataStoreManager.serverConfigFlow.firstOrNull()
                            val token = dataStoreManager.accessTokenFlow.firstOrNull()
                            if (config != null && token != null) {
                                authService.endVoiceCall(config, token, roomId)
                            }
                        }
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }
}

@Composable
fun IncomingCallContent(
    callerName: String,
    callerEmail: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = callerName,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            if (callerEmail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = callerEmail,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Bottom Drag Buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp, start = 60.dp, end = 60.dp)
        ) {
            FixedButtonDraggableAction(
                icon = Icons.Default.Call,
                iconColor = Color(0xFF00C853),
                onTrigger = onAccept,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            FixedButtonDraggableAction(
                icon = Icons.Default.CallEnd,
                iconColor = Color(0xFFF44336),
                onTrigger = onReject,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        
        Text(
            text = "Swipe to answer or reject",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}

@Composable
fun FixedButtonDraggableAction(
    icon: ImageVector,
    iconColor: Color,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val triggerDistance = with(density) { 100.dp.toPx() } 
    
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var isTouching by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }
    
    val distance = sqrt(currentOffset.x * currentOffset.x + currentOffset.y * currentOffset.y)
    val progress = (distance / triggerDistance).coerceIn(0f, 1f)
    
    val backgroundScale = 3.0f - (2.0f * progress)
    val backgroundAlpha = if (isTouching && !hasTriggered) 0.35f else 0f

    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(backgroundScale)
                .alpha(backgroundAlpha)
                .background(iconColor, CircleShape)
        )

        Surface(
            modifier = Modifier
                .size(72.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        isTouching = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Move) {
                                if (hasTriggered) continue
                                
                                val change = event.changes.first()
                                val totalChange = change.position - change.previousPosition
                                currentOffset += totalChange
                                
                                val newDistance = sqrt(currentOffset.x * currentOffset.x + currentOffset.y * currentOffset.y)
                                if (newDistance >= triggerDistance && !hasTriggered) {
                                    hasTriggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrigger()
                                }
                            } else if (event.type == PointerEventType.Release) {
                                isTouching = false
                                currentOffset = Offset.Zero
                                break
                            }
                        }
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, iconColor.copy(alpha = 0.6f)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = iconColor
                )
            }
        }
    }
}
