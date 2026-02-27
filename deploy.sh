#!/usr/bin/env bash
set -euo pipefail

PROJECT="Orbit"
JAR="build/libs/Orbit.jar"

echo "[$PROJECT] Building shadow jar..."
./gradlew clean shadowJar --quiet

if [ ! -f "$JAR" ]; then
  echo "[$PROJECT] ERROR: $JAR not found" >&2
  exit 1
fi

echo "[$PROJECT] Uploading to GitHub Releases (tag: latest)..."
gh release upload latest "$JAR" --clobber

echo "[$PROJECT] Deployed successfully."
