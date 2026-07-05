#!/usr/bin/env bash
set -euo pipefail
GRADLE_VERSION="9.6.1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$ROOT_DIR/.gradle/bootstrap/gradle-$GRADLE_VERSION"
DIST_ZIP="$ROOT_DIR/.gradle/bootstrap/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_BIN="$DIST_DIR/bin/gradle"

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$ROOT_DIR/.gradle/bootstrap"
  if [ ! -f "$DIST_ZIP" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIST_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O "$DIST_ZIP"
    else
      echo "Gradle is not installed and neither curl nor wget is available." >&2
      exit 1
    fi
  fi
  unzip -q -o "$DIST_ZIP" -d "$ROOT_DIR/.gradle/bootstrap"
fi

exec "$GRADLE_BIN" "$@"
