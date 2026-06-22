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
        Log.d("SignalingClient", "Connecting to: $serverUrl")
        val request = Request.Builder()
            .url(serverUrl)
            .apply {
                accessToken?.let {
                    addHeader("Authorization", "Bearer $it")
                }
            }
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SignalingClient", "WebSocket Connected")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("SignalingClient", "Received Message: $text")
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "peer-joined" -> {
                            Log.d("SignalingClient", "Peer joined event")
                            listener.onPeerJoined()
                        }
                        "peer-left" -> {
                            Log.d("SignalingClient", "Peer left event")
                            listener.onPeerLeft()
                        }
                        "user-busy" -> {
                            Log.d("SignalingClient", "User busy event")
                            listener.onUserBusy()
                        }
                        "offer" -> {
                            Log.d("SignalingClient", "Offer received")
                            listener.onOfferReceived(json.getString("sdp"))
                        }
                        "answer" -> {
                            Log.d("SignalingClient", "Answer received")
                            listener.onAnswerReceived(json.getString("sdp"))
                        }
                        "candidate" -> {
                            Log.d("SignalingClient", "Candidate received")
                            listener.onIceCandidateReceived(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("candidate")
                            )
                        }
                        else -> {
                            Log.d("SignalingClient", "Unknown message type: ${json.optString("type")}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SignalingClient", "Error parsing message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("SignalingClient", "WebSocket Closing: $code / $reason")
                listener.onPeerLeft()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket Error: ${t.message}", t)
                listener.onPeerLeft()
            }
        })
    }

    fun sendOffer(sdp: String) {
        val json = JSONObject()
        json.put("type", "offer")
        json.put("sdp", sdp)
        Log.d("SignalingClient", "Sending Offer")
        webSocket?.send(json.toString())
    }

    fun sendAnswer(sdp: String) {
        val json = JSONObject()
        json.put("type", "answer")
        json.put("sdp", sdp)
        Log.d("SignalingClient", "Sending Answer")
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
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e("SignalingClient", "Error closing WebSocket", e)
        }
        webSocket = null
    }
}
