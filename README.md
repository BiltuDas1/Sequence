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

To understand the complex interaction between Firebase pushes, WebSockets, and WebRTC, please review the [Sequence Architecture & Call Flow](docs/ARCHITECTURE.md).

## Help and Support

If you experience any issues or have questions about the application, please open a new issue in the GitHub repository. Provide as much detail as possible about your environment and the problem you are facing to help us assist you effectively.

## Maintenance and Contributing

Sequence is maintained by the community. We welcome contributions from anyone interested in improving the app. To contribute, please review our [contributing guidelines](CONTRIBUTING.md) which include information on our development workflow and commit message standards.

## License

This project is licensed under the BSD 3-Clause License. See the [LICENSE](LICENSE) file for the full text.
