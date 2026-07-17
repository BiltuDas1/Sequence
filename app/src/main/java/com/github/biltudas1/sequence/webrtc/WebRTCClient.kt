package com.github.biltudas1.sequence.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import com.github.biltudas1.sequence.media.AudioOutput
import com.github.biltudas1.sequence.media.CallAudioManager
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Handles WebRTC PeerConnection lifecycle, SDP negotiation, and ICE candidates.
 */
class WebRTCClient(
    context: Context,
    private val callAudioManager: CallAudioManager,
    private val listener: WebRTCListener
) {
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var qualityLevel: AudioQualityLevel = AudioQualityLevel.STANDARD
    
    private var statsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    interface WebRTCListener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onSdpCreated(description: SessionDescription)
        fun onDataUsageCollected(stunSent: Long, stunRecv: Long, turnSent: Long, turnRecv: Long)
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
        fun onRelayUsageChanged(isRelay: Boolean)
    }

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun initPeerConnection(iceServers: List<PeerConnection.IceServer>, quality: AudioQualityLevel) {
        this.qualityLevel = quality
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        Timber.d("WebRTCClient: Initializing PeerConnection with quality: $quality")

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Timber.v("WebRTCClient: onIceCandidate: ${candidate.sdpMid}")
                listener.onIceCandidate(candidate)
            }

            override fun onAddStream(stream: MediaStream) {
                Timber.d("WebRTCClient: onAddStream: ${stream.id}")
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                Timber.v("WebRTCClient: onIceCandidatesRemoved: ${p0?.size}")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Timber.d("WebRTCClient: Signaling State Change: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Timber.i("WebRTCClient: ICE Connection State Change: $state")
                state?.let { listener.onConnectionStateChange(it) }
                
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    startStatsPolling()
                } else if ((state == PeerConnection.IceConnectionState.DISCONNECTED) || 
                           (state == PeerConnection.IceConnectionState.FAILED) || 
                           (state == PeerConnection.IceConnectionState.CLOSED)) {
                    stopStatsPolling()
                }
            }
            override fun onIceConnectionReceivingChange(p1: Boolean) {
                Timber.v("WebRTCClient: onIceConnectionReceivingChange: $p1")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Timber.d("WebRTCClient: ICE Gathering State Change: $state")
            }
            override fun onRemoveStream(p0: MediaStream?) {
                Timber.d("WebRTCClient: onRemoveStream")
            }
            override fun onDataChannel(p0: DataChannel?) {
                Timber.d("WebRTCClient: onDataChannel: ${p0?.label()}")
            }
            override fun onRenegotiationNeeded() {
                Timber.d("WebRTCClient: onRenegotiationNeeded")
            }
        })

        val audioConstraints = MediaConstraints().apply {
            val processing = quality.useProcessing.toString()
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", processing))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", processing))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", processing))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", processing))
        }

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK", audioSource)
        Timber.i("WebRTCClient: Local Audio Track created and added to PeerConnection")
        peerConnection?.addTrack(localAudioTrack, listOf("STREAM"))
        
        // Initial routing: default to Earpiece (or Headset if CallManager already detected it)
        setAudioOutput(AudioOutput.EARPIECE)
    }

    /**
     * Delegates audio output switching to the CallAudioManager.
     */
    fun setAudioOutput(output: AudioOutput) {
        Timber.d("WebRTCClient: Routing audio to $output")
        callAudioManager.setAudioOutput(output)
    }

    fun setMute(isMuted: Boolean) {
        Timber.d("WebRTCClient: setMute($isMuted)")
        localAudioTrack?.setEnabled(!isMuted)
    }

    private fun startStatsPolling() {
        if (statsJob != null) return
        statsJob = scope.launch {
            while (isActive) {
                peerConnection?.getStats { report ->
                    var foundRelay = false
                    report.statsMap.values.forEach { stats ->
                        if (stats.type == "candidate-pair" && (stats.members["state"] == "succeeded")) {
                            val localId = stats.members["localCandidateId"] as? String
                            val localCandidate = report.statsMap[localId]
                            if (localCandidate?.members?.get("candidateType") == "relay") {
                                foundRelay = true
                            }
                        }
                    }
                    listener.onRelayUsageChanged(foundRelay)
                }
                delay(5.seconds)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    private fun modifySdpForQuality(description: SessionDescription): SessionDescription {
        var sdp = description.description
        val bitrateBps = qualityLevel.bitrateKbps * 1000
        val isStereo = if (qualityLevel.stereo) "1" else "0"
        val isCbr = if (qualityLevel.cbr) "1" else "0"
        
        if (sdp.contains("opus/48000")) {
            val fmtpParams = StringBuilder("useinbandfec=1")
            fmtpParams.append(";maxaveragebitrate=$bitrateBps")
            fmtpParams.append(";stereo=$isStereo")
            fmtpParams.append(";sprop-stereo=$isStereo")
            fmtpParams.append(";cbr=$isCbr")
            
            if (qualityLevel.opusModeAudio) {
                fmtpParams.append(";maxptime=20;minptime=10")
            }

            sdp = sdp.replace("useinbandfec=1", fmtpParams.toString())
        }
        return SessionDescription(description.type, sdp)
    }

    fun createOffer() {
        Timber.d("WebRTCClient: Creating Offer")
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                Timber.d("WebRTCClient: Offer Created Successfully")
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Timber.d("WebRTCClient: Local Description (Offer) Set Success")
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        Timber.d("WebRTCClient: Creating Answer")
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                Timber.d("WebRTCClient: Answer Created Successfully")
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Timber.d("WebRTCClient: Local Description (Answer) Set Success")
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(description: SessionDescription) {
        Timber.d("WebRTCClient: Setting Remote Description: ${description.type}")
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Timber.i("WebRTCClient: setRemoteDescription Success")
                synchronized(pendingIceCandidates) {
                    Timber.d("WebRTCClient: Adding ${pendingIceCandidates.size} pending ICE candidates")
                    for (candidate in pendingIceCandidates) {
                        peerConnection?.addIceCandidate(candidate)
                    }
                    pendingIceCandidates.clear()
                }
            }
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        if (peerConnection?.remoteDescription != null) {
            Timber.v("WebRTCClient: Adding ICE candidate immediately")
            peerConnection?.addIceCandidate(candidate)
        } else {
            Timber.v("WebRTCClient: Queueing ICE candidate until remote description is set")
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    fun close() {
        Timber.i("WebRTCClient: Closing PeerConnection and cleaning up")
        stopStatsPolling()
        val pc = peerConnection ?: return
        peerConnection = null
        
        pc.getStats { report ->
            var stunSent = 0L
            var stunRecv = 0L
            var turnSent = 0L
            var turnRecv = 0L

            report.statsMap.values.forEach { stats ->
                if (stats.type == "candidate-pair" && (stats.members["state"] == "succeeded")) {
                    val sent = (stats.members["bytesSent"] as? Number)?.toLong() ?: 0L
                    val recv = (stats.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                    
                    val localId = stats.members["localCandidateId"] as? String
                    val localCandidate = report.statsMap[localId]
                    val isRelay = localCandidate?.members?.get("candidateType") == "relay"
                    
                    if (isRelay) {
                        turnSent += sent
                        turnRecv += recv
                    } else {
                        stunSent += sent
                        stunRecv += recv
                    }
                }
            }
            Timber.d("WebRTCClient: Final Stats - STUN(S=$stunSent R=$stunRecv), TURN(S=$turnSent R=$turnRecv)")
            listener.onDataUsageCollected(stunSent, stunRecv, turnSent, turnRecv)
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    pc.close()
                    pc.dispose()
                    Timber.d("WebRTCClient: PeerConnection disposed successfully")
                } catch (e: Exception) {
                    Timber.e(e, "WebRTCClient: Error disposing PeerConnection")
                }
            }
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Timber.e("WebRTCClient: SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Timber.e("WebRTCClient: SDP Set Failure: $p0") }
    }
}
