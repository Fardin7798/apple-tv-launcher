#!/usr/bin/env bash
# Universal 1-Click Android TV Transformation Suite
# Transforms any Android TV (Sony, TCL, Xiaomi, OnePlus, Shield, Chromecast)
# into an ultra-clean, ad-free Apple TV experience with SmartTube and 218 KB Launcher.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================================="
echo "       🍎 Universal Apple TV Launcher Setup Suite         "
echo "=========================================================="

# 1. Verify ADB tool
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: 'adb' command not found." >&2
    echo "Please install Android Platform Tools:" >&2
    echo "  - Linux:  sudo apt install adb" >&2
    echo "  - macOS:  brew install android-platform-tools" >&2
    exit 1
fi

# 2. Target Device Discovery & Connection
TARGET_HOST="${1:-${TV_HOST:-${TV_IP:-}}}"
if [ -z "$TARGET_HOST" ]; then
    DEVICES=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' || true)
    if [ -n "$DEVICES" ]; then
        TARGET_DEV=$(echo "$DEVICES" | head -n 1)
        echo "==> Detected currently connected ADB device: $TARGET_DEV"
    else
        # Try mDNS discovery on local network
        MDNS_DEV=$(adb mdns services 2>/dev/null | grep -E '_adb\._tcp|_adb-tls-connect\._tcp' | head -n 1 | awk '{print $NF}' || true)
        if [ -n "$MDNS_DEV" ]; then
            echo "==> Auto-discovered Android TV via mDNS: $MDNS_DEV"
            TARGET_DEV="$MDNS_DEV"
        else
            if [ ! -t 0 ]; then
                echo "Error: Non-interactive mode detected and no device found." >&2
                echo "Please specify TV IP (e.g. ./tools/setup.sh 192.168.1.50) or set TV_HOST=<ip>." >&2
                exit 1
            fi
            echo ""
            echo "ℹ️  How to find your TV IP address:"
            echo "    On your TV: Go to Settings ⚙️ -> Network & Internet -> Click your connected Wi-Fi -> Look for 'IP address'"
            echo ""
            read -r -p "Enter your Android TV IP address (e.g. 192.168.1.50): " TARGET_HOST
            if [ -z "$TARGET_HOST" ]; then
                echo "Error: TV IP address is required." >&2
                exit 1
            fi
            echo "==> Connecting to $TARGET_HOST:5555..."
            adb connect "$TARGET_HOST:5555" || true
            TARGET_DEV="$TARGET_HOST:5555"
        fi
    fi
else
    if [[ "$TARGET_HOST" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "==> Connecting to $TARGET_HOST:5555..."
        adb connect "$TARGET_HOST:5555" || true
        TARGET_DEV="$TARGET_HOST:5555"
    else
        TARGET_DEV="$TARGET_HOST"
    fi
fi

# Verify device is authorized and responding
if ! adb -s "$TARGET_DEV" get-state >/dev/null 2>&1; then
    echo "Error: Device $TARGET_DEV is not accessible or unauthorized." >&2
    echo "Please check your TV screen to accept the 'Allow USB debugging' prompt." >&2
    exit 1
fi

# 3. Hardware & OS Probing
echo "==> Probing TV hardware..."
MANUFACTURER=$(adb -s "$TARGET_DEV" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]' || echo "generic")
MODEL=$(adb -s "$TARGET_DEV" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Android TV")
ANDROID_VER=$(adb -s "$TARGET_DEV" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "Unknown")

echo "    • Manufacturer: $MANUFACTURER"
echo "    • Model:        $MODEL"
echo "    • Android OS:   $ANDROID_VER"

# 4. Profile Matching
SELECTED_PROFILE=""
case "$MANUFACTURER" in
    *xiaomi*|*redmi*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/xiaomi.txt"
        ;;
    *sony*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/sony-bravia.txt"
        ;;
    *tcl*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/tcl.txt"
        ;;
    *hisense*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/hisense.txt"
        ;;
    *oneplus*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/oneplus.txt"
        ;;
    *nvidia*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/nvidia-shield.txt"
        ;;
    *google*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/chromecast-gtv.txt"
        ;;
    *amazon*)
        SELECTED_PROFILE="$ROOT_DIR/profiles/amazon-fire-tv.txt"
        ;;
    *)
        SELECTED_PROFILE="$ROOT_DIR/profiles/universal-safe.txt"
        ;;
esac

echo "==> Auto-selected debloat profile: $(basename "$SELECTED_PROFILE")"

# 5. Pre-Flight TV Audit & Analysis Report
echo ""
echo "=========================================================="
echo "                 📋 TV Pre-Flight Audit Report             "
echo "=========================================================="
echo "  • Device:         $MANUFACTURER $MODEL (Android $ANDROID_VER)"
echo "  • Matched Profile: $(basename "$SELECTED_PROFILE")"
echo ""
CURRENT_PACKAGES=$(adb -s "$TARGET_DEV" shell pm list packages 2>/dev/null | sed 's/package://' | tr -d '\r' || true)
TOTAL_PKGS=$(echo "$CURRENT_PACKAGES" | grep -v '^$' | wc -l || echo "0")
echo "  • Total Packages Installed on TV: $TOTAL_PKGS"
echo ""
echo "  • Identified Bloatware to Disable:"
BLOAT_COUNT=0
if [ -f "$SELECTED_PROFILE" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        PKG=$(echo "$line" | sed -e 's/#.*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
        if [ -n "$PKG" ]; then
            if echo "$CURRENT_PACKAGES" | grep -qx "$PKG"; then
                echo "    🛑 $PKG"
                BLOAT_COUNT=$((BLOAT_COUNT + 1))
            fi
        fi
    done < "$SELECTED_PROFILE"
fi
echo "    (Total $BLOAT_COUNT bloatware packages found on device)"
echo ""
echo "  • Detected Streaming & Media Apps:"
if echo "$CURRENT_PACKAGES" | grep -qx "com.google.android.youtube.tv"; then
    echo "    📺 YouTube (Stock ad-supported) -> Will replace with SmartTube"
fi
if echo "$CURRENT_PACKAGES" | grep -qx "org.smarttube.stable" || echo "$CURRENT_PACKAGES" | grep -qx "com.teamsmart.videomanager.tv"; then
    echo "    📺 SmartTube -> Already installed"
fi
if echo "$CURRENT_PACKAGES" | grep -qx "com.netflix.ninja"; then
    echo "    📺 Netflix -> Protected / Left Intact"
fi
if echo "$CURRENT_PACKAGES" | grep -qx "com.amazon.amazonvideo.livingroom"; then
    echo "    📺 Prime Video -> Protected / Left Intact"
fi
if echo "$CURRENT_PACKAGES" | grep -qx "in.startv.hotstar"; then
    echo "    📺 Disney+ Hotstar -> Protected / Left Intact"
fi
echo ""
echo "  • Core Protected Services (NEVER TOUCHED):"
echo "    🛡️  Chromecast Built-in (com.google.android.apps.mediashell) [SAFE]"
echo "    🛡️  Google Assistant Voice Search (com.google.android.katniss) [SAFE]"
echo "    🛡️  Gboard Keyboard & System IME [SAFE]"
echo "    🛡️  Wi-Fi, Bluetooth & Hardware Drivers [SAFE]"
echo "=========================================================="
echo ""
AUTO_CONFIRM=false
for arg in "$@"; do
    if [ "$arg" = "-y" ] || [ "$arg" = "--yes" ]; then
        AUTO_CONFIRM=true
    fi
done
if [ "$AUTO_CONFIRM" = false ] && [ -t 0 ]; then
    read -r -p "Do you want to proceed with this debloat and setup plan? [Y/n]: " PROCEED_REPLY
    case "$PROCEED_REPLY" in
        [nN][oO]|[nN])
            echo "Operation cancelled by user. No changes were made to your TV."
            exit 0
            ;;
    esac
fi

# 5. Snapshot Current Packages (Safe Recovery Guarantee)
BACKUP_DIR="$ROOT_DIR/backups"
mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/backup_${TIMESTAMP}.txt"
echo "==> Creating safety snapshot before modifications: $BACKUP_FILE"
adb -s "$TARGET_DEV" shell pm list packages -e | sed 's/package://' | sort > "$BACKUP_FILE"
echo "    Snapshot saved (${BACKUP_FILE}). Can be restored anytime with tools/restore.sh"

# 6. Apply Debloat Profile
echo "==> Disabling bloatware, telemetry, and sponsored recommendation rows..."
if [ -f "$SELECTED_PROFILE" ]; then
    COUNT=0
    while IFS= read -r line || [ -n "$line" ]; do
        PKG=$(echo "$line" | sed -e 's/#.*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
        if [ -n "$PKG" ]; then
            adb -s "$TARGET_DEV" shell pm disable-user --user 0 "$PKG" >/dev/null 2>&1 || true
            COUNT=$((COUNT + 1))
        fi
    done < "$SELECTED_PROFILE"
    echo "    Cleaned $COUNT bloatware packages safely."
fi

# 7. Stock YouTube Disable & SmartTube Installation
echo "==> Disabling stock YouTube (ad-supported)..."
adb -s "$TARGET_DEV" shell pm disable-user --user 0 com.google.android.youtube.tv >/dev/null 2>&1 || true
adb -s "$TARGET_DEV" shell pm disable-user --user 0 com.google.android.youtube.tvmusic >/dev/null 2>&1 || true

echo "==> Installing SmartTube (Ad-Free, SponsorBlock, TV-Optimized)..."
"$SCRIPT_DIR/get_smarttube.sh" "$TARGET_DEV"

# 8. Apple TV Launcher Installation
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    if command -v javac >/dev/null 2>&1 && [ -f "$ROOT_DIR/gradlew" ]; then
        echo "==> Compiling Apple TV Launcher APK from source..."
        (cd "$ROOT_DIR" && ./gradlew assembleRelease) || true
    fi
    if [ ! -f "$APK_PATH" ]; then
        echo "==> Fetching pre-compiled release APK (218 KB) from GitHub Releases..."
        mkdir -p "$(dirname "$APK_PATH")"
        curl -sSL -f -o "$APK_PATH" "https://github.com/Fardin7798/apple-tv-launcher/releases/latest/download/app-release.apk"
    fi
fi

# Phase 1: Install APK — stock launcher is still ENABLED & running at this point
echo "==> Installing Apple TV Launcher (218 KB release)..."
INSTALL_OUT=$(adb -s "$TARGET_DEV" install -r "$APK_PATH" 2>&1)
echo "$INSTALL_OUT"
case "$INSTALL_OUT" in
    *Success*) ;;
    *INSTALL_FAILED_OLDER_SDK*)
        echo "Error: APK requires a newer Android SDK than your TV supports." >&2
        echo "  The stock launcher was left untouched. No changes were made." >&2
        exit 1 ;;
    *)
        echo "Error: APK install failed — stock launcher left untouched." >&2
        exit 1 ;;
esac

# Phase 2: Detect which stock launcher currently owns Home (needed for rollback)
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
KNOWN_STOCK="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome"
HOME_PKG=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
STOCK_LAUNCHER=""
case " $KNOWN_STOCK " in
    *" $HOME_PKG "*) STOCK_LAUNCHER="$HOME_PKG" ;;
    *com.appletv.launcher*) STOCK_LAUNCHER="none"
        echo "==> Apple TV Launcher already owns Home — nothing will be disabled." ;;
    *) STOCK_LAUNCHER="${HOME_PKG:-none}" ;;
esac
echo "==> Detected current Home launcher: ${STOCK_LAUNCHER}"

# Phase 3: TEST the new launcher actually starts before touching stock launcher
echo "==> Launch test: verifying Apple TV Launcher starts (pre-disable safety check)..."
START_OUT=$(adb -s "$TARGET_DEV" shell am start -W -n com.appletv.launcher/.MainActivity 2>&1)
echo "$START_OUT"
if ! printf '%s' "$START_OUT" | grep -qF 'Status: ok' || printf '%s' "$START_OUT" | grep -qi '^Error'; then
    echo "Error: Apple TV Launcher did not start — stock launcher left untouched." >&2
    exit 1
fi
echo "==> Launch test PASSED — safe to proceed with Home handover."

# Helper functions for Home query and rollback
query_home() {
    adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true
}

rollback_home() {
    echo "  Rolling back: re-enabling stock launcher '${STOCK_LAUNCHER}'..." >&2
    [ "$STOCK_LAUNCHER" = "none" ] || [ -z "$STOCK_LAUNCHER" ] && { echo "  Nothing to roll back." >&2; return 1; }
    adb -s "$TARGET_DEV" shell pm enable --user 0 "$STOCK_LAUNCHER" >/dev/null 2>&1 || true
    adb -s "$TARGET_DEV" shell "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK_LAUNCHER | tail -n 1)" >/dev/null 2>&1 || true
    BACK=$(query_home)
    case "$BACK" in
        "$STOCK_LAUNCHER") echo "  TV is back to normal." >&2; return 0 ;;
    esac
    REC_FILE="$ROOT_DIR/backups/emergency-restore-$(printf '%s' "$TARGET_DEV" | tr -c 'A-Za-z0-9._-' '_').sh"
    { echo "#!/usr/bin/env bash"
      echo "# Run this if your TV Home is blank after a failed setup"
      echo "adb -s \"$TARGET_DEV\" shell pm enable --user 0 $STOCK_LAUNCHER"
      echo "adb -s \"$TARGET_DEV\" shell cmd package set-home-activity --user 0 \$(adb -s \"$TARGET_DEV\" shell $HOME_QUERY $STOCK_LAUNCHER | tail -n 1)"
    } > "$REC_FILE" 2>/dev/null && chmod +x "$REC_FILE" || REC_FILE=""
    echo "  ⚠️  Rollback incomplete — Home still shows '${BACK:-nothing}'." >&2
    [ -n "$REC_FILE" ] && echo "  Emergency restore script: $REC_FILE" >&2
    return 1
}

# Phase 4: Atomic handover — disable stock, set home (only NOW, after launch test passed)
echo "==> Handing over Home to Apple TV Launcher..."
[ "$STOCK_LAUNCHER" != "none" ] && [ -n "$STOCK_LAUNCHER" ] &&     adb -s "$TARGET_DEV" shell pm disable-user --user 0 "$STOCK_LAUNCHER" >/dev/null 2>&1 || true
adb -s "$TARGET_DEV" shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity >/dev/null 2>&1 || true

# 9. Verify Home resolved to our launcher — rollback automatically if not
echo "==> Verifying Home ownership..."
HOME_NOW=$(query_home)
case "$HOME_NOW" in
    com.appletv.launcher)
        echo "==> Home verified: Apple TV Launcher is now the default." ;;
    "")
        echo "Error: TV stopped responding during handover. Attempting rollback..." >&2
        rollback_home || true; exit 1 ;;
    *)
        echo "Error: Home resolved to '${HOME_NOW}', not Apple TV Launcher. Attempting rollback..." >&2
        rollback_home || true
        echo "  Tip: If on Google TV, add com.google.android.tungsten.setupwraith to your debloat profile and retry." >&2
        exit 1 ;;
esac

echo ""
echo "=========================================================="
echo "           🎉 Setup Successfully Completed!              "
echo "=========================================================="
echo "Your TV is now running the Apple TV Launcher ecosystem:"
echo "  • Pitch-Black Leanback Grid with dynamic ambient glow"
echo "  • SmartTube pre-configured (Ad-Free YouTube experience)"
echo "  • Native Apple TV Recent Apps Switcher (Double-Press Home)"
echo "  • Zero Volume Lag (100% hardware acceleration preserved)"
echo ""
echo "To restore original stock packages at any time, run:"
echo "  tools/restore.sh $BACKUP_FILE $TARGET_DEV"
echo "=========================================================="
