#!/bin/bash
set -e

echo "## Changelog" > changelog.md
git fetch --tags
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)

if [ -n "$LATEST_TAG" ]; then
  git log $LATEST_TAG..HEAD --pretty=format:"* %s (%h)" >> changelog.md
else
  git log --pretty=format:"* %s (%h)" >> changelog.md
fi