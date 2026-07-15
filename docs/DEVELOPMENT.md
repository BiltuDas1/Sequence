# Local Development

## Required Secrets

The following files are excluded from the repository and required for building:

### 1. `google-services.json`
Required for Firebase FCM and Auth.
- **Package Name**: `com.github.biltudas1.sequence`
- **Placement**: `app/google-services.json`

### 2. `keystore.properties`
Required for signing. Create in the root directory.
- **Fields**: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- **Template**: See `examples/keystore.properties.example`.

## Build Commands
- **Assemble Debug**: `./gradlew assembleDebug`
- **Assemble Release**: `./gradlew assembleRelease`
- **Run Tests**: `./gradlew test`
