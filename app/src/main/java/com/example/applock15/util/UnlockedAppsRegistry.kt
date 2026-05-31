package com.locker.app.util

/**
 * In-memory set of packages the user has authenticated for this session.
 * Cleared on screen-off and service restart, so locks reapply automatically.
 */
object UnlockedAppsRegistry {
    private val packages = mutableSetOf<String>()

    @Synchronized fun unlock(pkg: String) { packages.add(pkg) }
    @Synchronized fun lock(pkg: String) { packages.remove(pkg) }
    @Synchronized fun lockAll() { packages.clear() }
    @Synchronized fun isUnlocked(pkg: String) = pkg in packages
}
