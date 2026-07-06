package com.github.biltudas1.sequence.webrtc

import android.content.Context
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import com.github.biltudas1.sequence.data.remote.AuthService
import com.github.biltudas1.sequence.ui.utils.CallAudioManager
import com.github.biltudas1.sequence.ui.utils.CallRingtonePlayer
import com.github.biltudas1.sequence.ui.utils.CallStatusManager
import androidx.compose.runtime.mutableStateOf
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.webrtc.*
import timber.log.Timber

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
    var activeCreationTime: Long? = null
    
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
        accessToken: String?,
        creationTime: Long? = null
    ) {
        if (activeRoomId != null) {
            Timber.w("initCall called while another call is active: $activeRoomId")
            return
        }
        
        Timber.i("Initializing call - Room: $roomId, Name: $name, Email: ${AppLogger.redact(email)}, External: $isExternal, creationTime: $creationTime")
        activeRoomId = roomId
        activeCallerName = name
        activeCallerEmail = email
        activeServerUrl = serverUrl
        isExternalCall = isExternal
        activeCreationTime = creationTime
        
        // Stop any incoming ringtone immediately when a call starts initializing
        CallRingtonePlayer.stop(context)
        
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        audioManager = CallAudioManager(context)
        
        val dataStoreManager = DataStoreManager.getInstance(context)
        val authService = AuthService(OkHttpClient(), dataStoreManager)
        
        webRTCClient = WebRTCClient(context, object : WebRTCClient.WebRTCListener {
            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient?.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onSdpCreated(description: SessionDescription) {
                Timber.d("onSdpCreated: ${description.type}")
                if (description.type == SessionDescription.Type.OFFER) {
                    signalingClient?.sendOffer(description.description)
                } else if (description.type == SessionDescription.Type.ANSWER) {
                    signalingClient?.sendAnswer(description.description)
                }
            }

            override fun onDataUsageCollected(stunSent: Long, stunRecv: Long, turnSent: Long, turnRecv: Long) {
                Timber.d("onDataUsageCollected: STUN($stunSent/$stunRecv) TURN($turnSent/$turnRecv)")
                scope?.launch(Dispatchers.IO) {
                    dataStoreManager.addDataUsage(stunSent, stunRecv, turnSent, turnRecv)
                }
            }

            override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
                Timber.i("ICE Connection State Change: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                    // Check if peer joined at all before terminating immediately
                    if (hasPeerJoined.value) {
                        Timber.w("Peer connection lost/failed. Terminating call.")
                        terminateCall(context)
                    }
                }
            }
        })

        signalingClient = SignalingClient(
            serverUrl = serverUrl,
            accessToken = accessToken,
            listener = object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    Timber.i("Signaling connected")
                    isSignalingConnected.value = true
                }

                override fun onPeerJoined() {
                    Timber.i("onPeerJoined event")
                    hasPeerJoined.value = true
                    isRemoteBusy.value = false
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                    webRTCClient?.createOffer()
                    audioManager?.stopAny()
                    CallStatusManager(context).requestCallAudioFocus()
                    CallService.start(context, roomId, name, email, isExternal, serverUrl)
                }

                override fun onPeerLeft() {
                    Timber.i("onPeerLeft event")
                    terminateCall(context)
                }

                override fun onUserBusy() {
                    Timber.i("onUserBusy event")
                    isRemoteBusy.value = true
                    if (!hasPeerJoined.value) {
                        audioManager?.startBusy()
                    }
                }

                override fun onOfferReceived(description: String) {
                    Timber.i("onOfferReceived event")
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
                    Timber.i("onAnswerReceived event")
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, description))
                }

                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                    Timber.v("onIceCandidateReceived: $sdpMid")
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
            
            // Ensure we start with the current speaker state (default: earpiece)
            // and request audio focus so the system routes audio correctly to earpiece
            CallStatusManager(context).requestCallAudioFocus()
            webRTCClient?.setSpeakerphoneOn(isSpeakerOn.value)

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
        if (roomId == null) {
            Timber.d("terminateCall called but no activeRoomId")
            return
        }
        
        Timber.i("Terminating call for room: $roomId")
        activeRoomId = null 

        val duration = if (startTime > 0) System.currentTimeMillis() - startTime else 0
        Timber.d("Call duration: ${duration}ms")
        startTime = 0
        
        val cTime = activeCreationTime
        activeCreationTime = null
        
        CoroutineScope(Dispatchers.IO).launch {
            val repository = com.github.biltudas1.sequence.data.CallLogRepository(context.applicationContext)
            repository.updateDuration(roomId, duration, cTime)
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
