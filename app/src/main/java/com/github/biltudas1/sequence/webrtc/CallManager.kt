package com.github.biltudas1.sequence.webrtc

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.github.biltudas1.sequence.data.DataStoreManager
import com.github.biltudas1.sequence.media.AudioOutput
import com.github.biltudas1.sequence.media.CallAudioManager
import com.github.biltudas1.sequence.media.CallRingtonePlayer
import com.github.biltudas1.sequence.media.CallStatusManager
import com.github.biltudas1.sequence.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.webrtc.*
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton object that manages the high-level call lifecycle, signaling integration, 
 * and shared call UI state.
 */
object CallManager {
    private var webRTCClient: WebRTCClient? = null
    private var signalingClient: SignalingClient? = null
    private var scope: CoroutineScope? = null
    private var audioManager: CallAudioManager? = null
    private var turnConsentDeferred: CompletableDeferred<Boolean>? = null
    
    var activeRoomId: String? = null
    var activeCallerName: String = ""
    var activeCallerEmail: String = ""
    var activeServerUrl: String = ""
    var isExternalCall: Boolean = false
    var activeCreationTime: Long? = null
    
    var isMuted = mutableStateOf(false)
    var audioOutput = mutableStateOf(AudioOutput.EARPIECE)
    var isHeadsetConnected = mutableStateOf(false)
    var hasPeerJoined = mutableStateOf(false)
    var isRemoteBusy = mutableStateOf(false)
    var isSignalingConnected = mutableStateOf(false)
    var isTurnWarningVisible = mutableStateOf(false)
    var isUsingRelay = mutableStateOf(false)
    
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
        creationTime: Long? = null,
        isOutgoing: Boolean = false
    ) {
        if (activeRoomId != null) {
            Timber.w("CallManager: initCall called while another call is active: $activeRoomId")
            return
        }
        
        Timber.i("CallManager: Initializing call - Room: $roomId, Email: ${AppLogger.redactEmail(email)}, Outgoing: $isOutgoing")
        activeRoomId = roomId
        activeCallerName = name
        activeCallerEmail = email
        activeServerUrl = serverUrl
        isExternalCall = isExternal
        activeCreationTime = creationTime
        
        // Reset states
        audioOutput.value = AudioOutput.EARPIECE
        isHeadsetConnected.value = false
        isMuted.value = false
        hasPeerJoined.value = false
        isRemoteBusy.value = false
        isUsingRelay.value = false
        
        CallRingtonePlayer.stop(context)
        
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        
        // Use application context to avoid memory leaks in the singleton manager
        val appContext = context.applicationContext
        val am = CallAudioManager(appContext)
        audioManager = am
        
        am.setOnDeviceChangedListener { output, isConnected ->
            Timber.d("CallManager: Audio device changed (output=$output, isConnected=$isConnected)")
            audioOutput.value = output
            isHeadsetConnected.value = isConnected
            webRTCClient?.setAudioOutput(output)
        }
        
        val dataStoreManager = DataStoreManager.getInstance(appContext)
        
        webRTCClient = WebRTCClient(appContext, am, object : WebRTCClient.WebRTCListener {
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

            override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
                if ((state == PeerConnection.IceConnectionState.DISCONNECTED) || (state == PeerConnection.IceConnectionState.FAILED)) {
                    if (hasPeerJoined.value) {
                        Timber.w("CallManager: Peer connection lost. Terminating call.")
                        terminateCall(appContext)
                    }
                }
            }

            override fun onRelayUsageChanged(isRelay: Boolean) {
                if (isUsingRelay.value != isRelay) {
                    isUsingRelay.value = isRelay
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
                    Timber.i("CallManager: Peer joined")
                    hasPeerJoined.value = true
                    isRemoteBusy.value = false
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                    webRTCClient?.createOffer()
                    audioManager?.stopAny()
                    CallStatusManager(appContext).requestCallAudioFocus()
                    CallService.start(appContext, roomId, name, email, isExternal, serverUrl)
                }

                override fun onPeerLeft() {
                    Timber.i("CallManager: Peer left")
                    terminateCall(appContext)
                }

                override fun onUserBusy() {
                    Timber.i("CallManager: Receiver is busy")
                    isRemoteBusy.value = true
                    if (!hasPeerJoined.value) {
                        audioManager?.startBusy()
                    }
                }

                override fun onOfferReceived(description: String) {
                    Timber.i("CallManager: Offer received")
                    hasPeerJoined.value = true
                    isRemoteBusy.value = false
                    if (startTime == 0L) startTime = System.currentTimeMillis()
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, description))
                    webRTCClient?.createAnswer()
                    audioManager?.stopAny()
                    CallStatusManager(appContext).requestCallAudioFocus()
                    CallService.start(appContext, roomId, name, email, isExternal, serverUrl)
                }

                override fun onAnswerReceived(description: String) {
                    Timber.i("CallManager: Answer received")
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, description))
                    audioManager?.stopAny()
                }

                override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
                    webRTCClient?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
        )

        scope?.launch {
            val webrtcConfig = dataStoreManager.webrtcConfigFlow.first()
            val audioQuality = dataStoreManager.audioQualityFlow.first()

            if (webrtcConfig.turnServers.isNotEmpty()) {
                isTurnWarningVisible.value = true
                val deferred = CompletableDeferred<Boolean>()
                turnConsentDeferred = deferred
                
                Timber.d("CallManager: Waiting for TURN usage consent...")
                val consent = deferred.await()
                turnConsentDeferred = null
                
                if (!consent) {
                    Timber.i("CallManager: User denied TURN usage. Initialization aborted.")
                    return@launch 
                }
            }

            val iceServers = webrtcConfig.stunServers.map { PeerConnection.IceServer.builder(it.url).createIceServer() } +
                             webrtcConfig.turnServers.map { 
                                 PeerConnection.IceServer.builder(it.url)
                                     .setUsername(it.username ?: "")
                                     .setPassword(it.credential ?: "")
                                     .createIceServer()
                             }
            
            val finalIceServers = iceServers.ifEmpty {
                listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            }

            webRTCClient?.initPeerConnection(finalIceServers, audioQuality)
            
            CallStatusManager(appContext).requestCallAudioFocus()
            delay(300.milliseconds)
            audioManager?.setAudioOutput(audioOutput.value)

            signalingClient?.start()
            
            CallService.start(appContext, roomId, name, email, isExternal, serverUrl)

            if (isOutgoing) {
                launch {
                    delay(2.seconds)
                    if ((activeRoomId == roomId) && !hasPeerJoined.value && !isRemoteBusy.value) {
                        if (isSignalingConnected.value) audioManager?.startRingback()
                        else audioManager?.startWaiting()
                    }
                }
            }
        }
    }


    fun toggleMute(context: Context? = null) {
        val newValue = !isMuted.value
        Timber.i("CallManager: Toggling mute to $newValue")
        isMuted.value = newValue
        webRTCClient?.setMute(newValue)
        context?.let { 
            scope?.launch {
                CallService.updateNotification(it)
            }
        }
    }

    /**
     * Cycles through available audio outputs (Earpiece -> Speaker -> Headset).
     */
    fun toggleAudioOutput(context: Context? = null) {
        val current = audioOutput.value
        val next = when (current) {
            AudioOutput.EARPIECE -> AudioOutput.SPEAKER
            AudioOutput.SPEAKER -> if (isHeadsetConnected.value) AudioOutput.HEADSET else AudioOutput.EARPIECE
            AudioOutput.HEADSET -> AudioOutput.EARPIECE
        }
        
        Timber.i("CallManager: Toggling audio output: $current -> $next")
        audioOutput.value = next
        
        scope?.launch(Dispatchers.Default) {
            audioManager?.setAudioOutput(next)
            context?.let { 
                CallService.updateNotification(it)
            }
        }
    }

    fun confirmTurnUsage(context: Context, consent: Boolean) {
        Timber.i("CallManager: TURN usage consent=$consent")
        if (!consent) {
            turnConsentDeferred?.complete(false)
            terminateCall(context)
        } else {
            isTurnWarningVisible.value = false
            turnConsentDeferred?.complete(true)
        }
    }

    /**
     * Terminates the current call session and cleans up all related resources.
     */
    fun terminateCall(context: Context) {
        val roomId = activeRoomId
        if (roomId == null) {
            Timber.d("CallManager: terminateCall called but no active call")
            return
        }
        
        Timber.i("CallManager: Terminating call session")
        activeRoomId = null 

        val duration = if (startTime > 0) System.currentTimeMillis() - startTime else 0
        startTime = 0
        
        val cTime = activeCreationTime
        activeCreationTime = null
        
        CoroutineScope(Dispatchers.IO).launch {
            val repository = com.github.biltudas1.sequence.data.repository.CallLogRepository(context.applicationContext)
            repository.updateDuration(roomId, duration, cTime)
        }

        audioManager?.cleanup()
        audioManager = null
        
        webRTCClient?.close()
        webRTCClient = null
        
        signalingClient?.stop()
        signalingClient = null
        
        CallService.stop(context)
        
        turnConsentDeferred?.complete(false)
        turnConsentDeferred = null
        
        // Notify listeners that call ended
        val listener = onCallEnded
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listener?.invoke()
        }
        
        scope?.cancel()
        scope = null
    }
}
