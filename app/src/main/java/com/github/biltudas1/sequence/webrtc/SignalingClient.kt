package com.github.biltudas1.sequence.webrtc

import android.util.Log
import okhttp3.*
import org.json.JSONObject

class SignalingClient(
    private val serverUrl: String,
    private val accessToken: String?,
    private val listener: SignalingListener
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

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
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("SignalingClient", "Received Message: $text")
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
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
                        else -> {
                            Log.d("SignalingClient", "Unhandled message: ${json.optString("type")}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SignalingClient", "Error parsing message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("SignalingClient", "WebSocket Closing from remote. Code: $code, Reason: $reason")
                listener.onPeerLeft()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket Connection Failed. Code: ${response?.code}, Message: ${t.message}", t)
                listener.onPeerLeft()
            }
        })
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
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e("SignalingClient", "Error during stop", e)
        }
        webSocket = null
    }
}
