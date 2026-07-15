# Security & Privacy

## Data Protection
- **Communication**: Audio streams are encrypted via **DTLS** and **SRTP** (Managed by WebRTC native layer).
- **Credentials**: `CryptoManager.kt` handles **AES-256 GCM** encryption of tokens stored in `DataStoreManager`.

## Logging (`AppLogger.kt`)
- **Redaction**: `redact()` removes emails/tokens from logs before they hit the disk or memory buffer.
- **Privacy**: No automated cloud logging; logs remain local to the device.

## Access Control
- **Privacy Mode**: Logic in `CallStatusManager` and server-side filtering prevents unauthorized users from initiating calls.
