# App Locker

Privacy focused Android app locker built with Kotlin, Jetpack Compose, AccessibilityService, PIN/biometric authentication, local-only storage, app search, and Android 15/16 support.

## Download

You can download the latest APK from the [GitHub Releases page](../../releases/latest).

Download the newest file named something like:

```
App-Locker-v1.0.0.apk
```

> **Note:** Because App Locker uses AccessibilityService, sideloaded APKs may trigger a
> Google Play Protect warning. See the [Installation Notes](#installation-notes) and
> [Google Play Protect Notice](#google-play-protect-notice) sections below.

## Features

- Lock selected Android apps
- PIN and biometric authentication
- User-selectable relock behavior
- App search
- Locked apps shown at the top
- Android 15/16 focused compatibility
- Local-only settings storage
- No internet permission
- No analytics
- No cloud sync

## Why Accessibility Permission Is Needed

App Locker uses Android AccessibilityService only to detect when a protected app is opened, so it can show the lock screen.

App Locker does not:

- read screen text
- perform gestures
- capture screenshots
- collect personal data
- send data to any server
- use analytics
- use cloud sync

## Privacy

App Locker is designed to work locally on the device.

Stored data may include:

- selected locked apps
- PIN authentication data
- relock settings
- app lock preferences

This data stays on the device.

See [PRIVACY.md](PRIVACY.md) for more details.

## Installation Notes

App Locker uses Android AccessibilityService to detect when protected apps are opened.

Because AccessibilityService is a sensitive Android permission, sideloaded APKs may trigger Google Play Protect warnings or Android Restricted Settings prompts.

For safety, users should keep Google Play Protect enabled whenever possible. This project does not attempt to bypass Play Protect or Android security protections.

If Android blocks Accessibility access after sideloading, users may need to manually allow Restricted Settings for this app.

## How to Allow Restricted Settings

Steps may vary depending on Android version and phone brand:

1. Open Android Settings.
2. Go to Apps.
3. Select App Locker.
4. Tap the three-dot menu in the top-right corner.
5. Tap Allow restricted settings.
6. Confirm the warning.
7. Go back to Settings.
8. Open Accessibility.
9. Select App Locker.
10. Enable the App Locker Accessibility Service.

If the "Allow restricted settings" option is missing, the device manufacturer may have changed or removed this feature.

## Google Play Protect Notice

Google Play Protect may warn about or block sideloaded APKs that use sensitive permissions such as AccessibilityService.

App Locker is designed to be transparent and privacy-friendly:

- no internet permission
- no analytics
- no cloud sync
- no screen text reading
- no gesture automation
- no screenshot capture

## Important Limitations

A normal third-party Android app cannot provide system-level locking on every Android device.

Users may still be able to:

- disable Accessibility access
- force stop the app
- uninstall the app
- encounter Play Protect or Restricted Settings warnings when sideloading
- briefly see protected app content before the lock screen appears

## Build from Source

**Requirements**

- Android Studio Ladybug or newer
- Android SDK 36
- JDK 17

**Steps**

```bash
git clone https://github.com/YOUR_USERNAME/app-locker.git
cd app-locker
./gradlew assembleRelease
```

The debug APK is built automatically by Android Studio. For a signed release APK see [DISTRIBUTION.md](DISTRIBUTION.md).

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Foreground detection | AccessibilityService |
| Biometric auth | androidx.biometric |
| Secure PIN storage | EncryptedSharedPreferences (AES-256-GCM) |
| Min SDK | 26 (Android 8) |
| Target SDK | 36 (Android 16) |

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Show fingerprint / face unlock prompt |
| `WAKE_LOCK` | Turn screen on when a locked app opens in the background |
| Accessibility Service | Detect the foreground app package name |

No other permissions are requested.

## License

MIT — see [LICENSE](LICENSE).
