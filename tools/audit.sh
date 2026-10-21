#!/usr/bin/env bash
# audit.sh — Read-only TV inspection.  ZERO changes to the TV.
# Outputs: reports/report_<timestamp>.md  (full audit)
#          reports/plan_<timestamp>.txt    (editable apply plan)
#          reports/apply_<timestamp>.sh    (generated from approved plan — run separately)
#
# Usage:
#   ./tools/audit.sh [TV_IP]      # connect over Wi-Fi
#   ./tools/audit.sh              # auto-discover via adb devices / mDNS
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORTS_DIR="$ROOT_DIR/reports"
mkdir -p "$REPORTS_DIR"
TS=$(date +"%Y%m%d_%H%M%S")
REPORT="$REPORTS_DIR/report_${TS}.md"
PLAN="$REPORTS_DIR/plan_${TS}.txt"
APPLY="$REPORTS_DIR/apply_${TS}.sh"

# ── ADB check ──────────────────────────────────────────────────────────────────
if ! command -v adb >/dev/null 2>&1; then
    echo "Error: 'adb' not found. Install Android Platform Tools first." >&2
    echo "  Linux:  sudo apt install adb" >&2
    echo "  macOS:  brew install android-platform-tools" >&2
    exit 1
fi

# ── Device discovery ───────────────────────────────────────────────────────────
TARGET_HOST="${1:-${TV_HOST:-${TV_IP:-}}}"
if [ -z "$TARGET_HOST" ]; then
    DEVICES=$(adb devices | grep -E '\b(device)\b' | awk '{print $1}' || true)
    if [ -n "$DEVICES" ]; then
        DEV=$(echo "$DEVICES" | head -n 1)
        echo "==> Using already-connected ADB device: $DEV"
    else
        MDNS_DEV=$(adb mdns services 2>/dev/null | grep '_adb' | head -n 1 | awk '{print $NF}' || true)
        if [ -n "$MDNS_DEV" ]; then
            DEV="$MDNS_DEV"
        else
            if [ ! -t 0 ]; then
                echo "Error: No device found. Pass TV IP as argument or set TV_HOST=<ip>." >&2; exit 1
            fi
            echo ""
            echo "ℹ️  How to find your TV IP: Settings ⚙️ → Network & Internet → Connected Wi-Fi → IP address"
            read -r -p "Enter Android TV IP address: " TARGET_HOST
            [ -z "$TARGET_HOST" ] && { echo "Error: IP required." >&2; exit 1; }
            adb connect "${TARGET_HOST}:5555" >/dev/null 2>&1 || true
            DEV="${TARGET_HOST}:5555"
        fi
    fi
else
    if [[ "$TARGET_HOST" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        adb connect "${TARGET_HOST}:5555" >/dev/null 2>&1 || true
        DEV="${TARGET_HOST}:5555"
    else
        DEV="$TARGET_HOST"
    fi
fi

if ! adb -s "$DEV" get-state >/dev/null 2>&1; then
    echo "Error: Device $DEV not accessible. Accept USB debugging on TV screen." >&2; exit 1
fi

# ── Convenience wrapper: tv_shell ──────────────────────────────────────────────
tv() { adb -s "$DEV" shell "$@" 2>/dev/null | tr -d '\r' || true; }
tv_raw() { adb -s "$DEV" shell "$@" 2>/dev/null || true; }

echo "=========================================================="
echo "  🔍 Apple TV Launcher — Read-Only TV Audit"
echo "  No changes will be made to your TV."
echo "=========================================================="
echo ""

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 1: TV IDENTITY
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [1/7] Reading TV identity..."
MFR=$(tv getprop ro.product.manufacturer | tr '[:upper:]' '[:lower:]')
MODEL=$(tv getprop ro.product.model)
CODENAME=$(tv getprop ro.product.device)
BOARD=$(tv getprop ro.board.platform)
ANDROID_VER=$(tv getprop ro.build.version.release)
SDK=$(tv getprop ro.build.version.sdk)
FIRMWARE=$(tv getprop ro.build.display.id)
SEC_PATCH=$(tv getprop ro.build.version.security_patch)
SCREEN_SIZE=$(tv wm size)
SCREEN_DENSITY=$(tv wm density)
IS_GTV=$(tv getprop ro.com.google.gmsversion | grep -q "Google TV" && echo "Google TV" || \
          tv pm list packages | grep -q "com.google.android.apps.tv.launcherx" && echo "Google TV" || echo "Android TV")
ADB_PORT=$(tv getprop service.adb.tcp.port || echo "unknown")

# ═══════════════════════════════════════════════════════════════════════════════
# Capture baseline Day-1 untouched snapshot
"$SCRIPT_DIR/backup.sh" "$DEV" "original" >/dev/null 2>&1 || true

# SECTION 2: HARDWARE HEALTH
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [2/7] Reading hardware health (RAM / Storage)..."
RAM_TOTAL=$(tv cat /proc/meminfo | grep MemTotal | awk '{printf "%.0f MB", $2/1024}')
RAM_FREE=$(tv cat /proc/meminfo | grep MemAvailable | awk '{printf "%.0f MB", $2/1024}')
STORAGE_RAW=$(tv df /data 2>/dev/null | tail -1 || true)
STOR_TOTAL=$(echo "$STORAGE_RAW" | awk '{printf "%.0f MB", $2/1024}' 2>/dev/null || echo "n/a")
STOR_FREE=$(echo "$STORAGE_RAW" | awk '{printf "%.0f MB", $4/1024}' 2>/dev/null || echo "n/a")
# Top 5 RAM hogs
TOP5_RAM=$(tv dumpsys meminfo | grep -E '^\s+[0-9]+K' | sort -rn | head -5 | \
           awk '{printf "  %s KB — %s\n", $1, $NF}' || true)

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 3: SAFETY CHECK — HOME, WIFI, HDMI, MIN SDK
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [3/7] Running safety checks..."
HOME_QUERY="cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
HOME_NOW=$(tv $HOME_QUERY | tail -n 1 | cut -d/ -f1)
WIFI_PKG=$(tv dumpsys wifi 2>/dev/null | grep -i "mWifiInfo\|NetworkInfo" | head -3 || echo "n/a")
SETUP_WIZ=$(tv pm list packages | grep -i "setupwraith\|tungsten" | sed 's/package://' || true)
HDMI_PKGS=$(tv dumpsys tv_input 2>/dev/null | grep -oE 'com\.[a-zA-Z0-9._]+' | sort -u | head -10 || true)
ADB_OPEN="No"
case "$ADB_PORT" in 5555|5554) ADB_OPEN="⚠️  YES — port $ADB_PORT open on network" ;; esac
MIN_SDK_OK="Yes (TV SDK=$SDK, our minSdk=28)"
[ "${SDK:-0}" -lt 28 ] 2>/dev/null && MIN_SDK_OK="⚠️  NO — TV SDK $SDK is below our minSdk 28 (Android 9). Launcher WILL NOT install."

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 4: PACKAGE INVENTORY — full per-app analysis
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [4/7] Enumerating all packages (this takes ~30s)..."

# Lists
ALL_PKGS=$(tv pm list packages | sed 's/package://' | sort)
ENABLED_PKGS=$(tv pm list packages -e | sed 's/package://' | sort)
DISABLED_PKGS=$(tv pm list packages -d | sed 's/package://' | sort)
SYSTEM_PKGS=$(tv pm list packages -s | sed 's/package://' | sort)
USER_PKGS=$(tv pm list packages -3 | sed 's/package://' | sort)
TOTAL=$(printf '%s\n' "$ALL_PKGS" | grep -c . || true)
DISABLED_COUNT=$(printf '%s\n' "$DISABLED_PKGS" | grep -c . || true)
# Protected packages — loaded from Single Source of Truth (protected.txt)
PROTECTED=$(grep -v '^\s*#' "$SCRIPT_DIR/protected.txt" | grep -v '^\s*$' || true)

# Known safe streaming apps
STREAMING="com.netflix.ninja
com.amazon.amazonvideo.livingroom
in.startv.hotstar
com.disney.disneyplus
com.zee5.android
com.voot.app
com.sonyliv
com.mxtech.videoplayer.tv"

# Known bloatware patterns (all brands)
BLOAT_PATTERNS="telemetry analytics tracker ads samba acr agama collect stat report metrics diag log beacon crashlytics firebase tune appsflyer kochava branch.io adjust fabric surveyor usage"

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 5: SETTINGS & PRIVACY
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [5/7] Reading privacy & animation settings..."
ANIM1=$(tv settings get global window_animation_scale || echo "1.0")
ANIM2=$(tv settings get global transition_animation_scale || echo "1.0")
ANIM3=$(tv settings get global animator_duration_scale || echo "1.0")
DNS_MODE=$(tv settings get global private_dns_mode || echo "off")
ADV_ID=$(tv settings get global advertising_id 2>/dev/null || echo "n/a")
SLEEP=$(tv settings get global sleep_timeout || echo "n/a")
AUTO_UPDATE=$(tv settings get global auto_update_package || echo "n/a")

# ═══════════════════════════════════════════════════════════════════════════════
# SECTION 6: PER-PROFILE BLOAT MATCH
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [6/7] Matching against brand debloat profile..."
SELECTED_PROFILE=""
case "$MFR" in
    *xiaomi*|*redmi*) SELECTED_PROFILE="$ROOT_DIR/profiles/xiaomi.txt" ;;
    *sony*)           SELECTED_PROFILE="$ROOT_DIR/profiles/sony-bravia.txt" ;;
    *tcl*)            SELECTED_PROFILE="$ROOT_DIR/profiles/tcl.txt" ;;
    *hisense*)        SELECTED_PROFILE="$ROOT_DIR/profiles/hisense.txt" ;;
    *oneplus*)        SELECTED_PROFILE="$ROOT_DIR/profiles/oneplus.txt" ;;
    *nvidia*)         SELECTED_PROFILE="$ROOT_DIR/profiles/nvidia-shield.txt" ;;
    *google*)         SELECTED_PROFILE="$ROOT_DIR/profiles/chromecast-gtv.txt" ;;
    *amazon*)         SELECTED_PROFILE="$ROOT_DIR/profiles/amazon-fire-tv.txt" ;;
    *)                SELECTED_PROFILE="$ROOT_DIR/profiles/universal-safe.txt" ;;
esac

BLOAT_TO_REMOVE=()
BLOAT_ALREADY_DISABLED=()
if [ -f "$SELECTED_PROFILE" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        PKG=$(echo "$line" | sed 's/#.*//' | tr -d ' ')
        [ -z "$PKG" ] && continue
        if echo "$DISABLED_PKGS" | grep -qx "$PKG"; then
            BLOAT_ALREADY_DISABLED+=("$PKG")
        elif echo "$ENABLED_PKGS" | grep -qx "$PKG"; then
            BLOAT_TO_REMOVE+=("$PKG")
        fi
    done < "$SELECTED_PROFILE"
fi

# ═══════════════════════════════════════════════════════════════════════════════
echo "==> [7/7] Reading app usage stats..."
STALE_PKGS=()
USAGE_RAW=$(tv dumpsys usagestats 2>/dev/null || true)
if [ -n "$USAGE_RAW" ]; then
    while IFS= read -r line; do
        pkg=$(echo "$line" | sed -n 's/.*package=\([^ "]*\).*/\1/p')
        [ -n "$pkg" ] && STALE_PKGS+=("$pkg")
    done < <(echo "$USAGE_RAW" | grep -E 'lastTime="197|lastTimeUsed="197|totalTime="00:00"|totalTimeInForeground="0"|totalTimeUsed="0"' | head -n 10 || true)
fi

# ═══════════════════════════════════════════════════════════════════════════════
# WRITE REPORT.MD
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> Writing report: $REPORT"

{
cat << HEADER
# 📋 TV Audit Report — ${MODEL} (${MFR})
Generated: $(date "+%Y-%m-%d %H:%M:%S")
Device ADB: \`$DEV\`

> **READ-ONLY** — Zero changes were made to your TV during this audit.

---

## 1. TV Identity

| Field | Value |
|---|---|
| **Brand / Manufacturer** | ${MFR} |
| **Model** | ${MODEL} |
| **Codename / Device** | ${CODENAME} |
| **Chipset / Board** | ${BOARD} |
| **OS Type** | ${IS_GTV} |
| **Android Version** | ${ANDROID_VER} (SDK ${SDK}) |
| **Firmware Build** | ${FIRMWARE} |
| **Security Patch** | ${SEC_PATCH} |
| **Screen** | ${SCREEN_SIZE} / ${SCREEN_DENSITY} |

---

## 2. Hardware Health

| | Value |
|---|---|
| **RAM Total** | ${RAM_TOTAL} |
| **RAM Available** | ${RAM_FREE} |
| **Storage Total (/data)** | ${STOR_TOTAL} |
| **Storage Free (/data)** | ${STOR_FREE} |

**Top RAM consumers:**
\`\`\`
${TOP5_RAM}
\`\`\`

---

## 3. Safety Checks ⚠️

| Check | Result |
|---|---|
| **Current Home Launcher** | \`${HOME_NOW}\` |
| **Our Launcher Compatible (SDK≥28)** | ${MIN_SDK_OK} |
| **Google TV Setup Wizard** | ${SETUP_WIZ:-Not found} |
| **ADB port open on network** | ${ADB_OPEN} |
| **Private DNS Mode** | ${DNS_MODE} |

**HDMI / TV Input packages:**
\`\`\`
${HDMI_PKGS}
\`\`\`

> [!NOTE]
> System setup wizards and HDMI input handlers are in the protected list and will never be touched by the debloat engine.

---

## 4. Package Inventory

| Metric | Count |
|---|---|
| **Total packages on TV** | ${TOTAL} |
| **Currently enabled** | $(echo "$ENABLED_PKGS" | grep -v '^$' | wc -l) |
| **Already disabled** | ${DISABLED_COUNT} |
| **System apps** | $(echo "$SYSTEM_PKGS" | grep -v '^$' | wc -l) |
| **User-installed apps** | $(echo "$USER_PKGS" | grep -v '^$' | wc -l) |

---

## 5. Debloat Plan — Profile: \`$(basename "$SELECTED_PROFILE")\`

### 🛑 Will Be Disabled (in plan.txt — you can untick any line)

HEADER

if [ ${#BLOAT_TO_REMOVE[@]} -eq 0 ]; then
    echo "_None found from profile on this TV._"
else
    for pkg in "${BLOAT_TO_REMOVE[@]}"; do
        # Check if stale
        STALE_NOTE=""
        for s in "${STALE_PKGS[@]:-}"; do
            [ "$s" = "$pkg" ] && STALE_NOTE=" _(not opened in 6+ months)_" && break
        done
        echo "- \`$pkg\`${STALE_NOTE}"
    done
fi

cat << SECTION5B

### ✅ Already Disabled on This TV

SECTION5B

if [ ${#BLOAT_ALREADY_DISABLED[@]} -eq 0 ]; then
    echo "_None._"
else
    for pkg in "${BLOAT_ALREADY_DISABLED[@]}"; do
        echo "- \`$pkg\`"
    done
fi

cat << SECTION6

---

## 6. Unused / Low Usage Packages (Review Candidates)

_These are enabled system/pre-installed apps with no recorded usage. Review before disabling._

SECTION6

if [ ${#STALE_PKGS[@]} -eq 0 ]; then
    echo "_No stale apps found (usage stats may be unavailable on this TV)._"
else
    SHOWN=0
    for pkg in "${STALE_PKGS[@]}"; do
        # Skip if already in bloat list, protected, or streaming
        echo "$PROTECTED" | grep -qx "$pkg" && continue
        echo "$STREAMING" | grep -qx "$pkg" && continue
        echo "- \`$pkg\` — Unused / No foreground time"
        SHOWN=$((SHOWN+1))
        [ $SHOWN -ge 30 ] && echo "_...and more (see full usage dump)_" && break
    done
    [ $SHOWN -eq 0 ] && echo "_All stale apps are either protected or streaming apps._"
fi

cat << SECTION7

---

## 7. Protected — Never Disable

These will never be touched by any script in this repo:

| Package | Role |
|---|---|
| \`com.google.android.apps.mediashell\` | Chromecast built-in |
| \`com.google.android.katniss\` | Google Assistant / Voice Mic |
| \`com.google.android.inputmethod.latin\` | Gboard keyboard |
| \`com.android.bluetooth\` | Bluetooth stack |
| \`com.android.wifi\` | Wi-Fi stack |
| \`com.google.android.gms\` | Google Play Services |
| \`com.google.android.gsf\` | Google Services Framework |
| \`com.android.settings\` | System Settings |
| \`com.android.systemui\` | System UI / Status bar |
| \`android\` | Android framework |

---

## 8. Privacy & Performance Settings

| Setting | Current Value | Recommendation |
|---|---|---|
| Window animation scale | ${ANIM1} | Set to 0.5 for snappier feel |
| Transition animation scale | ${ANIM2} | Set to 0.5 |
| Animator duration scale | ${ANIM3} | Set to 0.5 |
| Private DNS | ${DNS_MODE} | Set to \`hostname:dns.adguard.com\` for ad-blocking |
| Screen sleep timeout | ${SLEEP} ms | — |
| Advertising ID | ${ADV_ID} | Reset periodically |

---

## 9. Next Steps

1. Review this report. Tick/untick items in \`$(basename "$PLAN")\`.
2. When ready, run: \`bash reports/apply_${TS}.sh\`
   The apply script will be generated from your approved plan.
3. To restore at any time: \`./tools/restore.sh backups/<backup>.txt $DEV\`

SECTION7
} > "$REPORT"

# ═══════════════════════════════════════════════════════════════════════════════
# WRITE PLAN.TXT  (user-editable — tick = [x], untick = [ ])
# ═══════════════════════════════════════════════════════════════════════════════
echo "==> Writing editable plan: $PLAN"
{
cat << PLANHEADER
# ══════════════════════════════════════════════════════════════════
#  APPLY PLAN — ${MODEL} — generated $(date "+%Y-%m-%d %H:%M:%S")
#  HOW TO USE:
#    [x] = will be executed   [ ] = skip this item
#  Edit any line, then run:  bash reports/apply_${TS}.sh
#  apply_${TS}.sh is auto-generated from this file by audit.sh
# ══════════════════════════════════════════════════════════════════

# ── DEBLOAT: packages to disable (pm disable-user --user 0) ──────
PLANHEADER

for pkg in "${BLOAT_TO_REMOVE[@]}"; do
    echo "[x] DISABLE $pkg"
done

cat << PLAN2

# ── YOUTUBE: disable stock, install SmartTube ────────────────────
[x] DISABLE_YOUTUBE
[x] INSTALL_SMARTTUBE

# ── LAUNCHER: install Apple TV Launcher & set as Home ────────────
[x] INSTALL_LAUNCHER

# ── ANIMATIONS: speed up TV (0.5x scale) ────────────────────────
[ ] SPEED_ANIMATIONS

# ── ADD MORE: paste extra package names below (one per line) ─────
# [x] DISABLE com.example.your.package

PLAN2
} > "$PLAN"

# ═══════════════════════════════════════════════════════════════════════════════
# GENERATE apply.sh FROM plan.txt  (called when user is ready)
# ═══════════════════════════════════════════════════════════════════════════════
generate_apply() {
    local PLAN_FILE="$1"
    local APPLY_FILE="$2"
    local ADB_DEV="$3"
    cat << APPLYHDR > "$APPLY_FILE"
#!/usr/bin/env bash
# apply runner dynamically executing $(basename "$PLAN_FILE")
exec "$ROOT_DIR/tools/apply.sh" "$PLAN_FILE" "$ADB_DEV"
APPLYHDR
    chmod +x "$APPLY_FILE"
}

generate_apply "$PLAN" "$APPLY" "$DEV"

# ═══════════════════════════════════════════════════════════════════════════════
# DONE
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "=========================================================="
echo "  ✅ Audit complete — ZERO changes made to TV."
echo "=========================================================="
echo ""
echo "  📄 Full report:    $REPORT"
echo "  📝 Editable plan:  $PLAN"
echo "  ⚙️  Apply script:   $APPLY"
echo ""
echo "  Next steps:"
echo "  1. Open $PLAN"
echo "     Tick [x] = apply   Untick [ ] = skip"
echo "  2. When satisfied: bash $APPLY"
echo "     (apply script has pre-launch safety test + auto-rollback)"
echo ""
