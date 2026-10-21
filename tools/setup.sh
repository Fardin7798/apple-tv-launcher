#!/usr/bin/env bash
# Universal 1-Click Android TV Transformation Suite
# Orchestrates read-only audit -> plan confirmation -> safe apply.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================================="
echo "       🍎 Universal Apple TV Launcher Setup Suite         "
echo "=========================================================="

# 1. Parse arguments safely
AUTO_CONFIRM=false
TARGET_HOST=""

for arg in "$@"; do
    case "$arg" in
        -y|--yes)
            AUTO_CONFIRM=true
            ;;
        -h|--help)
            echo "Usage: ./tools/setup.sh [TV_IP] [-y|--yes]"
            exit 0
            ;;
        -*)
            ;;
        *)
            if [ -z "$TARGET_HOST" ]; then
                TARGET_HOST="$arg"
            fi
            ;;
    esac
done

TARGET_HOST="${TARGET_HOST:-${TV_HOST:-${TV_IP:-}}}"

# 2. Verify ADB tool
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: 'adb' command not found." >&2
    echo "Please install Android Platform Tools:" >&2
    echo "  - Linux:  sudo apt install adb" >&2
    echo "  - macOS:  brew install android-platform-tools" >&2
    exit 1
fi

# 3. Target Device Discovery & Connection
TARGET_DEV=""
if [ -n "$TARGET_HOST" ]; then
    if [[ "$TARGET_HOST" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "==> Connecting to $TARGET_HOST:5555..."
        adb connect "$TARGET_HOST:5555" || true
        TARGET_DEV="$TARGET_HOST:5555"
    else
        TARGET_DEV="$TARGET_HOST"
    fi
else
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
fi

# Verify connection state
DEV_STATE=$(adb -s "$TARGET_DEV" get-state 2>/dev/null || true)
if [ "$DEV_STATE" != "device" ]; then
    echo "Error: Device $TARGET_DEV is not in 'device' state (currently: '${DEV_STATE:-disconnected}')." >&2
    echo "Tip: Ensure USB Debugging is enabled and accept the 'Allow USB debugging' dialog on your TV screen." >&2
    exit 1
fi

# 4. Step 1: Run Read-Only TV Audit
echo ""
echo "==> Step 1: Running Read-Only TV Inspection (Zero changes made to TV)..."
"$SCRIPT_DIR/audit.sh" "$TARGET_DEV"

# Locate latest generated plan file
LATEST_PLAN=$(ls -t "$ROOT_DIR/reports"/plan_*.txt 2>/dev/null | head -n 1 || true)
if [ -z "$LATEST_PLAN" ] || [ ! -f "$LATEST_PLAN" ]; then
    echo "Error: Audit did not produce a plan file. Aborting." >&2
    exit 1
fi

# 5. Step 2: Approval
if [ "$AUTO_CONFIRM" = false ]; then
    if [ -t 0 ]; then
        echo "You can review or edit $LATEST_PLAN before proceeding."
        read -r -p "Do you approve and want to apply this transformation plan now? [Y/n]: " PROCEED_REPLY
        case "$PROCEED_REPLY" in
            [nN][oO]|[nN])
                echo "Operation cancelled. No changes were made to your TV."
                echo "You can manually apply later by running: ./tools/apply.sh $LATEST_PLAN $TARGET_DEV"
                exit 0
                ;;
        esac
    else
        echo "⚠️  Non-interactive mode detected and '-y' flag was not passed."
        echo "   Read-only audit completed. Transformation plan saved to: $LATEST_PLAN"
        echo "   AI Agents: You MUST NOT re-run with -y autonomously. Ask the user for explicit approval in chat first."
        echo "   To apply after user approval, run: ./tools/setup.sh $TARGET_DEV -y"
        echo "   Or review the plan and run: ./tools/apply.sh $LATEST_PLAN $TARGET_DEV"
        echo "   (No changes were made to your TV)"
        exit 0
    fi
fi
# 6. Step 3: Run Apply Pipeline
echo ""
echo "==> Step 3: Applying Transformation with Pre-Launch Safety..."
"$SCRIPT_DIR/apply.sh" "$LATEST_PLAN" "$TARGET_DEV"
