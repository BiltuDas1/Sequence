package com.github.biltudas1.sequence.webrtc

import android.content.Context
import android.util.Log
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.utils.CallAudioManager
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.webrtc.*

object CallManager {
    private var webRTCClient: WebRTCClient? = null
    private var signalingClient: SignalingClient? = null
    private var scope: CoroutineScope? = null
    private var audioManager: CallAudioManager? = null
    
    var activeRoomId: String? = null
    var activeCallerName: String = ""
    var activeCallerEmail: String = ""
    var activeServerUrl: String = ""
    var isExternalCall: Boolean = false
    
    var isMuted = mutableStateOf(false)
    var isSpeakerOn = mutableStateOf(false)
    var hasPeerJoined = mutableStateOf(false)
    var isRemoteBusy = mutableStateOf(false)
    var isSignalingConnected = mutableStateOf(false)
    
    var onCallEnded: (() -> Unit)? = null
    private var startTime: Long = 0

    fun initCall(
        context: Context,
        roomId: String,
        serverUrl: String,
        name: String,
        email: String,
        isExternal: Boolean,
        accessToken: String?
    ) {
        if (activeRoomId != null) return
        
        activeRoomId = roomId
        activeCallerName = name
        activeCallerEmail = email
        activeServerUrl = serverUrl
        isExternalCall = isExternal
        
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        audioManager = CallAudioManager(context)
        
        val dataStoreManager = DataStoreManager.getInstance(context)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        
        webRTCClient = WebRTCClient(context, object : WebRTCClient.WebRTCListener {
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
                scope?.launch(Dispatchers.IO) {
                    dataStoreManager.addDataUsage(stunSent, stunRecv, turnSent, turnRecv)
                }
            }
        })

        signalingClient = SignalingClient(
            serverUrl = serverUrl,
            accessToken = accessToken,
            listener = object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    isSignalingConnected.value = true
                }

                override fun onPeerJoined() {
                    hasPeerJoined.value = true
                    isRemoteBusy.value = false
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                    webRTCClient?.createOffer()
                    audioManager?.stopAny()
                    CallStatusManager(context).requestCallAudioFocus()
                    CallService.start(context, roomId, name, email, isExternal, serverUrl)
                }

                override fun onPeerLeft() {
                    Log.i("CallManager", "Peer left")
                    terminateCall(context)
                }

                override fun onUserBusy() {
                    isRemoteBusy.value = true
                    if (!hasPeerJoined.value) {
                        audioManager?.startBusy()
                    }
                }

                override fun onOfferReceived(description: String) {
                    hasPeerJoined.value = true
                    isRemoteBusy.value = false
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, description))
                    webRTCClient?.createAnswer()
                    audioManager?.stopAny()
                    CallStatusManager(context).requestCallAudioFocus()
                    CallService.start(context, roomId, name, email, isExternal, serverUrl)
                }

                override fun onAnswerReceived(description: String) {
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, description))
                }

                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                    webRTCClient?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
        )

        scope?.launch {
            val webrtcConfig = dataStoreManager.webrtcConfigFlow.first()
            val audioQuality = dataStoreManager.audioQualityFlow.first()

            val iceServers = webrtcConfig.stunServers.map { PeerConnection.IceServer.builder(it.url).createIceServer() } +
                             webrtcConfig.turnServers.map { 
                                 PeerConnection.IceServer.builder(it.url)
                                     .setUsername(it.username ?: "")
                                     .setPassword(it.credential ?: "")
                                     .createIceServer()
                             }
            
            val finalIceServers = if (iceServers.isEmpty()) {
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            } else iceServers

            webRTCClient?.initPeerConnection(finalIceServers, audioQuality)
            signalingClient?.start()
            
            // Start foreground service immediately for persistent notification
            CallService.start(context, roomId, name, email, isExternal, serverUrl)

            // Start ringback/waiting logic
            launch {
                delay(2000)
                if (!hasPeerJoined.value && !isRemoteBusy.value) {
                    if (isSignalingConnected.value) audioManager?.startRingback()
                    else audioManager?.startWaiting()
                }
            }
        }
    }

    fun toggleMute(context: Context? = null) {
        isMuted.value = !isMuted.value
        webRTCClient?.setMute(isMuted.value)
        context?.let { CallService.updateNotification(it) }
    }

    fun toggleSpeaker(context: Context? = null) {
        isSpeakerOn.value = !isSpeakerOn.value
        webRTCClient?.setSpeakerphoneOn(isSpeakerOn.value)
        context?.let { CallService.updateNotification(it) }
    }

    fun terminateCall(context: Context) {
        val roomId = activeRoomId
        if (roomId == null) return
        
        Log.i("CallManager", "Terminating call")
        activeRoomId = null 

        val duration = if (startTime > 0) System.currentTimeMillis() - startTime else 0
        startTime = 0
        
        CoroutineScope(Dispatchers.IO).launch {
            val repository = com.github.biltudas1.sequence.data.CallLogRepository(context.applicationContext)
            repository.updateDuration(roomId, duration)
        }

        webRTCClient?.close()
        signalingClient?.stop()
        audioManager?.stopAny()
        CallService.stop(context)
        
        hasPeerJoined.value = false
        isRemoteBusy.value = false
        isSignalingConnected.value = false
        isMuted.value = false
        isSpeakerOn.value = false
        
        // Notify listeners that call ended on the Main thread
        val listener = onCallEnded
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listener?.invoke()
        }
        
        scope?.cancel()
        scope = null
    }
}
