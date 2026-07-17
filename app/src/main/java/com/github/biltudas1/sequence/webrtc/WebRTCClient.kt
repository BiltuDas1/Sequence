package com.github.biltudas1.sequence.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import timber.log.Timber

class WebRTCClient(
    context: Context,
    private val listener: WebRTCListener
) {
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var qualityLevel: AudioQualityLevel = AudioQualityLevel.STANDARD
    
    private var statsJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

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

        Timber.d("Initializing PeerConnection with quality: $quality")
        iceServers.forEach { server ->
            Timber.v("ICE Server: ${server.urls.joinToString(", ")} [Redacted Creds]")
        }

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Timber.v("onIceCandidate: ${candidate.sdpMid} - ${candidate.sdp}")
                listener.onIceCandidate(candidate)
            }

            override fun onAddStream(stream: MediaStream) {
                Timber.d("onAddStream: ${stream.id}")
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                Timber.v("onIceCandidatesRemoved: ${p0?.size}")
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Timber.d("WebRTC Signaling State Change: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Timber.i("WebRTC ICE Connection State Change: $state")
                state?.let { listener.onConnectionStateChange(it) }
                
                if (state == PeerConnection.IceConnectionState.CONNECTED) {
                    startStatsPolling()
                } else if (state == PeerConnection.IceConnectionState.DISCONNECTED || 
                           state == PeerConnection.IceConnectionState.FAILED || 
                           state == PeerConnection.IceConnectionState.CLOSED) {
                    stopStatsPolling()
                }
            }
            override fun onIceConnectionReceivingChange(p1: Boolean) {
                Timber.v("onIceConnectionReceivingChange: $p1")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Timber.d("WebRTC ICE Gathering State Change: $state")
            }
            override fun onRemoveStream(p0: MediaStream?) {
                Timber.d("onRemoveStream")
            }
            override fun onDataChannel(p0: DataChannel?) {
                Timber.d("onDataChannel: ${p0?.label()}")
            }
            override fun onRenegotiationNeeded() {
                Timber.d("onRenegotiationNeeded")
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
        Timber.i("Local Audio Track created and added to PeerConnection")
        peerConnection?.addTrack(localAudioTrack, listOf("STREAM"))
        
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(false)
    }

    fun setSpeakerphoneOn(isEnabled: Boolean) {
        Timber.d("setSpeakerphoneOn: $isEnabled")
        
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isEnabled) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    speakerDevice?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    audioManager.clearCommunicationDevice()
                    val earpieceDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    earpieceDevice?.let { audioManager.setCommunicationDevice(it) }
                }
            }
            
            @Suppress("DEPRECATION")
            if (audioManager.isSpeakerphoneOn != isEnabled) {
                audioManager.isSpeakerphoneOn = isEnabled
            }
            
            Timber.d("Speakerphone toggled to: $isEnabled")
        } catch (e: Exception) {
            Timber.e(e, "Error setting speakerphone: $isEnabled")
        }
    }

    fun setMute(isMuted: Boolean) {
        Timber.d("setMute: $isMuted")
        localAudioTrack?.setEnabled(!isMuted)
    }

    private fun startStatsPolling() {
        if (statsJob != null) return
        statsJob = scope.launch {
            while (isActive) {
                peerConnection?.getStats { report ->
                    var foundRelay = false
                    report.statsMap.values.forEach { stats ->
                        if (stats.type == "candidate-pair" && stats.members["state"] == "succeeded") {
                            val localId = stats.members["localCandidateId"] as? String
                            val localCandidate = report.statsMap[localId]
                            if (localCandidate?.members?.get("candidateType") == "relay") {
                                foundRelay = true
                            }
                        }
                    }
                    listener.onRelayUsageChanged(foundRelay)
                }
                delay(5000)
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
        Timber.d("Creating Offer")
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                Timber.d("Offer Created Successfully")
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Timber.d("Local Description (Offer) Set Success")
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        Timber.d("Creating Answer")
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                Timber.d("Answer Created Successfully")
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Timber.d("Local Description (Answer) Set Success")
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(description: SessionDescription) {
        Timber.d("Setting Remote Description: ${description.type}")
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Timber.i("setRemoteDescription Success")
                synchronized(pendingIceCandidates) {
                    Timber.d("Adding ${pendingIceCandidates.size} pending ICE candidates")
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
            Timber.v("Adding ICE candidate immediately: ${candidate.sdpMid} - ${candidate.sdp}")
            peerConnection?.addIceCandidate(candidate)
        } else {
            Timber.v("Queueing ICE candidate: ${candidate.sdpMid} - ${candidate.sdp}")
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    fun close() {
        Timber.i("Closing WebRTCClient")
        stopStatsPolling()
        val pc = peerConnection ?: return
        peerConnection = null
        
        pc.getStats { report ->
            var stunSent = 0L
            var stunRecv = 0L
            var turnSent = 0L
            var turnRecv = 0L

            report.statsMap.values.forEach { stats ->
                if (stats.type == "candidate-pair" && stats.members["state"] == "succeeded") {
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
            Timber.d("Final Stats - STUN: S=$stunSent R=$stunRecv, TURN: S=$turnSent R=$turnRecv")
            listener.onDataUsageCollected(stunSent, stunRecv, turnSent, turnRecv)
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    pc.close()
                    pc.dispose()
                    Timber.d("PeerConnection disposed")
                } catch (e: Exception) {
                    Timber.e(e, "Error disposing PeerConnection")
                }
            }
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Timber.e("SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Timber.e("SDP Set Failure: $p0") }
    }
}
