# Architecture Overview

## WebRTC Call Flow

The following diagram illustrates the sequence of operations required to establish a peer-to-peer connection between a Caller and a Callee.

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Caller (App A)
    participant Server as Sequence Server
    participant FCM as Firebase Cloud Messaging
    actor Receiver as Receiver (App B)

    Note over Caller, Server: 1. Call Request
    Caller->>Server: HTTP POST /call/start
    
    Note over Server, Receiver: 2. Device Wakeup
    Server->>FCM: Send push notification
    FCM->>Receiver: Wake device (MyFirebaseMessagingService)
    Receiver-->>Receiver: Acquire WakeLock & Show Call UI
    
    Note over Receiver, Server: 3. Connection & Signaling
    Receiver->>Receiver: User Accepts Call
    Receiver->>Server: Connect WebSocket (SignalingClient)
    Server->>Caller: WS Event: "peer-joined"
    
    Note over Caller, Receiver: 4. WebRTC Negotiation
    Caller->>Caller: Create WebRTC Offer
    Caller->>Server: WS Send: {type: "offer", sdp: ...}
    Server->>Receiver: WS Receive: {type: "offer"}
    
    Receiver->>Receiver: Set Remote Description, Create Answer
    Receiver->>Server: WS Send: {type: "answer", sdp: ...}
    Server->>Caller: WS Receive: {type: "answer"}
    
    par ICE Candidate Exchange
        Caller->>Server: Send ICE Candidates
        Server->>Receiver: Receive ICE Candidates
    and
        Receiver->>Server: Send ICE Candidates
        Server->>Caller: Receive ICE Candidates
    end
    
    Note over Caller, Receiver: 5. Peer-to-Peer Audio Active
    Caller->>Receiver: WebRTC Audio Stream (DTLS/SRTP)
    Receiver->>Caller: WebRTC Audio Stream (DTLS/SRTP)

    Note over Caller, Receiver: 6. Call Termination
    Caller->>Caller: User Hangs Up (CallManager.terminateCall)
    Caller->>Server: Close WebSocket Connection
    Server->>Receiver: WS Event: "peer-left"
    Receiver->>Receiver: Close WebRTC & Update UI
    Caller->>Caller: Close WebRTC & Update UI
```


### Implementation Details:

1. **Call Request:** Initiated via `AuthService.sendVoiceCall()`.
2. **Device Wakeup:** Handled by `MyFirebaseMessagingService`. Triggers `IncomingCallActivity` via a Full-Screen Intent.
3. **Connection & Signaling:** Once accepted, `CallManager` initiates `SignalingClient` (WebSocket) and `WebRTCClient`.
4. **Negotiation:** `SignalingClient` exchanges SDP Offer/Answer and ICE candidates.
5. **Active Call:** `CallService` (Foreground) maintains the session. `WebRTCClient` manages the `PeerConnection` and audio tracks.
6. **Termination:** `CallManager.terminateCall()` cleans up local resources, stops the `CallService`, and notifies the server via `AuthService.endVoiceCall()`.
