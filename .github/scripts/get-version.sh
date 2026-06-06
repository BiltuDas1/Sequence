#!/bin/bash
set -e

GRADLE_FILE="app/build.gradle.kts"

# Extract versionName using grep and sed
# This looks for lines matching: versionName = "..." or versionName = '...'
VERSION=$(grep -E 'versionName\s*=\s*["'"'"']' "$GRADLE_FILE" | sed -E 's/.*versionName\s*=\s*["'"'"']([^"'"'"']+)["'"'"'].*/\1/')

# Fallback check if VERSION is empty
if [ -z "$VERSION" ]; then
  echo "Error: Could not find versionName in $GRADLE_FILE"
  exit 1
fi

IS_PRERELEASE="false"

# Check for pre-release indicators
if [[ "$VERSION" =~ [ab] ]] || [[ "$VERSION" =~ "rc" ]] || [[ "$VERSION" == *"alpha"* ]] || [[ "$VERSION" == *"beta"* ]]; then
  IS_PRERELEASE="true"
fi

# Exporting to GITHUB_OUTPUT
echo "VERSION=$VERSION" >> $GITHUB_OUTPUT
echo "IS_PRERELEASE=$IS_PRERELEASE" >> $GITHUB_OUTPUT