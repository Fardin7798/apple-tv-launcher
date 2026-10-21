#!/usr/bin/env bash
# restore.sh — Complete System State & Home Restoration Engine
# Reverts debloat, restores original Home launcher, resets animation scales,
# and optionally uninstalls Apple TV Launcher / SmartTube.
set -euo pipefail

TARGET_PATH="${1:-}"
CLI_DEV="${2:-}"

if [ -z "$TARGET_PATH" ]; then
    echo "Usage: $0 <backup_directory_or_txt_file> [target_device_ip]"
    echo ""
    echo "Examples:"
    echo "  $0 backups/192.168.1.9_5555_original"
    echo "  $0 backups/192.168.1.9_5555_20261021_200333"
    echo "  $0 backups/backup_20261021_120000.txt 192.168.1.9:5555"
    exit 1
fi

if [ ! -e "$TARGET_PATH" ]; then
    echo "Error: Target path not found: $TARGET_PATH" >&2
    exit 1
fi

echo "=========================================================="
echo "          🔄 Android TV Full System Restoration           "
echo "=========================================================="

# ── If Target Path is a Snapshot Directory ──────────────────────
if [ -d "$TARGET_PATH" ] && [ -f "$TARGET_PATH/metadata.env" ]; then
    echo "==> Loading state metadata from: $TARGET_PATH/metadata.env"
    # Source metadata safely
    # shellcheck source=/dev/null
    source "$TARGET_PATH/metadata.env"

    DEV="${CLI_DEV:-${TARGET_DEV:-}}"
    if [ -z "$DEV" ]; then
        DEV=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' | head -n 1 || true)
    fi
    if [ -z "$DEV" ]; then
        echo "Error: No ADB device connected. Connect to your TV first." >&2
        exit 1
    fi

    echo "==> Target TV: $DEV (${MODEL:-TV} - Android ${RELEASE:-unknown})"
    echo ""

    # 1. Re-enable Packages
    PKG_LIST="$TARGET_PATH/packages_enabled.txt"
    if [ -f "$PKG_LIST" ]; then
        echo "==> [1/5] Re-enabling previously enabled system & user packages..."
        RESTORED=0
        while IFS= read -r pkg || [ -n "$pkg" ]; do
            pkg=$(echo "$pkg" | tr -d '\r\n ' || true)
            [ -z "$pkg" ] && continue
            adb -s "$DEV" shell pm enable --user 0 "$pkg" >/dev/null 2>&1 || true
            RESTORED=$((RESTORED + 1))
        done < "$PKG_LIST"
        echo "    Re-enabled $RESTORED packages."
    fi

    # Also re-enable any recorded in changelog.txt
    if [ -f "$TARGET_PATH/changelog.txt" ]; then
        grep '^DISABLED:' "$TARGET_PATH/changelog.txt" 2>/dev/null | awk '{print $2}' | while IFS= read -r pkg; do
            [ -n "$pkg" ] && adb -s "$DEV" shell pm enable --user 0 "$pkg" >/dev/null 2>&1 || true
        done || true
    fi

    # 2. Re-enable Stock YouTube
    echo "==> [2/5] Restoring stock YouTube packages..."
    adb -s "$DEV" shell pm enable --user 0 com.google.android.youtube.tv >/dev/null 2>&1 || true
    adb -s "$DEV" shell pm enable --user 0 com.google.android.youtube.tvmusic >/dev/null 2>&1 || true

    # 3. Restore Original Home Launcher
    echo "==> [3/5] Restoring original Home launcher..."
    if [ -n "${ORIG_HOME_PKG:-}" ] && [ "$ORIG_HOME_PKG" != "com.appletv.launcher" ] && [ "$ORIG_HOME_PKG" != "none" ]; then
        echo "    Re-enabling stock launcher: $ORIG_HOME_PKG"
        adb -s "$DEV" shell pm enable --user 0 "$ORIG_HOME_PKG" >/dev/null 2>&1 || true
        if [ -n "${ORIG_HOME_RES:-}" ]; then
            echo "    Setting Home activity: $ORIG_HOME_RES"
            adb -s "$DEV" shell cmd package set-home-activity --user 0 "$ORIG_HOME_RES" >/dev/null 2>&1 || true
        fi
        HOME_NOW=$(adb -s "$DEV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
        echo "    Current Home owner: $HOME_NOW"
    else
        echo "    Original Home package was not different or already stock."
    fi

    # 4. Revert Animation Scale Settings
    echo "==> [4/5] Restoring system animation scales..."
    adb -s "$DEV" shell settings put global window_animation_scale "${WINDOW_ANIMATION_SCALE:-1.0}" >/dev/null 2>&1 || true
    adb -s "$DEV" shell settings put global transition_animation_scale "${TRANSITION_ANIMATION_SCALE:-1.0}" >/dev/null 2>&1 || true
    adb -s "$DEV" shell settings put global animator_duration_scale "${ANIMATOR_DURATION_SCALE:-1.0}" >/dev/null 2>&1 || true

    # 5. Optional Clean Uninstall (Interactive)
    echo "==> [5/5] Optional App Cleanup..."
    if [ -t 0 ]; then
        read -r -p "Do you want to uninstall Apple TV Launcher from TV? [y/N]: " REMOVE_LAUNCHER
        case "$REMOVE_LAUNCHER" in
            [yY][eE][sS]|[yY])
                echo "    Uninstalling com.appletv.launcher..."
                adb -s "$DEV" shell pm uninstall com.appletv.launcher >/dev/null 2>&1 || true
                ;;
        esac

        read -r -p "Do you want to uninstall SmartTube from TV? [y/N]: " REMOVE_SMARTTUBE
        case "$REMOVE_SMARTTUBE" in
            [yY][eE][sS]|[yY])
                echo "    Uninstalling SmartTube..."
                for st_pkg in com.teamsmart.videomanager.tv com.liskovsoft.videomanager org.smarttube.stable; do
                    adb -s "$DEV" shell pm uninstall "$st_pkg" >/dev/null 2>&1 || true
                done
                ;;
        esac
    fi

    # Test Home Screen
    adb -s "$DEV" shell input keyevent 3 >/dev/null 2>&1 || true

    echo ""
    echo "=========================================================="
    echo "  ✅ Restoration complete! TV is restored to original state."
    echo "=========================================================="
    exit 0
fi

# ── Fallback: Legacy Text File Package Restore ───────────────────
echo "==> Restoring from package list file: $TARGET_PATH"
DEV="${CLI_DEV:-}"
if [ -z "$DEV" ]; then
    DEV=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' | head -n 1 || true)
fi
if [ -z "$DEV" ]; then
    echo "Error: No ADB device connected." >&2
    exit 1
fi

RESTORED=0
while IFS= read -r line || [ -n "$line" ]; do
    PKG=$(echo "$line" | sed -e 's/#.*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    if [ -n "$PKG" ]; then
        adb -s "$DEV" shell pm enable --user 0 "$PKG" >/dev/null 2>&1 || true
        RESTORED=$((RESTORED + 1))
    fi
done < "$TARGET_PATH"

echo "==> Successfully processed $RESTORED packages."
echo "==> System packages have been re-enabled."
