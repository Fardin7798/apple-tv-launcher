# Contributing to Spider TV Launcher 🕷️

Thanks for wanting to help! Bug reports, new debloat profiles, fixes and ideas are all welcome.

## Ways to help

- 🐞 **Report a bug** using the [Bug report](../../issues/new?template=bug_report.yml) template.
- 💡 **Suggest a feature or a new suit/theme** using the [Feature request](../../issues/new?template=feature_request.yml) template.
- 📺 **Add or fix a TV profile** in `profiles/` (one package name per line). Tell us the TV model and Android version you tested on.
- 🧑‍💻 **Send code**: look for issues labelled `good first issue` or `help wanted`.
- 📸 **Share your setup**: screenshots of Spider TV running on your TV help a lot.

## Project layout

| Path | What lives there |
| --- | --- |
| `app/src/main/java/com/spidertv/launcher/` | The launcher (Java, no extra libraries) |
| `app/src/main/res/` | Layouts, drawables, the hero art and fonts |
| `profiles/` | Per-brand debloat lists |
| `tools/` | `audit.sh`, `apply.sh`, `restore.sh` and the setup scripts |
| `tools/protected.txt` | Packages that must never be disabled |

## Building

You need JDK 17 and the Android SDK (API 34).

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Install on your TV over ADB:

```bash
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Please test on a real Android TV / Google TV device (or an Android TV emulator) and use the remote (D-pad, Back, Home) to check focus and navigation.

## Pull request checklist

1. Fork the repo and create a branch from `main` (`fix/recents-focus`, `feat/new-suit`, `profile/hisense-u8`).
2. Keep each PR small and about one thing.
3. Keep the APK tiny: no new libraries without discussing it in an issue first.
4. Never add a package to a debloat profile that is listed in `tools/protected.txt`, or that breaks Wi-Fi, Bluetooth, HDMI inputs, Settings or the system UI.
5. Build passes and you tested on a TV. Say which device in the PR.
6. Fill in the pull request template.

## Art and branding rules

This is a fan-made, unofficial project.

- Don't add official Marvel or Sony logos, movie stills, game screenshots or copied fan art.
- New art must be your own and you must be allowed to share it under the MIT License. Say how it was made in your PR.
- App logos (YouTube, Netflix and others) are only used to show which app is which.

## Code of conduct

Be kind and respectful. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
