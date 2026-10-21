#!/usr/bin/env bash
# Downloads and verifies the latest stable SmartTube APK from official GitHub release
set -euo pipefail

OUT_DIR="$(dirname "$0")/../build/downloads"
mkdir -p "$OUT_DIR"
TARGET="$OUT_DIR/smarttube_stable.apk"
URL="https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk"

echo "==> Fetching latest official SmartTube stable release..."
if command -v curl >/dev/null 2>&1; then
    curl -L --fail --progress-bar -o "$TARGET" "$URL"
elif command -v wget >/dev/null 2>&1; then
    wget -q --show-progress -O "$TARGET" "$URL"
else
    echo "Error: Neither curl nor wget found. Please install curl or wget." >&2
    exit 1
fi

# Verify the downloaded file is a valid Android APK (zip archive containing AndroidManifest.xml)
if unzip -t "$TARGET" AndroidManifest.xml >/dev/null 2>&1; then
    SIZE=$(du -h "$TARGET" | cut -f1)
    echo "==> Successfully downloaded & verified SmartTube APK ($SIZE): $TARGET"
else
    echo "Error: Downloaded file is corrupted or not a valid APK." >&2
    rm -f "$TARGET"
    exit 1
fi

# If a connected ADB device is available or TV_HOST is provided, offer immediate installation
if [ -n "${1:-}" ]; then
    echo "==> Installing SmartTube to target device ($1)..."
    adb -s "$1" install -r "$TARGET"
    echo "==> SmartTube successfully installed!"
elif adb devices | grep -E '\b(device)\b' >/dev/null 2>&1; then
    FIRST_DEV=$(adb devices | grep -E '\b(device)\b' | head -n 1 | awk '{print $1}')
    echo "==> Installing SmartTube to connected device ($FIRST_DEV)..."
    adb -s "$FIRST_DEV" install -r "$TARGET"
    echo "==> SmartTube successfully installed!"
fi
