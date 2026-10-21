# Apple TV Launcher for Android TV

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Android%20TV-9.0%2B-blue.svg" alt="Android TV"></a>
  <img src="https://img.shields.io/badge/APK%20Size-218%20KB-success.svg" alt="Size">
</p>


An ultra-lightweight, bloat-free Android TV launcher featuring a native **Apple TV-style Recent Apps Switcher**, remote double-press navigation, and 100% hardware-speed volume acceleration.

---

<p align="center">
  <img src="docs/Homescreen.png" alt="Apple TV Home Screen" width="850">
</p>
<p align="center"><em>Minimalist Pitch-Black Leanback Grid</em></p>

---

## ✨ Features

- 🍎 **tvOS-Inspired Interface:** Minimalist pitch-black home screen, dynamic top shelf with ambient artwork glow, and a fluid app grid with spring animations.
- 🔄 **Apple TV Recent Apps Switcher:** Smooth horizontal carousel of running apps with live task switching and quick task controls.
- ⚡ **Zero Volume Lag (Hardware Preservation):** Unlike accessibility-based remappers that break Android's `InputDispatcher` repeat timer, our launcher preserves 100% hardware-speed volume hold acceleration.
- 🎮 **Remote Gesture Shortcuts:** Double-press the Home button from anywhere to immediately bring up the App Switcher.
- 🪶 **Ultra-Lightweight (~218 KB):** Pure Java, zero analytics, zero telemetry, zero background battery drain.
- 🧹 **Safe Debloat Ready:** Compatible with ADB debloat lists to disable manufacturer bloatware (PatchWall, telemetry, recommendation rows) without root.

---

## 📸 Screenshots

| | | |
|---|---|---|
| ![Recent Apps Switcher](docs/RecentTab.png) | ![Control Center](docs/Control_Center.png) | ![tvOS Split-Pane Settings](docs/Settings.png) |
| *Native Apple TV App Switcher (Double-Press Home)* | *Quick Settings & Control Center Platter* | *tvOS 18 Split-Pane Launcher Settings* |
---

## 🚀 1-Click Universal TV Setup

Transform any Android TV or Google TV into an ultra-clean, ad-free Apple TV experience with automated debloating, SmartTube installation, and the 218 KB Apple TV launcher.

### Option 1: Automated 1-Click Setup (Recommended)

1. Enable **Developer Options** and **Network / USB Debugging** on your Android TV.
2. Connect to your TV and run the setup wizard:

**Linux / macOS:**
```bash
./tools/setup.sh <TV_IP>
```

**Windows (PowerShell):**
```powershell
.\tools\setup.ps1 -TargetHost <TV_IP>
```

**What the automated setup does:**
- 🔍 **Auto-detects TV hardware & manufacturer** (Sony, TCL, Xiaomi, OnePlus, Hisense, Shield, Chromecast).
- 💾 **Creates an instant safety backup** of all currently enabled packages.
- 🧹 **Safely disables manufacturer bloatware, sponsored recommendation rows, and telemetry** via `pm disable-user`.
- 📺 **Replaces stock ad-heavy YouTube with official SmartTube** (Ad-free, SponsorBlock, TV-optimized).
- 🍎 **Installs and sets Apple TV Launcher** as the permanent default home activity.

---

### Option 2: Standalone Launcher Installation

If you only want the ultra-lightweight launcher without debloating:
```bash
# 1. Build release APK (or use pre-built binary in app/build/outputs/apk/release/)
./gradlew assembleRelease

# 2. Connect to your TV over Wi-Fi
adb connect <TV_IP>:5555

# 3. Install the launcher
adb install -r app/build/outputs/apk/release/app-release.apk

# 4. Set as permanent default Home launcher
adb shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity
```

---

## 🛡️ Multi-Brand Debloat Profiles

Pre-configured, verified debloat profiles are organized in [`profiles/`](profiles/):

| Brand / Platform | Profile File | Target Capabilities Cleaned |
|---|---|---|
| **Xiaomi / Redmi** | [`profiles/xiaomi.txt`](profiles/xiaomi.txt) | PatchWall, Glance lockscreen ads, Mi Video, Mi Music, Mi Analytics |
| **Sony BRAVIA** | [`profiles/sony-bravia.txt`](profiles/sony-bravia.txt) | Samba TV ACR tracking, Sony Select promos, Sony Discover, Demo mode |
| **TCL** | [`profiles/tcl.txt`](profiles/tcl.txt) | TCL Channel, TCL TTVS tracker, TCL Assistant, recommendation feeds |
| **OnePlus TV** | [`profiles/oneplus.txt`](profiles/oneplus.txt) | OxygenPlay, OnePlus Store, HeyTap telemetry, preloaded browser |
| **Hisense** | [`profiles/hisense.txt`](profiles/hisense.txt) | Hisense telemetry, partner ad services, sponsored home rows |
| **NVIDIA SHIELD** | [`profiles/nvidia-shield.txt`](profiles/nvidia-shield.txt) | Shield recommendation rows, telemetry, partner promotions |
| **Chromecast with Google TV** | [`profiles/chromecast-gtv.txt`](profiles/chromecast-gtv.txt) | Google TV sponsored rows, partner auto-installs, feedback sender |
| **Amazon Fire TV** | [`profiles/amazon-fire-tv.txt`](profiles/amazon-fire-tv.txt) | Fire TV sponsored banners, forced promotions, metrics |
| **Universal Fallback** | [`profiles/universal-safe.txt`](profiles/universal-safe.txt) | Safe for any Android TV 9.0+ device |

> [!NOTE]
> **Safety Whitelist:** None of our profiles ever disable Chromecast built-in (`com.google.android.apps.mediashell`), Google Assistant voice search (`com.google.android.katniss`), Wi-Fi/Bluetooth stacks, or Gboard.

---

## ⏪ Instant Rollback & Restore

Every modification is 100% reversible. To restore all original system packages on your TV:
```bash
./tools/restore.sh backups/<backup_file>.txt <TV_IP>:5555
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
