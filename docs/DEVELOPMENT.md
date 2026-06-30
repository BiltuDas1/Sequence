# Local Development

This document provides instructions for setting up your local development environment and building the Sequence Android application.

## Prerequisites

To build and run the project, ensure you have the following installed:

- **Android Studio**: Latest stable version is recommended.
- **JDK 21**: The project uses Java 21 features. Ensure your Gradle JDK is set to JDK 21 in Android Studio Settings.
- **Android SDK**: API Level 37 (Android 15) is required for compilation.

## Files to Collect

Certain configuration files are excluded from the repository for security and must be added manually to build the project successfully.

### 1. `google-services.json`
This file is required for Firebase services (Cloud Messaging and Authentication). The project will fail to build if this file is missing because the Google Services plugin is applied.

- **How to obtain**:
  1. Go to the [Firebase Console](https://console.firebase.google.com/).
  2. Create a new project (or use an existing one).
  3. Register an Android app with the package name `com.github.biltudas1.sequence`.
  4. Download the `google-services.json` file.
- **Placement**: Place it in the `app/` directory of the project:
  `app/google-services.json`

### 2. Keystore File (`.jks`)
The actual cryptographic file used for signing.

- **How to obtain**: You can generate a new one in Android Studio via **Build > Generate Signed Bundle / APK...** or use the `keytool` command-line utility.
- **Placement**: Place it in a secure location and ensure the `storeFile` path in `keystore.properties` points to it correctly.

### 3. `keystore.properties` (Required for Release builds)
This file contains credentials used to sign the production version of the app. Without this, you cannot build the `release` variant.

- **How to create**: Create a plain text file named `keystore.properties` in the root directory. You can use the [example file](../examples/keystore.properties.example) as a template for the required fields (`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`).

## How to Build

### Using Android Studio
- Select the `app` run configuration in the toolbar.
- Click the **Run** button (or press `Shift + F10`) to build and deploy the debug version to a device or emulator.
- Use **Build > Build APK(s)** to generate APKs manually.

### Using Command Line
From the project root directory, use the Gradle wrapper:

- **Build Debug APK**:

  ```sh
  ./gradlew assembleDebug
  ```
- **Build Release APK**:

  ```sh
  ./gradlew assembleRelease
  ```
- **Run Unit Tests**:

  ```sh
  ./gradlew test
  ```
