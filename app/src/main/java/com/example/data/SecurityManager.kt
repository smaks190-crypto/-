package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityManager(context: Context) {
    private val appContext = context.applicationContext
    private val oldPrefs: SharedPreferences =
        appContext.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences

    init {
        val securePrefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "app_security_prefs_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard SharedPreferences if encrypted initialization fails
            appContext.getSharedPreferences("app_security_prefs_secure_fallback", Context.MODE_PRIVATE)
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
        if (enabled) {
            com.example.davidapp.AchievementManager.unlock(appContext, "security_backup")
        }
    }

    fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_PIN_ENABLED, true)
            .apply()
        com.example.davidapp.AchievementManager.unlock(appContext, "security_backup")
    }

    fun removePin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_GUEST_PIN_HASH)
            .putBoolean(KEY_PIN_ENABLED, false)
            .putBoolean(KEY_GUEST_PIN_ENABLED, false)
            .putBoolean(KEY_GUEST_SESSION_ACTIVE, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_BLOCKED_UNTIL, 0L)
            .apply()
    }

    // --- Guest / Decoy PIN support ---
    fun isGuestPinEnabled(): Boolean {
        return isPinEnabled() &&
            prefs.getBoolean(KEY_GUEST_PIN_ENABLED, false) &&
            getGuestPinHash().isNotEmpty()
    }

    fun setGuestPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        prefs.edit()
            .putString(KEY_GUEST_PIN_HASH, hash)
            .putBoolean(KEY_GUEST_PIN_ENABLED, true)
            .apply()
    }

    fun removeGuestPin() {
        prefs.edit()
            .remove(KEY_GUEST_PIN_HASH)
            .putBoolean(KEY_GUEST_PIN_ENABLED, false)
            .putBoolean(KEY_GUEST_SESSION_ACTIVE, false)
            .apply()
    }

    fun isGuestSessionActive(): Boolean {
        return prefs.getBoolean(KEY_GUEST_SESSION_ACTIVE, false)
    }

    fun setGuestSessionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_GUEST_SESSION_ACTIVE, active).apply()
    }

    fun verifyGuestPin(pin: String): Boolean {
        if (!isGuestPinEnabled()) return false
        val storedGuestHash = getGuestPinHash()
        return verifyHash(pin, storedGuestHash)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = getPinHash()
        if (storedHash.isEmpty()) return false

        // 1. Check primary PIN
        if (verifyHash(pin, storedHash, upgradeLegacy = true)) {
            setGuestSessionActive(false)
            return true
        }

        // 2. Check Guest / Decoy PIN if enabled
        if (verifyGuestPin(pin)) {
            setGuestSessionActive(true)
            return true
        }

        return false
    }

    private fun verifyHash(pin: String, storedHash: String, upgradeLegacy: Boolean = false): Boolean {
        if (storedHash.isEmpty()) return false

        // 1. Check if stored hash uses modern PBKDF2 format
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            val parts = storedHash.removePrefix(PBKDF2_PREFIX).split("$")
            if (parts.size == 2) {
                try {
                    val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                    val expectedHash = parts[1]
                    val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val actualHashBytes = factory.generateSecret(spec).encoded
                    val actualHash = Base64.encodeToString(actualHashBytes, Base64.NO_WRAP)
                    return MessageDigest.isEqual(
                        expectedHash.toByteArray(Charsets.UTF_8),
                        actualHash.toByteArray(Charsets.UTF_8)
                    )
                } catch (e: Exception) {
                    return false
                }
            }
        }

        // 2. Backward compatibility with legacy SHA-256
        val legacy = legacySha256(pin)
        val matches = MessageDigest.isEqual(
            storedHash.toByteArray(Charsets.UTF_8),
            legacy.toByteArray(Charsets.UTF_8)
        )
        if (matches && upgradeLegacy) {
            setPin(pin)
        }
        return matches
    }

    private fun getPinHash(): String {
        return prefs.getString(KEY_PIN_HASH, "") ?: ""
    }

    private fun getGuestPinHash(): String {
        return prefs.getString(KEY_GUEST_PIN_HASH, "") ?: ""
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

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$PBKDF2_PREFIX$saltBase64$$hashBase64"
    }

    private fun legacySha256(pin: String): String {
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
        private const val KEY_GUEST_PIN_ENABLED = "key_guest_pin_enabled"
        private const val KEY_GUEST_PIN_HASH = "key_guest_pin_hash"
        private const val KEY_GUEST_SESSION_ACTIVE = "key_guest_session_active"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        private const val KEY_BLOCKED_UNTIL = "key_blocked_until"

        private const val PBKDF2_PREFIX = "pbkdf2$"
        private const val PBKDF2_ITERATIONS = 12000
        private const val PBKDF2_KEY_LENGTH = 256
    }
}
