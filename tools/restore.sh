#!/usr/bin/env bash
# Restores / re-enables packages on Android TV that were previously disabled.
set -euo pipefail

LIST="${1:-}"
TARGET="${2:-}"

if [ -z "$LIST" ]; then
    echo "Usage: $0 <backup_file_or_profile> [device_ip_or_serial]"
    echo ""
    echo "Examples:"
    echo "  $0 backups/backup_20261021_120000.txt"
    echo "  $0 profiles/xiaomi.txt 192.168.1.9:5555"
    exit 1
fi

if [ ! -f "$LIST" ]; then
    echo "Error: File not found: $LIST" >&2
    exit 1
fi

# Determine target ADB device
ADB_CMD="adb"
if [ -n "$TARGET" ]; then
    ADB_CMD="adb -s $TARGET"
elif adb devices | grep -E '\b(device)\b' >/dev/null 2>&1; then
    FIRST_DEV=$(adb devices | grep -E '\b(device)\b' | head -n 1 | awk '{print $1}')
    ADB_CMD="adb -s $FIRST_DEV"
    echo "==> Using connected device: $FIRST_DEV"
else
    echo "Error: No connected ADB devices found. Run 'adb connect <TV_IP>:5555' first." >&2
    exit 1
fi

echo "==> Restoring packages from: $LIST"
RESTORED=0
while IFS= read -r line || [ -n "$line" ]; do
    # Strip comments and whitespace
    PKG=$(echo "$line" | sed -e 's/#.*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    if [ -n "$PKG" ]; then
        echo "  [Enabling] $PKG"
        $ADB_CMD shell pm enable "$PKG" >/dev/null 2>&1 || true
        RESTORED=$((RESTORED + 1))
    fi
done < "$LIST"

echo "==> Successfully processed $RESTORED packages."
echo "==> Your system packages have been re-enabled."
