package com.locker.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.locker.app.data.LockedAppsRepository
import com.locker.app.ui.lock.LockActivity
import java.lang.ref.WeakReference

class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var repository: LockedAppsRepository
    private lateinit var windowManager: WindowManager
    private lateinit var sharedPrefs: SharedPreferences

    // ── Package-list cache ────────────────────────────────────────────────────

    @Volatile private var cachedLockedPackages: Set<String>? = null
    @Volatile private var cachedAntiTamper: Boolean? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "packages"    -> cachedLockedPackages = null
            "anti_tamper" -> { cachedLockedPackages = null; cachedAntiTamper = null }
        }
    }

    private fun lockedPackages(): Set<String> =
        cachedLockedPackages ?: repository.getLockedApps().also { cachedLockedPackages = it }

    private fun antiTamperEnabled(): Boolean =
        cachedAntiTamper ?: repository.isAntiTamperEnabled().also { cachedAntiTamper = it }

    // ── Launcher package cache ────────────────────────────────────────────────
    //
    // Resolved once lazily from the PackageManager. Home-screen launchers must be
    // treated the same as SystemUI: navigating to the home screen during or after
    // a Back gesture should NOT count as "leaving" the current app.
    private val launcherPackages: Set<String> by lazy {
        try {
            packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                .map { it.activityInfo.packageName }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // ── Foreground tracking ───────────────────────────────────────────────────
    //
    // lastRealFgPackage tracks the last package that is a *real* user-facing app
    // (i.e. not systemUI, not a launcher, not a permission dialog, not our own
    // LockActivity).
    //
    // This is the ONLY variable used to decide whether a real app switch occurred.
    // It is intentionally NOT updated when a transient/ignored package appears.
    //
    // Root cause of the Back-gesture false-trigger (fixed here):
    //   Old code kept a single `currentFgPackage` updated for ALL packages.
    //   On Android 14+ the system fires TYPE_WINDOW_STATE_CHANGED with
    //   "com.android.systemui" momentarily during a Back gesture animation.
    //   That caused:
    //     prev="com.whatsapp" → pkg="com.android.systemui"
    //     The old guard only blocked if PREV was systemUI, not if the NEW package was.
    //   → markAppLeft("com.whatsapp") was called.
    //   → Temporary unlock was cleared for IMMEDIATELY mode.
    //   → WhatsApp was re-locked when the animation finished and WhatsApp resumed.
    @Volatile private var lastRealFgPackage: String? = null

    // ── Privacy overlay ───────────────────────────────────────────────────────

    private var overlayView: View? = null

    // ── Screen-off receiver ───────────────────────────────────────────────────

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            repository.clearTemporaryUnlocks()
            LockActivity.isVisible = false
            hidePrivacyOverlay()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository    = LockedAppsRepository(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        weakSelf      = WeakReference(this)

        sharedPrefs = applicationContext.getSharedPreferences(
            LockedAppsRepository.PREFS_FILE, Context.MODE_PRIVATE
        )
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onInterrupt() {
        repository.clearTemporaryUnlocks()
        hidePrivacyOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        hidePrivacyOverlay()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        weakSelf = null
    }

    // ── Accessibility events ──────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return

                // ── Step 1: ignore transient / system packages ─────────────────
                //
                // System UI, home launchers, permission controllers, and package
                // installers are "invisible" to our foreground-tracking logic.
                // They appear temporarily during gesture animations, permission
                // dialogs, and home-screen transitions. If we treated them as real
                // app changes we would:
                //   (a) call markAppLeft(whatsapp) when systemUI appears briefly
                //       during a Back gesture, and
                //   (b) consider "going home" as leaving the protected app.
                // Both would incorrectly relock the app.
                if (isIgnoredPackage(pkg)) return

                val prev = lastRealFgPackage

                if (prev == pkg) {
                    // ── Step 2a: same real app ─────────────────────────────────
                    //
                    // This fires for: Back gesture within the app, internal Activity
                    // transitions, dialog opening/closing, keyboard shown/hidden,
                    // in-app navigation (Fragments, BottomSheet, etc.).
                    //
                    // Do NOT call markAppLeft — the user has not left the app.
                    // Still call evaluate so that a timed unlock that has since
                    // expired will re-lock the app even without an app switch.
                    evaluate(pkg)
                    return
                }

                // ── Step 2b: real app switch ───────────────────────────────────
                //
                // Both the previous AND the new foreground are real (non-ignored)
                // apps. This is when we consider the user to have "left" prev.
                if (prev != null) {
                    repository.markAppLeft(prev)
                }
                lastRealFgPackage = pkg
                evaluate(pkg)
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Carries no package name; re-evaluate the last known real foreground.
                // Catches Android 14/15/16 window transitions where TYPE_WINDOW_STATE_CHANGED
                // is not emitted (e.g. returning to a locked app from Recents).
                evaluate(lastRealFgPackage ?: return)
            }
        }
    }

    // ── Ignored-package predicate ─────────────────────────────────────────────

    /**
     * Returns true for packages that should not update [lastRealFgPackage] or
     * trigger [repository.markAppLeft].
     *
     * These are OS-level / transient packages whose temporary appearance in the
     * foreground does not represent a user-initiated app switch:
     *  • Our own LockActivity (would create a recursive lock loop)
     *  • Android System UI (status bar, Recents panel, navigation gesture layer)
     *  • Home-screen launchers (going home is not the same as switching apps)
     *  • Permission controllers (appear as a dialog over the current app)
     *  • Package installers (appear as a dialog over the current app)
     */
    private fun isIgnoredPackage(pkg: String): Boolean =
        pkg == applicationContext.packageName ||
        pkg == "com.android.systemui" ||
        pkg == "com.google.android.permissioncontroller" ||
        pkg == "com.android.permissioncontroller" ||
        pkg == "com.google.android.packageinstaller" ||
        pkg == "com.android.packageinstaller" ||
        pkg == "com.miui.packageinstaller" ||
        pkg in launcherPackages

    // ── Core lock logic ───────────────────────────────────────────────────────

    private fun evaluate(pkg: String) {
        // Belt-and-suspenders: isIgnoredPackage is checked before calling evaluate,
        // but guard here too in case evaluate is called from TYPE_WINDOWS_CHANGED
        // with a stale lastRealFgPackage that later became an ignored package.
        if (isIgnoredPackage(pkg)) return

        val extra      = if (antiTamperEnabled()) ANTI_TAMPER_PACKAGES else emptySet()
        val isLockedApp = pkg in lockedPackages() || pkg in extra
        val hasPending  = repository.getPendingLock() == pkg

        // Safe foreground app: remove any lingering overlay.
        if (!isLockedApp && !hasPending) {
            hidePrivacyOverlay()
            return
        }

        // Authenticated and not yet expired: let the app through.
        // shouldRelock() is the canonical check; it returns false when
        // isTemporarilyUnlocked() returns true (session still valid).
        if (!repository.shouldRelock(pkg)) {
            hidePrivacyOverlay()
            return
        }

        // Locked app in the foreground: place the privacy overlay immediately
        // (covers the first frame) then start LockActivity.
        showPrivacyOverlay()
        if (LockActivity.isVisible) return

        repository.setPendingLock(pkg)
        startActivity(
            LockActivity.createIntent(applicationContext, pkg).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        )
    }

    // ── Privacy overlay ───────────────────────────────────────────────────────

    fun showPrivacyOverlay() {
        if (overlayView != null) return
        val view = View(this).apply { setBackgroundColor(Color.BLACK) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE
        )
        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (_: Exception) {}
    }

    fun hidePrivacyOverlay() {
        overlayView?.let { v ->
            try { windowManager.removeView(v) } catch (_: Exception) {}
            overlayView = null
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private val ANTI_TAMPER_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller"
        )

        @Volatile private var weakSelf: WeakReference<AppLockAccessibilityService>? = null

        fun hideOverlay() { weakSelf?.get()?.hidePrivacyOverlay() }
        fun showOverlay() { weakSelf?.get()?.showPrivacyOverlay() }
    }
}
