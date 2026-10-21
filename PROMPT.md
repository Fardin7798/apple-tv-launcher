# Automated Android TV Transformation Prompt

> **For Beginners:** Copy and paste the prompt below directly into your AI coding assistant (Claude Code, Cursor, Devin, Antigravity, Roo Code, Windsurf, etc.). Your assistant will handle the entire setup for you step-by-step.

---

### Copy Everything Below This Line:

```markdown
I want to transform my Android TV / Google TV into an ultra-clean, ad-free Apple TV experience using this repository (https://github.com/Fardin7798/apple-tv-launcher).

Please act as an autonomous Android TV deployment engineer and guide me through the setup step-by-step.

> **Prerequisite:** Confirm that my computer and TV are connected to the same Wi-Fi network.

### Step 1: Connect to My TV
1. If you don't know my TV's IP address:
   - Check if an ADB device is already connected (`adb devices`).
   - Try local mDNS scan (`adb mdns services`).
   - If not found, ask me for my TV's IP address and tell me where to find it on my TV:
     "On your TV: Go to Settings ⚙️ → Network & Internet → Click your connected Wi-Fi → Look for 'IP address' (e.g., 192.168.1.XX)".
2. Ensure my TV has USB Debugging turned ON:
   - "Settings → Device Preferences → About → Press OK on 'Build' 7 times → Back → Developer options → Turn ON 'USB debugging' (and 'Network debugging' if visible)".
3. Connect over Wi-Fi ADB (`adb connect <TV_IP>:5555`).
4. Remind me to look at my TV screen to accept the "Allow USB debugging?" dialog and check "Always allow from this computer".

### Step 2: Auto-Detect Hardware & Ask Preferences
1. Probe my TV's manufacturer and model:
   `adb shell getprop ro.product.manufacturer`
   `adb shell getprop ro.product.model`
   `adb shell getprop ro.build.version.release`
2. Ask me these 3 quick preference questions:
   - "Which 4 favorite apps do you want placed in the Top Dock row?" (e.g. SmartTube, Netflix, Prime Video, Hotstar)
   - "Do you want to replace stock ad-heavy YouTube with official Ad-Free SmartTube?" (Recommended: Yes)
   - "Keep Chromecast built-in and Google Assistant voice mic active?" (Default: Yes)

### Step 3: Safety Backup
- Before disabling any package, save a snapshot of all currently enabled packages to `backups/backup_<timestamp>.txt`.
- Confirm to me that everything is 100% reversible anytime with `./tools/restore.sh`.

### Step 4: Multi-Brand Safe Debloat
- Match my TV brand against `profiles/`:
  - Xiaomi / Redmi → `profiles/xiaomi.txt`
  - Sony BRAVIA → `profiles/sony-bravia.txt`
  - TCL → `profiles/tcl.txt`
  - OnePlus → `profiles/oneplus.txt`
  - Hisense → `profiles/hisense.txt`
  - NVIDIA SHIELD → `profiles/nvidia-shield.txt`
  - Chromecast with Google TV → `profiles/chromecast-gtv.txt`
  - Amazon Fire TV → `profiles/amazon-fire-tv.txt`
  - Other → `profiles/universal-safe.txt`
- Safely disable manufacturer telemetry, sponsored recommendations, and bloatware using `pm disable-user --user 0`.
- NEVER disable core system services: Chromecast (`com.google.android.apps.mediashell`), Google Assistant (`com.google.android.katniss`), Gboard keyboard, or Wi-Fi/Bluetooth stack.

### Step 5: Replace YouTube with SmartTube (If approved)
- Disable stock ad-heavy YouTube:
  `pm disable-user --user 0 com.google.android.youtube.tv`
  `pm disable-user --user 0 com.google.android.youtube.tvmusic`
- Download and install official latest stable SmartTube using `./tools/get_smarttube.sh <TV_IP>:5555`.

### Step 6: Install Apple TV Launcher & Set Default Home
- Install the ultra-lightweight 218 KB release APK:
  `adb install -r app/build/outputs/apk/release/app-release.apk`
- Set it as the permanent default Home activity:
  `adb shell cmd package set-home-activity --user 0 com.appletv.launcher/.MainActivity`

### Step 7: Verify Everything
- Verify that default home resolves to `com.appletv.launcher/.MainActivity`.
- Verify that SmartTube is installed and ready.
- Send a test Home key event (`input keyevent 3`).
- Explain the remote shortcuts to me:
  - Double-Press Home: Brings up the native Apple TV Recent Apps Switcher.
  - Volume Hold: 100% hardware-speed volume acceleration (zero lag).
```
