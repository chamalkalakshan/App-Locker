# Privacy Policy — App Locker

**Last updated:** 2026-05-31

---

## Summary

App Locker does not collect, transmit, or share any personal data.  
Everything stays on your device.

---

## What Data Is Stored

App Locker stores the following data **locally on your device only**:

| Data | Where | Purpose |
|---|---|---|
| List of locked app package names | SharedPreferences | Know which apps to protect |
| PIN (AES-256-GCM encrypted) | EncryptedSharedPreferences | Authenticate unlock requests |
| Relock mode preference | SharedPreferences | Remember when to relock |
| Anti-tamper preference | SharedPreferences | Optional protection setting |
| Onboarding completion flag | SharedPreferences | Skip onboarding after first run |

No other data is stored. There is no database, no file storage beyond the above, and no cloud sync.

---

## What Data Is NOT Collected

App Locker **never** collects or processes:

- Screen content, text fields, passwords, or clipboard data
- Keystrokes or input events
- App usage history or session durations
- Device identifiers (IMEI, Android ID, advertising ID, etc.)
- Location data
- Contact, calendar, or media data
- Network activity of other apps
- Crash reports or diagnostics

---

## Network Access

App Locker has **no INTERNET permission**.

It cannot make network requests of any kind. No data can leave the device through the app.

---

## Accessibility Service

App Locker uses Android's Accessibility Service for **one purpose only**: detecting which app is currently in the foreground, so it can show the lock screen when a protected app is opened.

The Accessibility Service is configured with the minimum possible capabilities:

- `canRetrieveWindowContent="false"` — cannot read screen content
- `canPerformGestures="false"` — cannot simulate taps or swipes
- `accessibilityFlags="flagDefault"` — no view-hierarchy access, no key-event interception
- Only listens for `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOWS_CHANGED` events
- Reads only the **package name** of the foreground window — nothing else

---

## Permissions Explained

| Permission | Reason |
|---|---|
| `USE_BIOMETRIC` | Required to invoke the system fingerprint / face authentication dialog |
| `WAKE_LOCK` | Required to turn the screen on when a protected app is launched while the device is asleep |
| Accessibility Service | Required to detect the foreground app (see above) |

The following permissions are **intentionally absent**: `INTERNET`, `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `CAMERA`, `CONTACTS`, `LOCATION`, `MICROPHONE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `BIND_DEVICE_ADMIN`.

---

## How to Delete Your Data

**Option 1 — In-app reset:**  
Settings → About & Privacy → Delete All Data

This removes all locked app selections, your PIN, and all preferences.

**Option 2 — Uninstall:**  
Uninstalling App Locker removes all stored data automatically.

---

## Open Source

App Locker is open-source. You can inspect the full source code to verify that no data collection, tracking, or transmission occurs.

---

## Contact

This is an open-source project. For questions or concerns, open an issue on the GitHub repository.
