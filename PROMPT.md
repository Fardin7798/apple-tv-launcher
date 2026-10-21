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

### Step 2: Read-Only TV Audit (No Changes to TV)
1. Run the audit script:
   `./tools/audit.sh <TV_IP>`
2. This inspects hardware specs, Android version, running apps, launcher status, and HDMI inputs. It generates:
   - `reports/report_<timestamp>.md` (Full audit findings)
   - `reports/plan_<timestamp>.txt` (Editable debloat and setup plan)
   - `reports/apply_<timestamp>.sh` (Pre-generated safe apply script)

### Step 3: Present Plan & Ask for My Approval
Present a clean, human-readable summary in our chat:
1. **TV Hardware:** Manufacturer, Model, Android Version, RAM, and Storage.
2. **Current Launcher:** Name of the stock launcher currently owning Home.
3. **Bloatware to be Disabled:** Specific list of ad packages/telemetry found on my TV.
4. **Protected Core Apps (Will NEVER be touched):** Chromecast, Google Assistant, Wi-Fi, Gboard, Netflix/Prime/Hotstar, HDMI inputs.
5. **Ask me these 2 preference questions and update `reports/plan_<timestamp>.txt` accordingly ([x] to enable, [ ] to skip):**
   - "Do you want to replace stock ad-heavy YouTube with official Ad-Free SmartTube?" (Recommended: Yes)
   - "Do you want to enable 0.5x animation scales for snappier TV response?" (Recommended: Yes)
6. **Wait for my explicit approval:** Do NOT touch my TV until I say "Approved" or specify changes.

### Step 4: Apply Approved Plan with Safety Handover
Once I approve:
1. Ensure the edited `reports/plan_<timestamp>.txt` reflects my approved choices.
2. Run the approved plan:
   `bash reports/apply_<timestamp>.sh`
   *(This automatically captures a full system state snapshot before touching the TV, runs pre-launch verification, performs safe Home handover with auto-rollback, and executes approved debloat).*

### Step 5: Verify Everything & Explain Remote Shortcuts
1. Test Home key: `adb shell input keyevent 3`.
2. Confirm TV is now displaying the Apple TV Launcher.
3. Explain the remote shortcuts to me:
   - **Double-Press Home:** Brings up the native Apple TV Recent Apps Switcher.
   - **Volume Hold:** 100% hardware-speed volume acceleration (zero lag).
   - **Restoration:** Remind me that `./tools/restore.sh backups/<device_snapshot_directory>` can revert all changes anytime.
```
