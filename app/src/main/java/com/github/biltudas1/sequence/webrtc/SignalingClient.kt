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
        fun onPeerJoined()
        fun onPeerLeft()
        fun onOfferReceived(description: String)
        fun onAnswerReceived(description: String)
        fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, sdp: String)
    }

    fun start() {
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
                Log.d("SignalingClient", "Connected to signaling server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("SignalingClient", "Received message: $text")
                try {
                    val json = JSONObject(text)
                    when (json.optString("type")) {
                        "peer-joined" -> listener.onPeerJoined()
                        "peer-left" -> listener.onPeerLeft()
                        "offer" -> listener.onOfferReceived(json.getString("sdp"))
                        "answer" -> listener.onAnswerReceived(json.getString("sdp"))
                        "candidate" -> listener.onIceCandidateReceived(
                            json.getString("sdpMid"),
                            json.getInt("sdpMLineIndex"),
                            json.getString("candidate")
                        )
                    }
                } catch (e: Exception) {
                    Log.e("SignalingClient", "Error parsing message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SignalingClient", "Closing: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "Error: ${t.message}")
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
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }
}
