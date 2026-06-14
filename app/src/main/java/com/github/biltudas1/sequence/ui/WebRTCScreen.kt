package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.biltudas1.sequence.ui.components.CallScreenContent
import com.github.biltudas1.sequence.webrtc.SignalingClient
import com.github.biltudas1.sequence.webrtc.WebRTCClient
import com.github.biltudas1.sequence.ui.theme.SurfaceDim
import org.webrtc.*

@Composable
fun WebRTCScreen(
    roomId: String,
    serverUrl: String,
    onCallStopped: () -> Unit
) {
    val context = LocalContext.current
    var signalingClient by remember { mutableStateOf<SignalingClient?>(null) }

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

    LaunchedEffect(serverUrl) {
        signalingClient = SignalingClient(serverUrl, object : SignalingClient.SignalingListener {
            override fun onPeerJoined() {
                webRTCClient.createOffer()
            }

            override fun onPeerLeft() {
                onCallStopped()
            }

            override fun onOfferReceived(description: String) {
                webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, description))
                webRTCClient.createAnswer()
            }

            override fun onAnswerReceived(description: String) {
                webRTCClient.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, description))
            }

            override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                webRTCClient.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
            }
        })
        
        webRTCClient.initPeerConnection()
        signalingClient?.start()
    }

    DisposableEffect(Unit) {
        onDispose {
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
            onCallStopped = onCallStopped,
            modifier = Modifier
                .background(SurfaceDim)
                .systemBarsPadding()
        )
    }
}
