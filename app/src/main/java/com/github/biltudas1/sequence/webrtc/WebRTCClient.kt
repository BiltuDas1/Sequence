package com.github.biltudas1.sequence.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    private val context: Context,
    private val listener: WebRTCListener
) {
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    interface WebRTCListener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onSdpCreated(description: SessionDescription)
        fun onDataUsageCollected(stunSent: Long, stunRecv: Long, turnSent: Long, turnRecv: Long)
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

    fun initPeerConnection(iceServers: List<PeerConnection.IceServer>) {
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

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("AUDIO_TRACK", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("STREAM"))
    }

    fun setSpeakerphoneOn(isEnabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isEnabled
    }

    fun setMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun createOffer() {
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        listener.onSdpCreated(description)
                    }
                }, description)
            }
        }, MediaConstraints())
    }

    fun createAnswer() {
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        listener.onSdpCreated(description)
                    }
                }, description)
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
        peerConnection = null // Clear reference early
        
        // Fetch stats before closing the native connection
        pc.getStats { report ->
            var stunSent = 0L
            var stunRecv = 0L
            var turnSent = 0L
            var turnRecv = 0L

            report.statsMap.values.forEach { stats ->
                // Check for the active candidate pair
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
            // Notify listener and THEN close native resources
            listener.onDataUsageCollected(stunSent, stunRecv, turnSent, turnRecv)
            pc.close()
            pc.dispose()
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) { Log.e("WebRTCClient", "SDP Create Failure: $p0") }
        override fun onSetFailure(p0: String?) { Log.e("WebRTCClient", "SDP Set Failure: $p0") }
    }
}
