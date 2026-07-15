# In-App Updates

## Core Components
1. **`UpdateWorker`**: Checks for new release tags via `VersionService`.
2. **`UpdateDownloadService`**: Handles file download via OkHttp. Runs as a `dataSync` foreground service.
3. **`UpdateInstallReceiver`**: Triggers the system package installer using `FileProvider` URIs.

## Process Flow
Check (`WorkManager`) -> Notify (`NotificationHelper`) -> Download (`Service`) -> Install (`Receiver`).
