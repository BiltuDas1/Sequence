# Self-Hosting & Infrastructure Guide

The Sequence backend signaling server is provided as a highly optimized, closed-source Docker image for users who wish to host their own infrastructure.

## Docker Image

The official Docker image is available on GitHub Container Registry:

```bash
ghcr.io/biltudas1/sequence:latest
```

## Configuration

The server can be configured using the following environment variables:

| Variable | Description | Default |
| :--- | :--- | :--- |
| `PORT` | Sets the port the server listens on. | `8888` |
| `DOCS` | If set to `1`, exposes the `/docs` endpoint for API documentation (Scalar). | Disabled |
| `HOST_ADDRESS` | Configures the server address for terminal display and auto-config QR code. | - |

### Note on `HOST_ADDRESS`
The `HOST_ADDRESS` variable affects both the terminal output and the auto-configuration QR code:

- **Terminal Output:** Displays the configured URL for reference (e.g., `[Network Access: https://example.com]`).
- **QR Code Configuration:** The server generates a QR code in the terminal with the format `seq://<address>`. This allows the Sequence mobile app to instantly configure server settings by scanning the code.

#### Address Resolution:
- If set to `https://example.com`, it uses `https://example.com`.
- If set to `http://example.com`, it uses `http://example.com`.
- If set to `example.com`, it defaults to `http://example.com:8888` (or the configured `PORT`).

## Persistence

The `/data` partition **must** be mounted to a persistent volume. This directory contains the databases required for the server to function and persist data across container restarts.

## Security

The image is hardened and supports running in **read-only mode**, provided that the `/data` volume is correctly mounted and writable.

## Deployment Examples

### Docker Run

```bash
docker run -d \
  --name sequence-server \
  -p 8888:8888 \
  -e DOCS=1 \
  -v sequence_data:/data \
  --read-only \
  ghcr.io/biltudas1/sequence:latest
```

### Docker Compose

For a production-ready setup, refer to the [Docker Compose example](../examples/docker-compose.yml.example) which demonstrates how to configure the server using environment variables and persistent volume mounts.
