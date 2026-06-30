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

1. **Call Request:** The caller initiates a call via an HTTP POST request to the signaling server.
2. **Device Wakeup:** The server triggers an FCM push notification. This wakes up the receiver's device via `MyFirebaseMessagingService`, acquiring a wake lock and displaying the incoming call UI, even if the app is closed.
3. **Connection & Signaling:** Once the receiver accepts, their app connects to the signaling WebSocket. The server notifies the caller that the peer has joined.
4. **WebRTC Negotiation:** The clients use the WebSocket to exchange WebRTC Session Description Protocol (SDP) Offers and Answers, followed by ICE candidates to punch through network NATs.
5. **Peer-to-Peer Audio Active:** A direct, encrypted (DTLS/SRTP) WebRTC audio stream is established between the two devices. The WebSocket remains open strictly to monitor the connection state.
6. **Call Termination:** When either user hangs up, their device closes the WebSocket. The server detects this drop and broadcasts a `peer-left` event to the remaining user, triggering a clean teardown of the UI and WebRTC clients on both sides.