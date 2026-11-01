#!/usr/bin/env bash
# backup.sh — Captures complete device state snapshot without root
# Usage: ./tools/backup.sh <TARGET_DEV> [TAG]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TARGET_DEV="${1:-}"
if [ -z "$TARGET_DEV" ]; then
    TARGET_DEV=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' | head -n 1 || true)
fi
if [ -z "$TARGET_DEV" ]; then
    echo "Error: No ADB device specified or connected." >&2
    exit 1
fi

TAG="${2:-$(date +"%Y%m%d_%H%M%S")}"

# Sanitize device ID for folder name
CLEAN_DEV=$(printf '%s' "$TARGET_DEV" | tr -c 'A-Za-z0-9._-' '_')
BACKUP_BASE="$ROOT_DIR/backups"
mkdir -p "$BACKUP_BASE"

# If tag is "original" and original backup folder already exists, NEVER overwrite it!
SNAPSHOT_DIR="$BACKUP_BASE/${CLEAN_DEV}_${TAG}"
if [ "$TAG" = "original" ] && [ -d "$SNAPSHOT_DIR" ] && [ -f "$SNAPSHOT_DIR/metadata.env" ]; then
    echo "==> Baseline original backup already exists: $SNAPSHOT_DIR (preserved untouched)."
    echo "$SNAPSHOT_DIR"
    exit 0
fi

mkdir -p "$SNAPSHOT_DIR"
echo "==> Creating full system snapshot: $SNAPSHOT_DIR"

# 1. Device Identity & Hardware Metadata
MFR=$(adb -s "$TARGET_DEV" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' || echo "unknown")
MODEL=$(adb -s "$TARGET_DEV" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "unknown")
SDK=$(adb -s "$TARGET_DEV" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "0")
RELEASE=$(adb -s "$TARGET_DEV" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "unknown")

# 2. Current Home Activity & Package
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
HOME_RES=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' || true)
HOME_PKG=$(echo "$HOME_RES" | cut -d/ -f1 || true)
HOME_ACT=$(echo "$HOME_RES" | cut -d/ -f2 || true)

# 3. Animation Scale Settings
W_SCALE=$(adb -s "$TARGET_DEV" shell settings get global window_animation_scale 2>/dev/null | tr -d '\r' || echo "1.0")
T_SCALE=$(adb -s "$TARGET_DEV" shell settings get global transition_animation_scale 2>/dev/null | tr -d '\r' || echo "1.0")
A_SCALE=$(adb -s "$TARGET_DEV" shell settings get global animator_duration_scale 2>/dev/null | tr -d '\r' || echo "1.0")

# Write metadata.env
cat << META > "$SNAPSHOT_DIR/metadata.env"
TARGET_DEV="$TARGET_DEV"
MFR="$MFR"
MODEL="$MODEL"
SDK="$SDK"
RELEASE="$RELEASE"
ORIG_HOME_RES="$HOME_RES"
ORIG_HOME_PKG="$HOME_PKG"
ORIG_HOME_ACT="$HOME_ACT"
WINDOW_ANIMATION_SCALE="$W_SCALE"
TRANSITION_ANIMATION_SCALE="$T_SCALE"
ANIMATOR_DURATION_SCALE="$A_SCALE"
SNAPSHOT_TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
TAG="$TAG"
META

# 4. Package Lists
echo "    Capturing package lists..."
adb -s "$TARGET_DEV" shell pm list packages -e 2>/dev/null | sed 's/package://' | sort > "$SNAPSHOT_DIR/packages_enabled.txt" || true
adb -s "$TARGET_DEV" shell pm list packages -d 2>/dev/null | sed 's/package://' | sort > "$SNAPSHOT_DIR/packages_disabled.txt" || true
adb -s "$TARGET_DEV" shell pm list packages -s 2>/dev/null | sed 's/package://' | sort > "$SNAPSHOT_DIR/packages_system.txt" || true
adb -s "$TARGET_DEV" shell pm list packages -3 2>/dev/null | sed 's/package://' | sort > "$SNAPSHOT_DIR/packages_user.txt" || true

# 5. Global Settings Dump
echo "    Capturing system settings..."
adb -s "$TARGET_DEV" shell settings list global 2>/dev/null | sort > "$SNAPSHOT_DIR/settings_global.txt" || true
adb -s "$TARGET_DEV" shell settings list secure 2>/dev/null | sort > "$SNAPSHOT_DIR/settings_secure.txt" || true
adb -s "$TARGET_DEV" shell settings list system 2>/dev/null | sort > "$SNAPSHOT_DIR/settings_system.txt" || true

# 6. Initialize empty changelog
touch "$SNAPSHOT_DIR/changelog.txt"

echo "✅ Snapshot created successfully at: $SNAPSHOT_DIR"
echo "$SNAPSHOT_DIR"
