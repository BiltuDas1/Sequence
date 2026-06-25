package com.github.biltudas1.sequence.webrtc

import android.util.Log
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
        Log.i("SignalingClient", "Starting connection to: $serverUrl")
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
            Log.e("SignalingClient", "Invalid URL: $serverUrl", e)
            listener.onPeerLeft()
            return
        }
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("SignalingClient", "WebSocket Connected Successfully")
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
                            Log.i("SignalingClient", "Remote peer joined")
                            listener.onPeerJoined()
                        }
                        "peer-left" -> {
                            Log.i("SignalingClient", "Remote peer left the room")
                            listener.onPeerLeft()
                        }
                        "user-busy" -> {
                            Log.i("SignalingClient", "Remote user reported busy")
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
                    Log.e("SignalingClient", "Error parsing message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("SignalingClient", "WebSocket Closing from remote. Code: $code, Reason: $reason")
                stopHeartbeat()
                listener.onPeerLeft()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket Connection Failed. Code: ${response?.code}, Message: ${t.message}")
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
                    Log.w("SignalingClient", "Heartbeat timeout! Closing connection.")
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
        Log.i("SignalingClient", "Stopping signaling client")
        stopHeartbeat()
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e("SignalingClient", "Error during stop", e)
        }
        webSocket = null
        scope.cancel()
    }
}
