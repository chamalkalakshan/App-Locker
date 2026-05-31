package com.locker.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Collections

// ── Relock mode ───────────────────────────────────────────────────────────────

/**
 * Controls when a temporarily-unlocked app should become locked again.
 *
 * [delayMs] is the timer duration for timed modes.
 * 0            → no timer (cleared by markAppLeft or clearTemporaryUnlocks)
 * Long.MAX_VALUE → never expires (MANUAL mode)
 */
enum class RelockMode(
    val id: Int,
    val label: String,
    val description: String,
    val delayMs: Long
) {
    IMMEDIATELY(
        0,
        "Immediately",
        "Relock as soon as you leave the protected app. Most secure option.",
        0L
    ),
    SCREEN_OFF(
        1,
        "Screen off only",
        "Stay unlocked until the screen turns off. Good balance of security and convenience.",
        0L
    ),
    AFTER_15S(
        2,
        "After 15 seconds",
        "Relock 15 seconds after you leave the app.",
        15_000L
    ),
    AFTER_30S(
        3,
        "After 30 seconds",
        "Relock 30 seconds after you leave the app.",
        30_000L
    ),
    AFTER_1M(
        4,
        "After 1 minute",
        "Relock 1 minute after you leave the app.",
        60_000L
    ),
    AFTER_5M(
        5,
        "After 5 minutes",
        "Relock 5 minutes after you leave the app.",
        300_000L
    ),
    MANUAL(
        6,
        "Manual only",
        "Stay unlocked until App Locker is disabled or the device restarts. Least secure — use with care.",
        Long.MAX_VALUE
    )
}

// ── Repository ────────────────────────────────────────────────────────────────

class LockedAppsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ── Persistent storage ────────────────────────────────────────────────────

    fun getLockedApps(): Set<String> =
        prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun setLockedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_LOCKED, packages).apply()
    }

    fun toggleLock(packageName: String): Boolean {
        val current = getLockedApps().toMutableSet()
        val nowLocked = if (packageName in current) {
            current.remove(packageName); false
        } else {
            current.add(packageName); true
        }
        setLockedApps(current)
        return nowLocked
    }

    fun isLocked(packageName: String) = packageName in getLockedApps()

    fun isAntiTamperEnabled() = prefs.getBoolean(KEY_ANTI_TAMPER, false)

    fun setAntiTamperEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_ANTI_TAMPER, enabled).apply()

    fun isOnboardingComplete() = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete() = prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()

    fun getRelockMode(): RelockMode {
        val id = prefs.getInt(KEY_RELOCK_MODE, RelockMode.IMMEDIATELY.id)
        return RelockMode.entries.firstOrNull { it.id == id } ?: RelockMode.IMMEDIATELY
    }

    fun setRelockMode(mode: RelockMode) =
        prefs.edit().putInt(KEY_RELOCK_MODE, mode.id).apply()

    // ── In-memory session state ───────────────────────────────────────────────
    //
    // All state below lives in the process-wide companion `State` object so it
    // is shared across the AccessibilityService and LockActivity instances.
    //
    // pendingLock        – package currently awaiting authentication.
    //                      Set on detection; cleared only on successful auth.
    //
    // temporarilyUnlocked – packages authenticated this session.
    //
    // unlockExpiry        – expiry epoch-ms per package:
    //                       0            → no timer (IMMEDIATELY / SCREEN_OFF)
    //                       Long.MAX_VALUE → never expires (MANUAL)
    //                       other         → timed modes

    fun setPendingLock(pkg: String) { State.pendingLock = pkg }
    fun getPendingLock(): String? = State.pendingLock
    fun clearPendingLock() { State.pendingLock = null }

    /**
     * Record a successful authentication for [pkg].
     * Expiry is computed once from the current [RelockMode] and stored;
     * future calls to [isTemporarilyUnlocked] are pure in-memory comparisons.
     */
    fun markTemporarilyUnlocked(pkg: String) {
        val expiry = when (val mode = getRelockMode()) {
            RelockMode.IMMEDIATELY,
            RelockMode.SCREEN_OFF -> 0L
            RelockMode.MANUAL     -> Long.MAX_VALUE
            else                  -> System.currentTimeMillis() + mode.delayMs
        }
        State.temporarilyUnlocked.add(pkg)
        State.unlockExpiry[pkg] = expiry
    }

    /** True if [pkg] has been authenticated and the unlock has not yet expired. */
    fun isTemporarilyUnlocked(pkg: String): Boolean {
        if (!State.temporarilyUnlocked.contains(pkg)) return false
        return when (val expiry = State.unlockExpiry[pkg] ?: 0L) {
            0L             -> true   // IMMEDIATELY / SCREEN_OFF: no time limit
            Long.MAX_VALUE -> true   // MANUAL: never expires
            else           -> System.currentTimeMillis() < expiry
        }
    }

    /** Returns true if the lock screen should be shown for [pkg]. */
    fun shouldRelock(pkg: String) = !isTemporarilyUnlocked(pkg)

    /**
     * Called by the AccessibilityService when [pkg] is no longer in the foreground.
     * For [RelockMode.IMMEDIATELY], removes the temporary unlock so the user must
     * authenticate again on the next visit.
     */
    fun markAppLeft(pkg: String) {
        if (getRelockMode() == RelockMode.IMMEDIATELY) {
            clearTemporaryUnlock(pkg)
        }
        // Timed modes: expiry was baked in at markTemporarilyUnlocked — no action needed.
        // SCREEN_OFF / MANUAL: unlock persists until clearTemporaryUnlocks() or never.
    }

    /** Removes the temporary unlock for a single package. */
    fun clearTemporaryUnlock(pkg: String) {
        State.temporarilyUnlocked.remove(pkg)
        State.unlockExpiry.remove(pkg)
    }

    /**
     * Clears all temporary unlocks.  Called on screen-off.
     *
     * MANUAL mode is exempt: the user explicitly chose "stay unlocked through screen-off".
     * All other modes are cleared so the user re-authenticates on waking the device.
     * pendingLock is always reset (any in-progress detection starts fresh).
     */
    fun clearTemporaryUnlocks() {
        if (getRelockMode() != RelockMode.MANUAL) {
            State.temporarilyUnlocked.clear()
            State.unlockExpiry.clear()
        }
        State.pendingLock = null
    }

    companion object {
        /** Exposed so AccessibilityService can register a SharedPreferences listener. */
        const val PREFS_FILE = "locked_apps"

        private const val KEY_LOCKED      = "packages"
        private const val KEY_ANTI_TAMPER = "anti_tamper"
        private const val KEY_ONBOARDING  = "onboarding_done"
        private const val KEY_RELOCK_MODE = "relock_mode"

        private object State {
            @Volatile var pendingLock: String? = null
            val temporarilyUnlocked: MutableSet<String> =
                Collections.synchronizedSet(mutableSetOf())
            val unlockExpiry: MutableMap<String, Long> =
                Collections.synchronizedMap(mutableMapOf())
        }
    }
}
