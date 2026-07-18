#!/bin/bash

# Ensure we wait for the device to be fully ready
bash .github/scripts/wait_for_emulator.sh

echo "Suppressing system crash and ANR dialogs..."
adb shell settings put global hide_error_dialogs 1

echo "Disabling cellular data to prevent network drops..."
adb shell svc data disable

echo "Routing emulator port 8888 to Docker host network layer..."
adb reverse tcp:8888 tcp:8888

echo "Installing APK..."
adb install app/build/outputs/apk/debug/app-debug.apk

echo "Starting Maestro Tests..."
MAESTRO_EXIT_CODE=0
$HOME/.maestro/bin/maestro test .maestro/ || MAESTRO_EXIT_CODE=$?

echo "Maestro finished with exit code: $MAESTRO_EXIT_CODE"

echo "Extracting log file from internal app storage..."
adb shell "run-as com.github.biltudas1.sequence cat /data/user/0/com.github.biltudas1.sequence/files/logs/app_logs.txt" > ./app-internal-output.log || echo "Failed to extract log file"

exit $MAESTRO_EXIT_CODE
