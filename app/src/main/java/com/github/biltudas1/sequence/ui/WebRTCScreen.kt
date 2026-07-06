package com.github.biltudas1.sequence.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.components.CallScreenContent
import com.github.biltudas1.sequence.webrtc.CallManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient

@Composable
fun WebRTCScreen(
    roomId: String,
    serverUrl: String,
    callerName: String,
    callerEmail: String,
    isExternal: Boolean,
    creationTime: Long? = null,
    accessToken: String?,
    onCallStopped: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)

    val isMuted by CallManager.isMuted
    val isSpeakerOn by CallManager.isSpeakerOn
    val hasPeerJoined by CallManager.hasPeerJoined
    val isRemoteBusy by CallManager.isRemoteBusy
    val isSignalingConnected by CallManager.isSignalingConnected

    LaunchedEffect(roomId) {
        if (CallManager.activeRoomId == null) {
            CallManager.initCall(context, roomId, serverUrl, callerName, callerEmail, isExternal, accessToken, creationTime)
        }
    }

    DisposableEffect(Unit) {
        val listener = {
            onCallStopped()
        }
        CallManager.onCallEnded = listener
        onDispose {
            CallManager.onCallEnded = null
        }
    }

    BackHandler {
        // Just navigate back, don't end the call
        onCallStopped()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        CallScreenContent(
            roomId = roomId,
            callerName = callerName,
            callerEmail = callerEmail,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            onMuteToggle = { CallManager.toggleMute(context) },
            onSpeakerToggle = { CallManager.toggleSpeaker(context) },
            onCallStopped = {
                // Immediately terminate local state and navigate back
                CallManager.terminateCall(context)
                
                // Then try to notify the server in the background
                scope.launch {
                    try {
                        if (accessToken != null && serverConfig != null) {
                            authService.endVoiceCall(serverConfig!!, accessToken, roomId)
                        }
                    } catch (e: Exception) {
                        Log.e("WebRTCScreen", "Error notifying server about end call", e)
                    }
                }
            },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding(),
            statusMessage = when {
                hasPeerJoined -> "Connected"
                isRemoteBusy -> "On another call"
                isSignalingConnected -> "Ringing..."
                else -> "Connecting..."
            }
        )
    }
}
