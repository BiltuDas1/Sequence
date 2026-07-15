# Android System Integration

## Background Orchestration

### 1. Ongoing Call Session (`CallService`)
- **Type**: `foregroundServiceType="microphone"`.
- **Purpose**: Required for persistent microphone access while the app is in the background. 
- **Notification**: Managed in `CallService.createNotification()` using `NotificationCompat.CallStyle`.

### 2. Wake-up & Lock Screen (`MyFirebaseMessagingService`)
- **Push Handling**: Receives FCM data push and triggers `IncomingCallActivity`.
- **Heads-up/Lock Screen**: Bypasses Background Activity Launch (BAL) limits using `setFullScreenIntent` in the `NotificationBuilder`.

## Permission Usage

| Permission | Component | Purpose |
|------------|-----------|---------|
| `RECORD_AUDIO` | `WebRTCClient` | Captures mic input for the call. |
| `READ_PHONE_STATE` | `CallStatusManager` | Detects cellular calls to prevent audio hardware conflicts. |
| `POST_NOTIFICATIONS` | `MyFirebaseMessagingService` | Required for Android 13+ to show call alerts. |
| `WAKE_LOCK` | `MyFirebaseMessagingService` | Prevents CPU sleep during push processing. |

## Audio Routing
`CallStatusManager` handles the `AudioManager` focus requests. VoIP audio routing (Speaker vs Earpiece) is handled in `WebRTCClient.setSpeakerphoneOn()`.
