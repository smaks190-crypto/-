package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Менеджер безопасности приложения David Finance AI.
 *
 * Реализует принцип Fail Closed:
 * - Никогда не переключается на незашифрованный SharedPreferences при ошибках Android Keystore / EncryptedSharedPreferences.
 * - При недоступности защищённого хранилища блокирует доступ (isPinEnabled() == true, verifyPin() == false).
 * - Безопасно мигрирует старые настройки только после синхронного commit() и проверки чтения из зашифрованного хранилища.
 * - Защищает как основной, так и гостевой PIN-код от брутфорса (перебора).
 * - Не записывает PIN-коды, хеши, соли или ключи в системные логи.
 */
class SecurityManager(
    context: Context,
    private val encryptedPrefsFactory: ((Context) -> SharedPreferences)? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val appContext = context.applicationContext
    private val oldPrefs: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyFallbackPrefs: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var storageError: Boolean = false

    init {
        initializeSecureStorage()
    }

    /**
     * Инициализирует EncryptedSharedPreferences с однократным безопасным повтором при временном сбое Keystore
     * без удаления пользовательских данных и без fallback на обычный SharedPreferences.
     */
    @Synchronized
    private fun initializeSecureStorage(): Boolean {
        val loadedPrefs = createEncryptedPrefsWithRetry()
        if (loadedPrefs == null) {
            prefs = null
            storageError = true
            return false
        }

        prefs = loadedPrefs
        storageError = false

        migrateLegacyPrefsSafely(source = oldPrefs, target = loadedPrefs)
        migrateLegacyPrefsSafely(source = legacyFallbackPrefs, target = loadedPrefs)
        return true
    }

    private fun createEncryptedPrefsWithRetry(): SharedPreferences? {
        val factory = encryptedPrefsFactory ?: ::createDefaultEncryptedPrefs
        repeat(MAX_INIT_ATTEMPTS) {
            try {
                return factory(appContext)
            } catch (_: Throwable) {
                // Никакие секреты не логируются; пробуем повторную инициализацию при кратковременном сбое Keystore
            }
        }
        return null
    }

    private fun createDefaultEncryptedPrefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Безопасная миграция настроек из старого хранилища в EncryptedSharedPreferences:
     * 1. Не перезаписывает уже существующий PIN в зашифрованном хранилище.
     * 2. Использует синхронный commit() вместо асинхронного apply().
     * 3. Проверяет корректность записанных данных чтением из target.
     * 4. Удаляет исходные настройки ТОЛЬКО после подтверждения успешной миграции.
     */
    private fun migrateLegacyPrefsSafely(source: SharedPreferences, target: SharedPreferences) {
        try {
            val hasLegacyData = source.contains(KEY_PIN_HASH) ||
                source.contains(KEY_PIN_ENABLED) ||
                source.contains(KEY_GUEST_PIN_HASH) ||
                source.contains(KEY_BIOMETRIC_ENABLED) ||
                source.contains(KEY_FAILED_ATTEMPTS) ||
                source.contains(KEY_BLOCKED_UNTIL)

            if (!hasLegacyData) return

            val targetAlreadyHasPin = !target.getString(KEY_PIN_HASH, "").isNullOrEmpty()
            if (targetAlreadyHasPin) {
                // Если в зашифрованном хранилище уже есть актуальный PIN,
                // переносим только более строгую блокировку (если она была) и очищаем старый открытый файл
                val oldBlockedUntil = source.getLong(KEY_BLOCKED_UNTIL, 0L)
                val currentBlockedUntil = target.getLong(KEY_BLOCKED_UNTIL, 0L)
                if (oldBlockedUntil > currentBlockedUntil) {
                    target.edit().putLong(KEY_BLOCKED_UNTIL, oldBlockedUntil).commit()
                }
                source.edit().clear().commit()
                return
            }

            val oldHash = source.getString(KEY_PIN_HASH, "") ?: ""
            val oldEnabled = source.getBoolean(KEY_PIN_ENABLED, false)
            val oldGuestHash = source.getString(KEY_GUEST_PIN_HASH, "") ?: ""
            val oldGuestEnabled = source.getBoolean(KEY_GUEST_PIN_ENABLED, false)
            val oldGuestSession = source.getBoolean(KEY_GUEST_SESSION_ACTIVE, false)
            val oldBiometric = source.getBoolean(KEY_BIOMETRIC_ENABLED, false)
            val oldAttempts = source.getInt(KEY_FAILED_ATTEMPTS, 0)
            val oldBlockedUntil = source.getLong(KEY_BLOCKED_UNTIL, 0L)

            val committed = target.edit().apply {
                putString(KEY_PIN_HASH, oldHash)
                putBoolean(KEY_PIN_ENABLED, oldEnabled)
                if (oldGuestHash.isNotEmpty()) {
                    putString(KEY_GUEST_PIN_HASH, oldGuestHash)
                }
                putBoolean(KEY_GUEST_PIN_ENABLED, oldGuestEnabled)
                putBoolean(KEY_GUEST_SESSION_ACTIVE, oldGuestSession)
                putBoolean(KEY_BIOMETRIC_ENABLED, oldBiometric)
                putInt(KEY_FAILED_ATTEMPTS, oldAttempts)
                putLong(KEY_BLOCKED_UNTIL, oldBlockedUntil)
            }.commit()

            if (!committed) return

            // Верификация успешной записи в EncryptedSharedPreferences перед очисткой исходных настроек
            val verifiedHash = target.getString(KEY_PIN_HASH, null)
            val verifiedEnabled = target.getBoolean(KEY_PIN_ENABLED, !oldEnabled)
            val verifiedGuestHash = target.getString(KEY_GUEST_PIN_HASH, "") ?: ""
            val verifiedBiometric = target.getBoolean(KEY_BIOMETRIC_ENABLED, !oldBiometric)
            val verifiedAttempts = target.getInt(KEY_FAILED_ATTEMPTS, -1)
            val verifiedBlockedUntil = target.getLong(KEY_BLOCKED_UNTIL, -1L)

            val migrationVerified = verifiedHash == oldHash &&
                verifiedEnabled == oldEnabled &&
                verifiedGuestHash == oldGuestHash &&
                verifiedBiometric == oldBiometric &&
                verifiedAttempts == oldAttempts &&
                verifiedBlockedUntil == oldBlockedUntil

            if (migrationVerified) {
                source.edit().clear().commit()
            }
        } catch (_: Throwable) {
            // При ошибке миграции исходные настройки в source НЕ удаляются
        }
    }

    /**
     * Показывает, доступно ли защищённое хранилище EncryptedSharedPreferences.
     */
    fun isStorageAvailable(): Boolean {
        return prefs != null && !storageError
    }

    /**
     * Возвращает true, если инициализация EncryptedSharedPreferences завершилась ошибкой.
     */
    fun isStorageError(): Boolean {
        return storageError || prefs == null
    }

    /**
     * Безопасная попытка повторной инициализации защищённого хранилища без удаления данных.
     * Позволяет восстановиться после временных сбоев Android Keystore.
     */
    @Synchronized
    fun tryRecoverSecureStorage(): Boolean {
        if (prefs != null && !storageError) return true
        return initializeSecureStorage()
    }

    /**
     * Безопасное пересоздание повреждённого хранилища Keystore (например, после системного сброса ключей Android)
     * БЕЗ удаления пользовательской базы данных Room (транзакций, бюджетов, счетов).
     * Вызывается только после явного подтверждения личности пользователя через системную аутентификацию устройства.
     */
    @Synchronized
    fun recoverKeystoreAndResetPinOnly(): Boolean {
        return try {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
            } catch (_: Throwable) {
            }

            try {
                appContext.deleteSharedPreferences(SECURE_PREFS_NAME)
            } catch (_: Throwable) {
                val prefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
                File(prefsDir, "$SECURE_PREFS_NAME.xml").delete()
            }

            val recreatedPrefs = createEncryptedPrefsWithRetry()
            if (recreatedPrefs != null) {
                prefs = recreatedPrefs
                storageError = false
                true
            } else {
                prefs = null
                storageError = true
                false
            }
        } catch (_: Throwable) {
            prefs = null
            storageError = true
            false
        }
    }

    /**
     * Fail Closed: если защищённое хранилище недоступно (prefs == null),
     * метод возвращает true, чтобы приложение оставалось заблокированным и не открывало доступ к финансовым данным.
     */
    fun isPinEnabled(): Boolean {
        val currentPrefs = prefs ?: return true // Fail Closed при ошибке хранилища
        return try {
            currentPrefs.getBoolean(KEY_PIN_ENABLED, false) && getPinHash().isNotEmpty()
        } catch (_: Throwable) {
            storageError = true
            true // Fail Closed
        }
    }

    fun setPinEnabled(enabled: Boolean): Boolean {
        val currentPrefs = prefs ?: return false
        if (enabled && getPinHash().isEmpty()) return false
        return try {
            val committed = currentPrefs.edit().putBoolean(KEY_PIN_ENABLED, enabled).commit()
            if (committed && enabled) {
                com.example.davidapp.AchievementManager.unlock(appContext, "security_backup")
            }
            committed
        } catch (_: Throwable) {
            storageError = true
            false
        }
    }

    fun setPin(pin: String): Boolean {
        if (pin.isBlank()) return false
        val currentPrefs = prefs ?: if (tryRecoverSecureStorage()) prefs else null
        if (currentPrefs == null) return false

        return try {
            val salt = generateSalt()
            val hash = hashPinWithSalt(pin, salt)
            val committed = currentPrefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putBoolean(KEY_PIN_ENABLED, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .commit()
            if (committed) {
                com.example.davidapp.AchievementManager.unlock(appContext, "security_backup")
            }
            committed
        } catch (_: Throwable) {
            storageError = true
            false
        }
    }

    fun removePin(): Boolean {
        val currentPrefs = prefs ?: return false
        return try {
            currentPrefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_GUEST_PIN_HASH)
                .putBoolean(KEY_PIN_ENABLED, false)
                .putBoolean(KEY_GUEST_PIN_ENABLED, false)
                .putBoolean(KEY_GUEST_SESSION_ACTIVE, false)
                .putBoolean(KEY_BIOMETRIC_ENABLED, false)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .commit()
        } catch (_: Throwable) {
            storageError = true
            false
        }
    }

    // --- Guest / Decoy PIN support ---
    fun isGuestPinEnabled(): Boolean {
        val currentPrefs = prefs ?: return false
        return try {
            isPinEnabled() &&
                currentPrefs.getBoolean(KEY_GUEST_PIN_ENABLED, false) &&
                getGuestPinHash().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    fun setGuestPin(pin: String): Boolean {
        if (pin.isBlank()) return false
        val currentPrefs = prefs ?: return false
        if (!isPinEnabled()) return false
        // Гостевой PIN не должен совпадать с основным PIN-кодом
        if (isSameAsPrimaryPin(pin)) return false

        return try {
            val salt = generateSalt()
            val hash = hashPinWithSalt(pin, salt)
            currentPrefs.edit()
                .putString(KEY_GUEST_PIN_HASH, hash)
                .putBoolean(KEY_GUEST_PIN_ENABLED, true)
                .commit()
        } catch (_: Throwable) {
            storageError = true
            false
        }
    }

    fun removeGuestPin(): Boolean {
        val currentPrefs = prefs ?: return false
        return try {
            currentPrefs.edit()
                .remove(KEY_GUEST_PIN_HASH)
                .putBoolean(KEY_GUEST_PIN_ENABLED, false)
                .putBoolean(KEY_GUEST_SESSION_ACTIVE, false)
                .commit()
        } catch (_: Throwable) {
            storageError = true
            false
        }
    }

    fun isGuestSessionActive(): Boolean {
        val currentPrefs = prefs ?: return false
        return try {
            currentPrefs.getBoolean(KEY_GUEST_SESSION_ACTIVE, false)
        } catch (_: Throwable) {
            false
        }
    }

    fun setGuestSessionActive(active: Boolean) {
        val currentPrefs = prefs ?: return
        try {
            currentPrefs.edit().putBoolean(KEY_GUEST_SESSION_ACTIVE, active).commit()
        } catch (_: Throwable) {
            storageError = true
        }
    }

    /**
     * Проверяет совпадение с основным PIN-кодом без изменения статуса сессии и счётчика попыток
     * (используется при создании гостевого PIN, чтобы исключить дублирование с основным PIN).
     */
    fun isSameAsPrimaryPin(pin: String): Boolean {
        if (prefs == null && !tryRecoverSecureStorage()) return false
        val storedHash = getPinHash()
        if (storedHash.isEmpty()) return false
        return verifyHash(pin, storedHash, upgradeLegacyPrimary = false, upgradeLegacyGuest = false)
    }

    /**
     * Проверяет ТОЛЬКО основной PIN-код с учётом защиты от перебора (для смены основного PIN в настройках).
     * Гостевой PIN-код здесь НЕ принимается.
     */
    fun verifyPrimaryPinOnly(pin: String): Boolean {
        if (prefs == null && !tryRecoverSecureStorage()) return false
        if (isBlocked()) return false

        val storedHash = getPinHash()
        if (storedHash.isEmpty()) return false

        return if (verifyHash(pin, storedHash, upgradeLegacyPrimary = true, upgradeLegacyGuest = false)) {
            resetFailedAttempts()
            setGuestSessionActive(false)
            true
        } else {
            handleFailedAttempt()
            false
        }
    }

    /**
     * Проверяет гостевой PIN-код с защитой от перебора и поддержкой Fail Closed.
     */
    fun verifyGuestPin(pin: String): Boolean {
        if (prefs == null && !tryRecoverSecureStorage()) return false
        if (isBlocked()) return false
        if (!isGuestPinEnabled()) return false

        val storedGuestHash = getGuestPinHash()
        if (storedGuestHash.isEmpty()) return false

        return if (verifyHash(pin, storedGuestHash, upgradeLegacyPrimary = false, upgradeLegacyGuest = true)) {
            resetFailedAttempts()
            setGuestSessionActive(true)
            true
        } else {
            handleFailedAttempt()
            false
        }
    }

    /**
     * Проверяет введённый PIN-код (основной или гостевой) с защитой от перебора (brute-force)
     * и соблюдением принципа Fail Closed при недоступности хранилища.
     */
    fun verifyPin(pin: String): Boolean {
        if (prefs == null && !tryRecoverSecureStorage()) return false
        if (isBlocked()) return false

        val storedHash = getPinHash()
        if (storedHash.isEmpty()) return false

        // 1. Проверка основного PIN-кода
        if (verifyHash(pin, storedHash, upgradeLegacyPrimary = true, upgradeLegacyGuest = false)) {
            resetFailedAttempts()
            setGuestSessionActive(false)
            return true
        }

        // 2. Проверка гостевого PIN-кода (если он включён)
        if (isGuestPinEnabled()) {
            val storedGuestHash = getGuestPinHash()
            if (storedGuestHash.isNotEmpty() &&
                verifyHash(pin, storedGuestHash, upgradeLegacyPrimary = false, upgradeLegacyGuest = true)
            ) {
                resetFailedAttempts()
                setGuestSessionActive(true)
                return true
            }
        }

        // Неверная попытка (для основного и гостевого PIN) учитывается в едином счётчике защиты от перебора
        handleFailedAttempt()
        return false
    }

    private fun verifyHash(
        pin: String,
        storedHash: String,
        upgradeLegacyPrimary: Boolean,
        upgradeLegacyGuest: Boolean
    ): Boolean {
        if (storedHash.isEmpty() || pin.isEmpty()) return false

        // 1. Проверка современного формата PBKDF2-HMAC-SHA256
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            val parts = storedHash.removePrefix(PBKDF2_PREFIX).split("$")
            if (parts.size == 2) {
                return try {
                    val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                    val expectedHashBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val pinChars = pin.toCharArray()
                    val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
                    try {
                        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        val actualHashBytes = factory.generateSecret(spec).encoded
                        MessageDigest.isEqual(expectedHashBytes, actualHashBytes)
                    } finally {
                        spec.clearPassword()
                        pinChars.fill('\u0000')
                    }
                } catch (_: Throwable) {
                    false
                }
            }
            return false
        }

        // 2. Обратная совместимость со старыми SHA-256 хешами с автоматическим апгрейдом до PBKDF2
        val legacy = legacySha256(pin)
        val matches = MessageDigest.isEqual(
            storedHash.toByteArray(Charsets.UTF_8),
            legacy.toByteArray(Charsets.UTF_8)
        )
        if (matches) {
            if (upgradeLegacyPrimary) {
                upgradePinHashOnly(KEY_PIN_HASH, pin)
            } else if (upgradeLegacyGuest) {
                upgradePinHashOnly(KEY_GUEST_PIN_HASH, pin)
            }
        }
        return matches
    }

    private fun upgradePinHashOnly(key: String, pin: String) {
        val currentPrefs = prefs ?: return
        try {
            val salt = generateSalt()
            val newHash = hashPinWithSalt(pin, salt)
            currentPrefs.edit().putString(key, newHash).commit()
        } catch (_: Throwable) {
            // Ошибка апгрейда хеша не должна блокировать уже проверенный вход пользователя
        }
    }

    private fun getPinHash(): String {
        val currentPrefs = prefs ?: return ""
        return try {
            currentPrefs.getString(KEY_PIN_HASH, "") ?: ""
        } catch (_: Throwable) {
            storageError = true
            ""
        }
    }

    private fun getGuestPinHash(): String {
        val currentPrefs = prefs ?: return ""
        return try {
            currentPrefs.getString(KEY_GUEST_PIN_HASH, "") ?: ""
        } catch (_: Throwable) {
            storageError = true
            ""
        }
    }

    fun isBiometricEnabled(): Boolean {
        val currentPrefs = prefs ?: return false // При недоступности защищённого хранилища биометрия отключена
        return try {
            currentPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isPinEnabled()
        } catch (_: Throwable) {
            false
        }
    }

    fun setBiometricEnabled(enabled: Boolean): Boolean {
        val currentPrefs = prefs ?: return false
        if (enabled && !isPinEnabled()) return false
        return try {
            currentPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).commit()
        } catch (_: Throwable) {
            storageError = true
            false
        }
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
        val pinChars = pin.toCharArray()
        val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
            "$PBKDF2_PREFIX$saltBase64$$hashBase64"
        } finally {
            spec.clearPassword()
            pinChars.fill('\u0000')
        }
    }

    private fun legacySha256(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Защита от перебора (Brute-force protection) ---
    fun getFailedAttempts(): Int {
        val currentPrefs = prefs ?: return MAX_FREE_ATTEMPTS
        return try {
            currentPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        } catch (_: Throwable) {
            MAX_FREE_ATTEMPTS
        }
    }

    fun getBlockedUntil(): Long {
        val currentPrefs = prefs ?: return Long.MAX_VALUE
        return try {
            currentPrefs.getLong(KEY_BLOCKED_UNTIL, 0L)
        } catch (_: Throwable) {
            Long.MAX_VALUE
        }
    }

    fun isBlocked(): Boolean {
        if (prefs == null) return true // Fail Closed
        return clockMillis() < getBlockedUntil()
    }

    fun getRemainingBlockTimeSeconds(): Long {
        if (prefs == null) return 0L
        val remainingMs = getBlockedUntil() - clockMillis()
        return if (remainingMs > 0L) (remainingMs + 999L) / 1000L else 0L
    }

    fun handleFailedAttempt() {
        val currentPrefs = prefs ?: return
        try {
            val currentAttempts = getFailedAttempts() + 1
            val editor = currentPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentAttempts)

            if (currentAttempts >= MAX_FREE_ATTEMPTS) {
                val delayMs = when (currentAttempts) {
                    5 -> 30_000L      // 30 секунд
                    6 -> 60_000L      // 1 минута
                    else -> 300_000L  // 5 минут
                }
                editor.putLong(KEY_BLOCKED_UNTIL, clockMillis() + delayMs)
            }
            editor.commit()
        } catch (_: Throwable) {
            storageError = true
        }
    }

    fun resetFailedAttempts() {
        val currentPrefs = prefs ?: return
        try {
            currentPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .commit()
        } catch (_: Throwable) {
            storageError = true
        }
    }

    companion object {
        internal const val LEGACY_PREFS_NAME = "app_security_prefs"
        internal const val LEGACY_FALLBACK_PREFS_NAME = "app_security_prefs_secure_fallback"
        internal const val SECURE_PREFS_NAME = "app_security_prefs_secure"

        internal const val KEY_PIN_ENABLED = "key_pin_enabled"
        internal const val KEY_PIN_HASH = "key_pin_hash"
        internal const val KEY_GUEST_PIN_ENABLED = "key_guest_pin_enabled"
        internal const val KEY_GUEST_PIN_HASH = "key_guest_pin_hash"
        internal const val KEY_GUEST_SESSION_ACTIVE = "key_guest_session_active"
        internal const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        internal const val KEY_FAILED_ATTEMPTS = "key_failed_attempts"
        internal const val KEY_BLOCKED_UNTIL = "key_blocked_until"

        private const val PBKDF2_PREFIX = "pbkdf2$"
        private const val PBKDF2_ITERATIONS = 12000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val MAX_FREE_ATTEMPTS = 5
        private const val MAX_INIT_ATTEMPTS = 2
    }
}
