#!/bin/bash
set -e

PROPERTIES_FILE="gradle.properties"

# Extract sequence.versionName from gradle.properties
# This looks for sequence.versionName=... and removes anything before and including the =
VERSION=$(grep -E '^sequence.versionName\s*=' "$PROPERTIES_FILE" | cut -d'=' -f2 | xargs)

# Fallback check if VERSION is empty
if [ -z "$VERSION" ]; then
  echo "Error: Could not find sequence.versionName in $PROPERTIES_FILE"
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