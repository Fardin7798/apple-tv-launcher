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

## 🚀 Quick Start

### 1. Build the APK
```bash
./gradlew assembleRelease
```
The optimized APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

### 2. Install to your TV over Wi-Fi
Enable **Developer Options** and **Network / USB Debugging** on your Android TV:
```bash
# Connect to your TV's IP
adb connect <TV_IP>:5555

# Install the launcher
adb install -r app/build/outputs/apk/release/app-release.apk

# Set as default Home launcher
adb shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity
```
---


---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
