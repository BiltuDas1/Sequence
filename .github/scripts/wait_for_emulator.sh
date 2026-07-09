#!/bin/bash

echo "Waiting for emulator core to be ready..."
adb wait-for-device

echo "Waiting for Android system boot to complete..."
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
  sleep 2
done

echo "Emulator is fully booted"
