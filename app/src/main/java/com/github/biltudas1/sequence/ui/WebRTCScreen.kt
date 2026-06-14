package com.github.biltudas1.sequence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.biltudas1.sequence.webrtc.SignalingClient
import com.github.biltudas1.sequence.webrtc.WebRTCClient
import com.github.biltudas1.sequence.ui.theme.SurfaceDim
import com.github.biltudas1.sequence.ui.theme.TextSecondary
import org.webrtc.*

@Composable
fun WebRTCScreen(
    roomId: String,
    serverUrl: String,
    onCallStopped: () -> Unit
) {
    val context = LocalContext.current
    var signalingClient: SignalingClient? by remember { mutableStateOf(null) }

    val webRTCClient = remember {
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

    LaunchedEffect(Unit) {
        val sc = SignalingClient(serverUrl, object : SignalingClient.SignalingListener {
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
        signalingClient = sc
        
        webRTCClient.initPeerConnection()
        sc.start()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDim)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice Call",
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "In Call",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Room: $roomId",
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Stop Button
        FloatingActionButton(
            onClick = { onCallStopped() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "Stop Call")
        }
    }
}
}
