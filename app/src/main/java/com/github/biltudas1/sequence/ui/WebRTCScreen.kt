package com.github.biltudas1.sequence.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import com.github.biltudas1.sequence.data.model.WebRTCConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.components.CallScreenContent
import com.github.biltudas1.sequence.webrtc.SignalingClient
import com.github.biltudas1.sequence.webrtc.WebRTCClient
import com.github.biltudas1.sequence.ui.theme.SurfaceDim
import com.github.biltudas1.sequence.ui.utils.CallAudioManager
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.webrtc.*

@Composable
fun WebRTCScreen(
    roomId: String,
    serverUrl: String,
    callerName: String,
    callerEmail: String,
    isExternal: Boolean,
    accessToken: String?,
    onCallStopped: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val applicationScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    
    val dataStoreManager = remember { DataStoreManager(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
    
    val callStatusManager = remember { CallStatusManager(context) }
    var isRemoteBusy by remember { mutableStateOf(false) }

    var signalingClient by remember { mutableStateOf<SignalingClient?>(null) }
    var isLeaving by remember { mutableStateOf(false) }
    var isSignalingConnected by remember { mutableStateOf(false) }
    var hasPeerJoined by remember { mutableStateOf(false) }
    
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    
    val audioManager = remember { CallAudioManager(context) }

    val safeOnCallStopped = {
        if (!isLeaving) {
            isLeaving = true
            Log.i("WebRTCScreen", "Closing call screen (safeOnCallStopped). Room: $roomId")
            audioManager.stopAny()
            scope.launch(Dispatchers.Main) {
                onCallStopped()
            }
        }
    }

    val webRTCClient = remember(context) {
        WebRTCClient(context, object : WebRTCClient.WebRTCListener {
            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient?.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onSdpCreated(description: SessionDescription) {
                if (description.type == SessionDescription.Type.OFFER) {
                    signalingClient?.sendOffer(description.description)
                } else if (description.type == SessionDescription.Type.ANSWER) {
                    signalingClient?.sendAnswer(description.description)
                }
            }

            override fun onDataUsageCollected(stunSent: Long, stunRecv: Long, turnSent: Long, turnRecv: Long) {
                applicationScope.launch {
                    dataStoreManager.addDataUsage(stunSent, stunRecv, turnSent, turnRecv)
                    delay(1000)
                    applicationScope.cancel()
                }
            }
        })
    }

    // Busy detection and focus management
    LaunchedEffect(isRemoteBusy) {
        if (isRemoteBusy && !hasPeerJoined) {
            audioManager.startBusy()
        }
    }

    // Logic for Ring Waiting (after 2s) and Ringback
    LaunchedEffect(isSignalingConnected, hasPeerJoined, isRemoteBusy) {
        if (hasPeerJoined || isRemoteBusy) {
            if (hasPeerJoined) {
                audioManager.stopAny()
                // Gain audio focus when call starts
                callStatusManager.requestCallAudioFocus()
            }
            return@LaunchedEffect
        }

        if (!isSignalingConnected) {
            delay(2000)
            if (!isSignalingConnected && !hasPeerJoined && !isLeaving) {
                audioManager.startWaiting()
            }
        } else {
            audioManager.startRingback()
        }
    }

    // Single initialization effect
    LaunchedEffect(roomId) {
        Log.i("WebRTCScreen", "LaunchedEffect(roomId) started for Room: $roomId")
        val webrtcConfig = dataStoreManager.webrtcConfigFlow.first()
        val audioQuality = dataStoreManager.audioQualityFlow.first()

        val iceServers = mutableListOf<PeerConnection.IceServer>()
        webrtcConfig.stunServers.forEach { 
            iceServers.add(PeerConnection.IceServer.builder(it.url).createIceServer())
        }
        webrtcConfig.turnServers.forEach { 
            val builder = PeerConnection.IceServer.builder(it.url)
            if (it.username != null) builder.setUsername(it.username)
            if (it.credential != null) builder.setPassword(it.credential)
            iceServers.add(builder.createIceServer())
        }

        if (iceServers.isEmpty()) {
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        }

        signalingClient = SignalingClient(
            serverUrl = serverUrl,
            accessToken = accessToken,
            listener = object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    isSignalingConnected = true
                }

                override fun onPeerJoined() {
                    hasPeerJoined = true
                    isRemoteBusy = false
                    webRTCClient.createOffer()
                }

                override fun onPeerLeft() {
                    Log.i("WebRTCScreen", "Signaling reported peer left or connection closed")
                    safeOnCallStopped()
                }

                override fun onUserBusy() {
                    Log.i("WebRTCScreen", "Signaling reported user busy")
                    isRemoteBusy = true
                    if (!hasPeerJoined) {
                        audioManager.startBusy()
                    }
                }

                override fun onOfferReceived(description: String) {
                    hasPeerJoined = true
                    isRemoteBusy = false
                    webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, description))
                    webRTCClient.createAnswer()
                }

                override fun onAnswerReceived(description: String) {
                    webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, description))
                }

                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                    webRTCClient.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
        )
        
        webRTCClient.initPeerConnection(iceServers, audioQuality)
        signalingClient?.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.i("WebRTCScreen", "WebRTCScreen Disposed")
            webRTCClient.close()
            signalingClient?.stop()
        }
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
            onMuteToggle = {
                isMuted = !isMuted
                webRTCClient.setMute(isMuted)
            },
            onSpeakerToggle = {
                isSpeakerOn = !isSpeakerOn
                webRTCClient.setSpeakerphoneOn(isSpeakerOn)
            },
            onCallStopped = {
                scope.launch {
                    if (accessToken != null && serverConfig != null) {
                        authService.endVoiceCall(serverConfig!!, accessToken, roomId)
                    }
                    safeOnCallStopped()
                }
            },
            modifier = Modifier
                .background(SurfaceDim)
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
