# Agent Runbook: Autonomous Android TV Transformation

This document is the single source of truth for AI agents (Antigravity, Claude Code, Cursor, Devin, Roo Code, Aider, Windsurf) executing tasks in this repository.

---

## 🎯 Primary Goal
Transform any connected Android TV or Google TV (Sony, TCL, Xiaomi, OnePlus, Hisense, Shield, Chromecast, Fire TV) into an ultra-clean, ad-free Apple TV experience with:
1. Pure pitch-black tvOS 18 leanback grid with dynamic ambient RGB glow.
2. Native Apple TV Recent Apps Switcher (`RecentAppsActivity`) with double-press Home gesture.
3. 100% hardware-speed volume hold acceleration preserved (zero accessibility lag).
4. Official Ad-Free SmartTube replacing stock YouTube.
5. Safe, reversible multi-brand debloating.

---

## ⚡ 1-Line Execution (Autonomous / Non-Interactive)

When a user asks you to set up their TV at an IP address:

```bash
# Run the automated transformation suite
./tools/setup.sh <TV_IP>
```
*Note: If on Windows, run:*
```powershell
powershell -ExecutionPolicy Bypass -File .\tools\setup.ps1 -TargetHost <TV_IP>
```

---

## 🛠️ Individual Autonomous Workflows

If you need to execute or debug individual phases independently:

### 1. Build the Ultra-Lightweight Launcher APK (218 KB)
```bash
./gradlew assembleRelease
# Output binary: app/build/outputs/apk/release/app-release.apk
```
*Run unit tests to verify:*
```bash
./gradlew test
```

### 2. Connect & Probe Target Device
```bash
# Connect over Wi-Fi ADB
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 get-state

# Inspect hardware specs
MANUFACTURER=$(adb -s <TV_IP>:5555 shell getprop ro.product.manufacturer | tr -d '\r')
MODEL=$(adb -s <TV_IP>:5555 shell getprop ro.product.model | tr -d '\r')
ANDROID_VER=$(adb -s <TV_IP>:5555 shell getprop ro.build.version.release | tr -d '\r')
```

### 3. Apply Multi-Brand Debloat Profile
Select matching profile from [`profiles/`](profiles/):
- Xiaomi / Redmi: `profiles/xiaomi.txt`
- Sony BRAVIA: `profiles/sony-bravia.txt`
- TCL: `profiles/tcl.txt`
- OnePlus: `profiles/oneplus.txt`
- Hisense: `profiles/hisense.txt`
- NVIDIA SHIELD: `profiles/nvidia-shield.txt`
- Chromecast with Google TV: `profiles/chromecast-gtv.txt`
- Fallback: `profiles/universal-safe.txt`

Disable packages safely for user 0:
```bash
adb -s <TV_IP>:5555 shell pm disable-user --user 0 <package_name>
```

### 4. Replace Stock YouTube with Official SmartTube
```bash
# Disable stock ad-heavy YouTube
adb -s <TV_IP>:5555 shell pm disable-user --user 0 com.google.android.youtube.tv
adb -s <TV_IP>:5555 shell pm disable-user --user 0 com.google.android.youtube.tvmusic

# Download and install latest official SmartTube
./tools/get_smarttube.sh <TV_IP>:5555
```

### 5. Install Launcher & Set Permanent Default Home
```bash
# Install release APK
adb -s <TV_IP>:5555 install -r app/build/outputs/apk/release/app-release.apk

# Bind as default Home activity (survives hard reboot / wall switch cycle)
adb -s <TV_IP>:5555 shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity
```

### 6. Verify Full Story
- Check default home resolution:
  ```bash
  adb -s <TV_IP>:5555 shell cmd package resolve-activity --brief android.intent.action.MAIN -c android.intent.category.HOME
  # Must output: com.appletv.launcher/.MainActivity
  ```
- Trigger Home screen:
  ```bash
  adb -s <TV_IP>:5555 shell input keyevent 3
  ```

---

## 🛡️ Critical Guardrails for Agents

1. **NEVER Disable Protected System Services:**
   - ❌ `com.google.android.apps.mediashell` (Chromecast built-in — needed for phone casting).
   - ❌ `com.google.android.katniss` (Google Assistant / Remote Mic Voice Search).
   - ❌ Keyboard packages (e.g. `com.google.android.inputmethod.latin` Gboard).
   - ❌ Wi-Fi, Bluetooth, Audio, HDMI CEC, and Display hal services.
2. **NEVER Use AccessibilityService for Key Interception:**
   - Enabling an accessibility service hook intercepts Android's native Linux `InputDispatcher` repeat queue, breaking volume button hold acceleration.
   - Hotkeys must remain at the launcher activity / broadcast receiver level (Double-press Home).
3. **Always Ensure Reversibility:**
   - Before running debloat, save a snapshot via `adb shell pm list packages -e > backups/backup_<timestamp>.txt`.
   - To restore: `./tools/restore.sh backups/<backup_file>.txt <TV_IP>:5555`.
