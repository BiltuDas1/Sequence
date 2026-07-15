# Troubleshooting & Debugging

This guide addresses common issues encountered during development and testing of the Sequence Android app.

## 1. FCM Notifications Not Arriving
If you aren't receiving incoming call alerts:
- **Check `google-services.json`:** Ensure it matches your Firebase project and package name (`com.github.biltudas1.sequence`).
- **Token Sync:** Check logs for "FCM token updated on server". If the token isn't on the server, the server can't find your device.
- **Battery Optimization:** On some devices (OEMs like Xiaomi/Samsung), you must manually allow "Auto-start" or disable "Battery Optimization" for the app to receive pushes while closed. Use the in-app "Battery Optimization" screen to check status.

## 2. Calls Fail to Connect (ICE Failures)
If the call rings but hangs on "Connecting...":
- **Network NAT:** If both devices are on complex corporate or cellular networks, a **TURN server** is required.
- **WebRTC Config:** Go to `Settings > WebRTC Configuration` and ensure you have valid STUN/TURN servers configured.
- **Logcat:** Look for `onIceConnectionChange: FAILED`. This usually means the peers could not find a direct or relayed path to each other.

## 3. Local Development (Emulator)
- **Audio Capture:** Emulators often have trouble with microphone loops. Use a physical device for audio testing whenever possible.
- **WebSocket Connection:** If your server is running on `localhost`, use the IP `10.0.2.2` in the app's server configuration to reach the host machine.

## 4. Viewing Logs
Sequence has a built-in log viewer to help debug field issues:
- Navigate to **Settings > About > View Logs**.
- You can export these logs to a text file for analysis.
- Note: PII like emails and tokens will be redacted (e.g., `b***@e***.com`).

## 5. Cleaning State
If the app behaves inconsistently after a server update:
- Use **Settings > Data & Storage > Clear Cache**.
- If needed, perform a "Clear Data" from Android System Settings to reset the Room database and encrypted DataStore.
