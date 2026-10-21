#!/usr/bin/env bash
# Downloads and verifies the latest stable SmartTube APK from official GitHub release
set -euo pipefail

TARGET_DEV="${1:-}"
if [ -z "$TARGET_DEV" ]; then
    DEVICES=$(adb devices 2>/dev/null | grep -E '\b(device)\b' | awk '{print $1}' || true)
    if [ -n "$DEVICES" ]; then
        TARGET_DEV=$(echo "$DEVICES" | head -n 1)
    fi
fi

# Check if SmartTube is already installed on the target device
if [ -n "$TARGET_DEV" ]; then
    if adb -s "$TARGET_DEV" shell pm list packages 2>/dev/null | grep -E 'org\.smarttube\.stable|com\.teamsmart\.videomanager\.tv' >/dev/null 2>&1; then
        echo "==> SmartTube is already installed on target device ($TARGET_DEV)."
        exit 0
    fi
fi

OUT_DIR="$(dirname "$0")/../build/downloads"
mkdir -p "$OUT_DIR"
TARGET="$OUT_DIR/smarttube_stable.apk"
URL="https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk"

# Check if valid cached APK exists
if [ -f "$TARGET" ] && unzip -t "$TARGET" AndroidManifest.xml >/dev/null 2>&1; then
    SIZE=$(du -h "$TARGET" | cut -f1)
    echo "==> Using cached SmartTube APK ($SIZE): $TARGET"
else
    echo "==> Fetching latest official SmartTube stable release..."
    if command -v curl >/dev/null 2>&1; then
        curl -sSL -f -o "$TARGET" "$URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$TARGET" "$URL"
    else
        echo "Error: Neither curl nor wget found. Please install curl or wget." >&2
        exit 1
    fi

    # Verify APK integrity
    if unzip -t "$TARGET" AndroidManifest.xml >/dev/null 2>&1; then
        SIZE=$(du -h "$TARGET" | cut -f1)
        echo "==> Downloaded & verified SmartTube APK ($SIZE): $TARGET"
    else
        echo "Error: Downloaded file is corrupted or not a valid APK." >&2
        rm -f "$TARGET"
        exit 1
    fi
fi

# Install to target device
if [ -n "$TARGET_DEV" ]; then
    echo "==> Installing SmartTube to target device ($TARGET_DEV)..."
    adb -s "$TARGET_DEV" install -r "$TARGET"
    echo "==> SmartTube successfully installed!"
fi
