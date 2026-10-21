#!/usr/bin/env bash
# apply.sh — Dynamic executor for approved plan.txt
# Usage: ./tools/apply.sh <path_to_plan.txt> [TARGET_DEV]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PLAN_FILE="${1:-}"
if [ -z "$PLAN_FILE" ] || [ ! -f "$PLAN_FILE" ]; then
    echo "Error: Plan file required. Usage: ./tools/apply.sh reports/plan_<timestamp>.txt [DEV]" >&2
    exit 1
fi

TARGET_DEV="${2:-}"
if [ -z "$TARGET_DEV" ]; then
    TARGET_DEV=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' | head -n 1 || true)
fi
if [ -z "$TARGET_DEV" ]; then
    echo "Error: No ADB device connected." >&2
    exit 1
fi

echo "=========================================================="
echo "  🚀 Applying Approved Plan on: $TARGET_DEV"
echo "  📄 Plan File: $PLAN_FILE"
echo "=========================================================="

# ── Protected and Core System Services (Loaded from protected.txt) ──
PROTECTED_FILE="$ROOT_DIR/tools/protected.txt"
PROTECTED_LIST=$(grep -v '^\s*#' "$PROTECTED_FILE" 2>/dev/null | grep -v '^\s*$' | tr '\n' ' ' || true)

is_protected() {
    local p="$1"
    for prot in $PROTECTED_LIST; do
        [ "$p" = "$prot" ] && return 0
    done
    return 1
}

# Stock launchers and TV input handlers (dynamically checked, protected from debloat)
KNOWN_LAUNCHERS="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome"
HDMI_INPUTS=$(adb -s "$TARGET_DEV" shell dumpsys tv_input 2>/dev/null | grep -oE 'com\.[a-zA-Z0-9._]+' | sort -u | tr '\n' ' ' || true)

# ── PHASE 0: Pre-flight Verification & APK Readiness ────────────
echo "==> [Phase 0] Verifying prerequisites and APK files..."
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    if command -v javac >/dev/null 2>&1 && [ -f "$ROOT_DIR/gradlew" ]; then
        echo "    Compiling release APK from source..."
        (cd "$ROOT_DIR" && ./gradlew assembleRelease) || true
    fi
    if [ ! -f "$APK_PATH" ]; then
        echo "    Fetching pre-compiled release APK from GitHub Releases..."
        mkdir -p "$(dirname "$APK_PATH")"
        if ! curl -sSL -f -o "$APK_PATH" "https://github.com/Fardin7798/apple-tv-launcher/releases/latest/download/app-release.apk"; then
            echo "Error: Failed to obtain release APK. Aborting — TV left completely untouched." >&2
            exit 1
        fi
    fi
fi
if [ ! -s "$APK_PATH" ]; then
    echo "Error: APK file is empty or missing. TV left untouched." >&2
    exit 1
fi

# Check Android SDK version
SDK_VER=$(adb -s "$TARGET_DEV" shell getprop ro.build.version.sdk | tr -d '\r')
if [ -n "$SDK_VER" ] && [ "$SDK_VER" -lt 28 ]; then
    echo "Error: TV is running Android SDK $SDK_VER. Apple TV Launcher requires SDK 28+ (Android 9.0+)." >&2
    echo "Aborting — TV left completely untouched." >&2
    exit 1
fi

# ── PHASE 1: Safety Snapshot Backup ──────────────────────────────
# ── PHASE 1: Safety Snapshot Backup ──────────────────────────────
TS=$(date +"%Y%m%d_%H%M%S")
SNAPSHOT_DIR=$("$SCRIPT_DIR/backup.sh" "$TARGET_DEV" "$TS" | tail -n 1)
echo "==> [Phase 1] Full system state saved to: $SNAPSHOT_DIR"
echo "    Reversible anytime with: ./tools/restore.sh $SNAPSHOT_DIR $TARGET_DEV"
# ── Check if INSTALL_LAUNCHER is ticked in plan ──────────────────
WANTS_LAUNCHER=false
if grep -qF '[x] INSTALL_LAUNCHER' "$PLAN_FILE"; then
    WANTS_LAUNCHER=true
fi

HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
ORIG_HOME=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
echo "    Initial Home package detected: $ORIG_HOME"

if [ "$WANTS_LAUNCHER" = true ]; then
    # ── PHASE 2: Install Launcher & Launch Test (Stock is still intact) ──
    echo "==> [Phase 2] Installing Apple TV Launcher (Stock launcher is still alive)..."
    INSTALL_OUT=$(adb -s "$TARGET_DEV" install -r "$APK_PATH" 2>&1) || true
    echo "    $INSTALL_OUT"
    case "$INSTALL_OUT" in
        *Success*) ;;
        *INSTALL_FAILED_OLDER_SDK*)
            echo "Error: APK requires a newer Android version than your TV supports." >&2
            echo "Stock launcher was left untouched. Aborting." >&2
            exit 1 ;;
        *)
            echo "Error: APK install failed ($INSTALL_OUT). Stock launcher left untouched. Aborting." >&2
            exit 1 ;;
    esac

    echo "==> [Phase 2] Testing Apple TV Launcher startup (pre-disable verification)..."
    START_OUT=$(adb -s "$TARGET_DEV" shell am start -W -n com.appletv.launcher/.MainActivity 2>&1) || true
    echo "    $START_OUT"
    if ! echo "$START_OUT" | grep -qF "Status: ok"; then
        echo "Error: Apple TV Launcher failed startup test. Stock launcher left untouched. Aborting." >&2
        exit 1
    fi
    echo "    Launcher verified runnable."

    echo "==> [Phase 2] Granting permissions for Recent Apps Switcher and Remote Keys..."
    adb -s "$TARGET_DEV" shell appops set com.appletv.launcher GET_USAGE_STATS allow >/dev/null 2>&1 || true
    adb -s "$TARGET_DEV" shell settings put secure enabled_accessibility_services com.appletv.launcher/.HomeKeyService >/dev/null 2>&1 || true
    adb -s "$TARGET_DEV" shell settings put secure accessibility_enabled 1 >/dev/null 2>&1 || true
    echo "    ✅ Permissions granted: GET_USAGE_STATS (Recent Apps) & HomeKeyService."

    # ── PHASE 3: Set Home Activity & Verify ─────────────────────────
    echo "==> [Phase 3] Setting Apple TV Launcher as default Home..."
    adb -s "$TARGET_DEV" shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity >/dev/null 2>&1 || true

    HOME_NOW=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
    echo "    Current Home owner: $HOME_NOW"

    # Only disable old stock launcher if Home is verified and old stock is NOT protected
    if [ "$HOME_NOW" = "com.appletv.launcher" ]; then
        echo "    ✅ Home verified: com.appletv.launcher"
        if [ -n "$ORIG_HOME" ] && [ "$ORIG_HOME" != "com.appletv.launcher" ] && [ "$ORIG_HOME" != "none" ] && [ "$ORIG_HOME" != "android" ]; then
            if is_protected "$ORIG_HOME"; then
                echo "    🛡️  Original Home ($ORIG_HOME) is in protected list — leaving enabled."
            else
                echo "    Disabling previous stock launcher: $ORIG_HOME"
                adb -s "$TARGET_DEV" shell pm disable-user --user 0 "$ORIG_HOME" >/dev/null 2>&1 || true
                # Verify Home again after disabling stock
                POST_DISABLE_HOME=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
                if [ "$POST_DISABLE_HOME" != "com.appletv.launcher" ]; then
                    echo "⚠️ Warning: Home changed unexpectedly to $POST_DISABLE_HOME. Re-enabling $ORIG_HOME..."
                    adb -s "$TARGET_DEV" shell pm enable --user 0 "$ORIG_HOME" >/dev/null 2>&1 || true
                fi
            fi
        fi
    else
        echo "Error: Home failed to switch to com.appletv.launcher (currently '$HOME_NOW')." >&2
        echo "Stock launcher left untouched. Aborting before debloat to protect TV." >&2
        exit 1
    fi
fi

# ── PHASE 4: Apply Approved Debloat (Safe post-launcher) ─────────
echo "==> [Phase 4] Applying approved debloat packages..."
DISABLED_COUNT=0
while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
        \[x\]\ DISABLE\ *)
            PKG="${line#\[x\] DISABLE }"
            PKG="${PKG%% *}"
            [ -z "$PKG" ] && continue
            if is_protected "$PKG"; then
                echo "    🛡️  Skipping protected package: $PKG"
                continue
            fi
            # Stock launchers are managed only in Phase 3 after Home verification
            for kl in $KNOWN_LAUNCHERS; do
                if [ "$PKG" = "$kl" ]; then
                    echo "    🛡️  Skipping stock launcher from generic debloat: $PKG"
                    continue 2
                fi
            done
            # HDMI/TV input handlers are protected
            for hdmi in $HDMI_INPUTS; do
                if [ "$PKG" = "$hdmi" ]; then
                    echo "    🛡️  Skipping HDMI/TV input handler: $PKG"
                    continue 2
                fi
            done
            adb -s "$TARGET_DEV" shell pm disable-user --user 0 "$PKG" >/dev/null 2>&1 || true
            if adb -s "$TARGET_DEV" shell pm list packages -d 2>/dev/null | grep -qx "package:$PKG"; then
                echo "DISABLED: $PKG" >> "$SNAPSHOT_DIR/changelog.txt"
                DISABLED_COUNT=$((DISABLED_COUNT + 1))
            fi
            ;;
    esac
done < "$PLAN_FILE"
echo "    Safely disabled $DISABLED_COUNT approved bloatware packages."

# ── PHASE 5: YouTube & SmartTube Handling ────────────────────────
if grep -qF '[x] INSTALL_SMARTTUBE' "$PLAN_FILE"; then
    echo "==> [Phase 5] Installing SmartTube..."
    SMARTTUBE_SCRIPT="$SCRIPT_DIR/get_smarttube.sh"
    ST_SUCCESS=false
    if [ -f "$SMARTTUBE_SCRIPT" ]; then
        if "$SMARTTUBE_SCRIPT" "$TARGET_DEV"; then
            ST_SUCCESS=true
        fi
    fi
    if [ "$ST_SUCCESS" = true ]; then
        echo "    SmartTube installed successfully."
        if grep -qF '[x] DISABLE_YOUTUBE' "$PLAN_FILE"; then
            echo "    Disabling stock ad-supported YouTube..."
            adb -s "$TARGET_DEV" shell pm disable-user --user 0 com.google.android.youtube.tv >/dev/null 2>&1 || true
            adb -s "$TARGET_DEV" shell pm disable-user --user 0 com.google.android.youtube.tvmusic >/dev/null 2>&1 || true
        fi
    else
        echo "⚠️ Warning: SmartTube installation did not complete. Stock YouTube left ENABLED."
    fi
fi

# ── PHASE 6: Animation Scale Tweaks ──────────────────────────────
if grep -qF '[x] SPEED_ANIMATIONS' "$PLAN_FILE"; then
    echo "==> [Phase 6] Setting animation scales to 0.5x for instant UI response..."
    adb -s "$TARGET_DEV" shell settings put global window_animation_scale 0.5 || true
    adb -s "$TARGET_DEV" shell settings put global transition_animation_scale 0.5 || true
    adb -s "$TARGET_DEV" shell settings put global animator_duration_scale 0.5 || true
fi

# ── FINAL VERIFICATION ───────────────────────────────────────────
echo ""
echo "=========================================================="
FINAL_HOME=$(adb -s "$TARGET_DEV" shell $HOME_QUERY 2>/dev/null | tail -n 1 | tr -d '\r' | cut -d/ -f1 || true)
echo "  ✅ Plan execution complete!"
echo "  📺 Current Home: $FINAL_HOME"
echo "  💾 Backup Saved: $SNAPSHOT_DIR"
echo "  🔄 To restore anytime: ./tools/restore.sh $SNAPSHOT_DIR $TARGET_DEV"
echo "=========================================================="
