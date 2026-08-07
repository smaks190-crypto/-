package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class SecurityManager(context: Context) {
    private val oldPrefs: SharedPreferences =
        context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences

    init {
        val securePrefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "app_security_prefs_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard SharedPreferences if encrypted initialization fails
            context.getSharedPreferences("app_security_prefs_secure_fallback", Context.MODE_PRIVATE)
        }
        prefs = securePrefs

        // One-time migration
        if (oldPrefs.contains(KEY_PIN_HASH)) {
            val oldHash = oldPrefs.getString(KEY_PIN_HASH, "") ?: ""
            val oldEnabled = oldPrefs.getBoolean(KEY_PIN_ENABLED, false)
            val oldBiometric = oldPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
            val oldAttempts = oldPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
            val oldBlockedUntil = oldPrefs.getLong(KEY_BLOCKED_UNTIL, 0L)

            prefs.edit().apply {
                putString(KEY_PIN_HASH, oldHash)
                putBoolean(KEY_PIN_ENABLED, oldEnabled)
                putBoolean(KEY_BIOMETRIC_ENABLED, oldBiometric)
                putInt(KEY_FAILED_ATTEMPTS, oldAttempts)
                putLong(KEY_BLOCKED_UNTIL, oldBlockedUntil)
                apply()
            }

            // Clear old preferences to ensure one-time execution
            oldPrefs.edit().clear().apply()
        }
    }

    fun isPinEnabled(): Boolean {
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash().isNotEmpty()
    }

    fun setPinEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIN_ENABLED, enabled).apply()
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
    }

    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_BLOCKED_UNTIL, 0L)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = getPinHash()
        return storedHash.isNotEmpty() && storedHash == hashPin(pin)
    }

    private fun getPinHash(): String {
        return prefs.getString(KEY_PIN_HASH, "") ?: ""
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isPinEnabled()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Brute-force protection functions
    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun getBlockedUntil(): Long {
        return prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
    }

    fun isBlocked(): Boolean {
        return System.currentTimeMillis() < getBlockedUntil()
    }

    fun getRemainingBlockTimeSeconds(): Long {
        val remainingMs = getBlockedUntil() - System.currentTimeMillis()
        return if (remainingMs > 0) remainingMs / 1000 else 0
    }

    fun handleFailedAttempt() {
        val currentAttempts = getFailedAttempts() + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)
        
        if (currentAttempts >= 5) {
            val delayMs = when (currentAttempts) {
                5 -> 30000L      // 30 seconds
                6 -> 60000L      // 1 minute
                else -> 300000L  // 5 minutes
            }
            editor.putLong(KEY_BLOCKED_UNTIL, System.currentTimeMillis() + delayMs)
        }
        editor.apply()
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_BLOCKED_UNTIL, 0L)
            .apply()
    }

    companion object {
        private const val KEY_PIN_ENABLED = "key_pin_enabled"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        private const val KEY_BLOCKED_UNTIL = "key_blocked_until"
    }
}
