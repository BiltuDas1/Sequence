# Sequence

Sequence is a communication tool for Android designed for high quality audio calls. It uses peer to peer technology to ensure that connections are direct, stable, and clear.

The project is useful for users who need a reliable, low latency audio communication platform that supports custom signaling configurations. By using WebRTC for direct audio streaming, Sequence provides a more private and efficient way to connect compared to traditional centralized services.

## Key Features

* **High Fidelity Audio**: Direct audio streaming with low latency and clear sound quality.
* **Custom Signaling**: Support for WebSockets and flexible server endpoints.
* **Usage Monitoring**: Integrated tools to track and manage mobile data consumption.
* **Contact Management**: Simple organization for frequent contacts and call history.
* **Background Alerts**: Reliable call notifications via Firebase Cloud Messaging.

## Getting Started

Sequence is an open-source Android client that requires a backend signaling server to negotiate connections.

**For Users / Self-Hosters:**
To run your own secure, private signaling server, we provide a highly optimized Docker image. For more information Please see our [Self-Hosting & Infrastructure Guide](docs/SELF_HOSTING.md).

**For Developers:**
If you want to compile the Android app, test features, or contribute to the codebase, please read the [Local Development Guide](docs/DEVELOPMENT.md).

For deeper insights, see:
- [Sequence Architecture & Call Flow](docs/ARCHITECTURE.md)
- [Signaling Protocol Specification](docs/SIGNALING_PROTOCOL.md)
- [Android System Integration](docs/ANDROID_SYSTEM_INTEGRATION.md)
- [Security & Privacy Standards](docs/SECURITY_PRIVACY.md)
- [Troubleshooting & Debugging](docs/TROUBLESHOOTING.md)
- [In-App Update Mechanism](docs/APP_UPDATES.md)
- [UI Testing with Maestro](docs/UI_TESTING_MAESTRO.md)

## Project Structure

Sequence follows a feature-oriented and modular structure to keep the codebase maintainable:

- **`ui/`**: Presentation layer grouped by feature.
    - `auth/`: Login and registration flows.
    - `call/`: Active call screens and incoming call handling.
    - `main/`: Primary dashboard and call history.
    - `settings/`: App preferences and WebRTC configuration.
- **`data/`**: Data layer with repositories, Room database (`local`), and network services (`remote`).
- **`media/`**: Centralized management for audio routing, ringtones, and call focus.
- **`webrtc/`**: Core WebRTC implementation, signaling clients, and foreground services.
- **`service/`**: Background infrastructure, including FCM push handling and app update workers.

## Help and Support

If you experience any issues or have questions about the application, please open a new issue in the GitHub repository. Provide as much detail as possible about your environment and the problem you are facing to help us assist you effectively.

## Maintenance and Contributing

Sequence is maintained by the community. We welcome contributions from anyone interested in improving the app. To contribute, please review our [contributing guidelines](CONTRIBUTING.md) which include information on our development workflow and commit message standards.

## License

This project is licensed under the BSD 3-Clause License. See the [LICENSE](LICENSE) file for the full text.
