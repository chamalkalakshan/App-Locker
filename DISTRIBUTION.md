# Distribution Guide — App Locker

---

## GitHub Releases (recommended)

The easiest way to distribute a signed APK without committing it to the repository is via
**GitHub Releases**, triggered automatically by pushing a version tag.

### How it works

1. Push a version tag — the GitHub Actions workflow in `.github/workflows/release.yml` runs.
2. The workflow builds a signed release APK on GitHub's servers using secrets you store in the repo settings.
3. The APK is uploaded as a release asset named `App-Locker-vX.X.X.apk`.
4. Users download it from the Releases page — no APK ever enters the git history.

### One-time setup

**Step 1 — Add repository secrets**

Go to: GitHub repo → Settings → Secrets and variables → Actions → New repository secret

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Your `release.keystore` encoded as base64 (see below) |
| `KEYSTORE_STORE_PASSWORD` | The store password you chose when generating the keystore |
| `KEYSTORE_KEY_ALIAS` | The key alias (default: `applock`) |
| `KEYSTORE_KEY_PASSWORD` | The key password |

To get the base64 value of your keystore on Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | clip
```

On macOS / Linux:

```bash
base64 -w 0 release.keystore
```

Paste the output as the `KEYSTORE_BASE64` secret value.

**Step 2 — Push a tag to trigger a release**

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow runs, builds the APK, creates a GitHub Release, and attaches the APK automatically.

---

## Building from Source

### Prerequisites

- Android Studio Ladybug (2024.2) or newer
- Android SDK 36 (downloadable from Android Studio SDK Manager)
- JDK 17 (bundled with Android Studio)

### Debug Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the Android debug keystore and can be installed on any device with USB debugging enabled.

### Release Build

A release build requires a signing keystore.

**Step 1 — Generate a keystore (one-time)**

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias applock \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Keep `release.keystore` safe. It cannot be recovered if lost.

**Step 2 — Create `keystore.properties`**

Copy the template and fill in your values:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties`:

```properties
storeFile=../release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=applock
keyPassword=YOUR_KEY_PASSWORD
```

`keystore.properties` is excluded from version control by `.gitignore`.

**Step 3 — Build**

```bash
# Signed APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Signed AAB (recommended for Play Store)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

---

## Installing the APK

### ADB (recommended for development)

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

ADB bypasses the Play Protect installer check. Requires USB debugging enabled on the device.

### Direct install

Transfer the APK to the device and tap to install. The device must have "Install from unknown sources" enabled for the file manager or browser used to open the file.

### Play Protect warnings

Sideloaded APKs that declare an AccessibilityService will receive a Google Play Protect warning or block on Android 10+. This is a blanket policy for sideloaded accessibility apps, not specific to App Locker.

To avoid Play Protect warnings entirely, distribute through the Google Play Store.

---

## Restricted Settings (Android 13+)

On Android 13 and later, apps sideloaded from outside the Play Store are subject to **Restricted Settings**. This can prevent the Accessibility Service from being enabled.

To allow Restricted Settings:

1. Open Settings → Apps → App Locker
2. Tap ⋮ (three-dot menu) → Allow restricted settings
3. Confirm

After allowing, the Accessibility Service can be enabled normally.

---

## Google Play Store Distribution

1. Create a Google Play Developer account (one-time $25 USD fee)
2. Build a release AAB: `./gradlew bundleRelease`
3. Create a new app in Play Console
4. Complete the Accessibility Declaration form (explains exactly how the service is used)
5. Submit for review

Once approved by Google, the app installs without any Play Protect warning.

---

## Versioning

Update `versionCode` and `versionName` in `app/build.gradle.kts` before each release:

```kotlin
defaultConfig {
    versionCode = 2          // increment for every release
    versionName = "1.1.0"   // semantic version shown to users
}
```

---

## Security Notes

- Never commit `keystore.properties` or `*.keystore` to version control
- The `.gitignore` already excludes these files
- Store the release keystore in a secure, backed-up location separate from the code repository
- Use a unique, strong password for both the store and the key alias
