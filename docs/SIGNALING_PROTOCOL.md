# Signaling Protocol

WebSocket Endpoint: `ws://<host>/room/<roomId>?u=<username>&p=<password>`

## Message Schemas

### `peer-joined`
Sent by server to initiator when callee connects.

### `offer` / `answer`
Standard WebRTC SDP exchange.
```json
{ "type": "offer", "sdp": "..." }
```

### `ice-candidate`
```json
{
  "type": "ice-candidate",
  "sdpMid": "audio",
  "sdpMLineIndex": 0,
  "candidate": "..."
}
```

### `peer-left`
Sent on peer disconnect.

### `busy` (REST/WS)
```json
{ "type": "busy", "roomId": "..." }
```
