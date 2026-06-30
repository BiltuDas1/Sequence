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


### Brief Explanation:

1.  **Offer/Answer Exchange**: The Caller creates an Session Description Protocol (SDP) offer and sends it to the Callee through the signaling server. The Callee responds with an SDP answer. This step negotiates media capabilities.
2.  **ICE Candidate Gathering**: Both peers interact with STUN/TURN servers to discover their network paths. These "ICE candidates" are exchanged via the signaling server.
3.  **Peer-to-Peer Connection**: Once candidates are exchanged and a viable path is found, a direct peer-to-peer connection is established, and the audio stream begins.
