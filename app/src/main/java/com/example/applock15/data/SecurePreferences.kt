package com.locker.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val prefs = buildPrefs(context)

    private fun buildPrefs(context: Context) = try {
        create(context)
    } catch (_: Exception) {
        // Corrupted keystore: wipe and recreate
        context.deleteSharedPreferences(FILE_NAME)
        create(context)
    }

    private fun create(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setPin(pin: String) = prefs.edit().putString(KEY_PIN, pin).apply()
    fun getPin(): String? = prefs.getString(KEY_PIN, null)
    fun hasPin(): Boolean = getPin() != null
    fun clearPin() = prefs.edit().remove(KEY_PIN).apply()

    companion object {
        private const val FILE_NAME = "applock_secure"
        private const val KEY_PIN = "pin"
    }
}
