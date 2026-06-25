package com.github.biltudas1.sequence.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.github.biltudas1.sequence.data.model.AudioQualityLevel
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    context: Context,
    private val listener: WebRTCListener
) {
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var qualityLevel: AudioQualityLevel = AudioQualityLevel.STANDARD

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    interface WebRTCListener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onSdpCreated(description: SessionDescription)
        fun onDataUsageCollected(stunSent: Long, stunRecv: Long, turnSent: Long, turnRecv: Long)
        fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    }

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun initPeerConnection(iceServers: List<PeerConnection.IceServer>, quality: AudioQualityLevel) {
        this.qualityLevel = quality
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                listener.onIceCandidate(candidate)
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d("WebRTCClient", "onAddStream")
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d("WebRTCClient", "onSignalingChange: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTCClient", "onIceConnectionChange: $state")
                state?.let { listener.onConnectionStateChange(it) }
            }
            override fun onIceConnectionReceivingChange(p1: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("WebRTCClient", "onIceGatheringChange: $state")
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d("WebRTCClient", "onRenegotiationNeeded")
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
        peerConnection?.addTrack(localAudioTrack, listOf("STREAM"))
        
        // Ensure audio mode is set for VoIP call detection
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun setSpeakerphoneOn(isEnabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (isEnabled) {
                speakerDevice?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = isEnabled
        }
    }

    fun setMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
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
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                if (p0 == null) return
                val mungedDescription = modifySdpForQuality(p0)
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        listener.onSdpCreated(mungedDescription)
                    }
                }, mungedDescription)
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(description: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d("WebRTCClient", "setRemoteDescription Success")
                synchronized(pendingIceCandidates) {
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
            peerConnection?.addIceCandidate(candidate)
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    fun close() {
        val pc = peerConnection ?: return
        peerConnection = null
        
        // Get stats before closing to log final usage
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
            listener.onDataUsageCollected(stunSent, stunRecv, turnSent, turnRecv)
            
            // Dispose WebRTC objects on Main thread to avoid threading issues
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    pc.close()
                    pc.dispose()
                } catch (e: Exception) {
                    Log.e("WebRTCClient", "Error disposing PeerConnection", e)
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
        override fun onCreateFailure(p0: String?) { Log.e("WebRTCClient", "SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("WebRTCClient", "SDP Set Failure: $p0") }
    }
}
