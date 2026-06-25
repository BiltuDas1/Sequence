package com.github.biltudas1.sequence.webrtc

import timber.log.Timber
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val serverUrl: String,
    private val accessToken: String?,
    private val listener: SignalingListener
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable timeout for websockets
        .build()
    private var webSocket: WebSocket? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var lastMessageTime = System.currentTimeMillis()
    private val HEARTBEAT_INTERVAL = 5000L
    private val HEARTBEAT_TIMEOUT = 15000L

    interface SignalingListener {
        fun onConnected()
        fun onPeerJoined()
        fun onPeerLeft()
        fun onUserBusy()
        fun onOfferReceived(description: String)
        fun onAnswerReceived(description: String)
        fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String)
    }

    fun start() {
        Timber.i("Starting connection to: $serverUrl")
        val request = try {
            Request.Builder()
                .url(serverUrl)
                .apply {
                    accessToken?.let {
                        addHeader("Authorization", "Bearer $it")
                    }
                }
                .build()
        } catch (e: Exception) {
            Timber.e(e, "Invalid URL: $serverUrl")
            listener.onPeerLeft()
            return
        }
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.i("WebSocket Connected Successfully")
                lastMessageTime = System.currentTimeMillis()
                startHeartbeat()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastMessageTime = System.currentTimeMillis()
                // Log.i("SignalingClient", "Received Message: $text")
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "ping" -> {
                            sendPong()
                        }
                        "pong" -> {
                            // Already updated lastMessageTime
                        }
                        "peer-joined" -> {
                            Timber.i("Remote peer joined")
                            listener.onPeerJoined()
                        }
                        "peer-left" -> {
                            Timber.i("Remote peer left the room")
                            listener.onPeerLeft()
                        }
                        "user-busy" -> {
                            Timber.i("Remote user reported busy")
                            listener.onUserBusy()
                        }
                        "offer" -> {
                            listener.onOfferReceived(json.getString("sdp"))
                        }
                        "answer" -> {
                            listener.onAnswerReceived(json.getString("sdp"))
                        }
                        "candidate" -> {
                            listener.onIceCandidateReceived(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("candidate")
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing message: $text")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket Closing from remote. Code: $code, Reason: $reason")
                stopHeartbeat()
                listener.onPeerLeft()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket Connection Failed. Code: ${response?.code}")
                stopHeartbeat()
                listener.onPeerLeft()
            }
        })
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)
                val now = System.currentTimeMillis()
                if (now - lastMessageTime > HEARTBEAT_TIMEOUT) {
                    Timber.w("Heartbeat timeout! Closing connection.")
                    listener.onPeerLeft()
                    stop()
                    break
                }
                sendPing()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendPing() {
        val json = JSONObject()
        json.put("type", "ping")
        webSocket?.send(json.toString())
    }

    private fun sendPong() {
        val json = JSONObject()
        json.put("type", "pong")
        webSocket?.send(json.toString())
    }

    fun sendOffer(sdp: String) {
        val json = JSONObject()
        json.put("type", "offer")
        json.put("sdp", sdp)
        webSocket?.send(json.toString())
    }

    fun sendAnswer(sdp: String) {
        val json = JSONObject()
        json.put("type", "answer")
        json.put("sdp", sdp)
        webSocket?.send(json.toString())
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val json = JSONObject()
        json.put("type", "candidate")
        json.put("sdpMid", sdpMid)
        json.put("sdpMLineIndex", sdpMLineIndex)
        json.put("candidate", candidate)
        webSocket?.send(json.toString())
    }

    fun stop() {
        Timber.i("Stopping signaling client")
        stopHeartbeat()
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Timber.e(e, "Error during stop")
        }
        webSocket = null
        scope.cancel()
    }
}
