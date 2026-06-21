package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.WebRTCConfig
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.components.CallScreenContent
import com.github.biltudas1.sequence.webrtc.SignalingClient
import com.github.biltudas1.sequence.webrtc.WebRTCClient
import com.github.biltudas1.sequence.ui.theme.SurfaceDim
import com.github.biltudas1.sequence.ui.utils.CallAudioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.webrtc.*

@Composable
fun WebRTCScreen(
    roomId: String,
    serverUrl: String,
    accessToken: String?,
    onCallStopped: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStoreManager = remember { DataStoreManager(context) }
    val authService = remember { AuthService(OkHttpClient(), dataStoreManager) }
    val serverConfig by dataStoreManager.serverConfigFlow.collectAsStateWithLifecycle(initialValue = null)
    val webrtcConfig by dataStoreManager.webrtcConfigFlow.collectAsStateWithLifecycle(initialValue = WebRTCConfig())

    var signalingClient by remember { mutableStateOf<SignalingClient?>(null) }
    var isLeaving by remember { mutableStateOf(false) }
    var isSignalingConnected by remember { mutableStateOf(false) }
    var hasPeerJoined by remember { mutableStateOf(false) }
    
    val audioManager = remember { CallAudioManager(context) }

    val safeOnCallStopped = {
        if (!isLeaving) {
            isLeaving = true
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
        })
    }

    // Logic for Ring Waiting (after 2s) and Ringback
    LaunchedEffect(isSignalingConnected, hasPeerJoined) {
        if (hasPeerJoined) {
            audioManager.stopAny()
            return@LaunchedEffect
        }

        if (!isSignalingConnected) {
            // Initial phase: Wait 2 seconds before starting ringwaiting
            delay(2000)
            if (!isSignalingConnected && !hasPeerJoined && !isLeaving) {
                audioManager.startWaiting()
            }
        } else {
            // Connected phase: Stop waiting and start ringback
            audioManager.startRingback()
        }
    }

    LaunchedEffect(serverUrl, webrtcConfig) {
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
            // Fallback to default if everything is deleted
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
                    webRTCClient.createOffer()
                }

                override fun onPeerLeft() {
                    safeOnCallStopped()
                }

                override fun onOfferReceived(description: String) {
                    hasPeerJoined = true
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
        
        webRTCClient.initPeerConnection(iceServers)
        signalingClient?.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            audioManager.stopAny()
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
                .systemBarsPadding()
        )
    }
}
