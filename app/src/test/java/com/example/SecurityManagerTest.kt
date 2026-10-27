package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityManagerTest {

    private lateinit var context: Context
    private lateinit var testSecureBackingPrefs: SharedPreferences
    private lateinit var oldPrefs: SharedPreferences
    private lateinit var legacyFallbackPrefs: SharedPreferences
    private var currentFakeTimeMs: Long = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testSecureBackingPrefs = context.getSharedPreferences("test_encrypted_backing", Context.MODE_PRIVATE)
        oldPrefs = context.getSharedPreferences(SecurityManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        legacyFallbackPrefs = context.getSharedPreferences(SecurityManager.LEGACY_FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)

        testSecureBackingPrefs.edit().clear().commit()
        oldPrefs.edit().clear().commit()
        legacyFallbackPrefs.edit().clear().commit()
        currentFakeTimeMs = 1_700_000_000_000L
    }

    private fun createSecurityManagerWithTestStore(): SecurityManager {
        return SecurityManager(
            context = context,
            encryptedPrefsFactory = { testSecureBackingPrefs },
            clockMillis = { currentFakeTimeMs }
        )
    }

    private fun sha256Hex(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `1_successful_migration_of_legacy_settings_and_clears_old_prefs_only_after_verification`() {
        val legacyPin = "4826"
        val legacyHash = sha256Hex(legacyPin)

        oldPrefs.edit()
            .putString(SecurityManager.KEY_PIN_HASH, legacyHash)
            .putBoolean(SecurityManager.KEY_PIN_ENABLED, true)
            .putBoolean(SecurityManager.KEY_BIOMETRIC_ENABLED, true)
            .putInt(SecurityManager.KEY_FAILED_ATTEMPTS, 2)
            .putLong(SecurityManager.KEY_BLOCKED_UNTIL, 0L)
            .commit()

        val manager = createSecurityManagerWithTestStore()

        // 1. Данные успешно перенесены в защищённое хранилище
        assertTrue(manager.isStorageAvailable())
        assertFalse(manager.isStorageError())
        assertTrue(manager.isPinEnabled())
        assertTrue(manager.isBiometricEnabled())
        assertEquals(2, manager.getFailedAttempts())

        // 2. Исходные настройки в oldPrefs очищены после подтверждённой миграции
        assertFalse(oldPrefs.contains(SecurityManager.KEY_PIN_HASH))

        // 3. Пользовательский PIN работает и прозрачно обновляется с SHA-256 до PBKDF2
        assertTrue(manager.verifyPin(legacyPin))
        assertEquals(0, manager.getFailedAttempts())
        val upgradedHash = testSecureBackingPrefs.getString(SecurityManager.KEY_PIN_HASH, "") ?: ""
        assertTrue(upgradedHash.startsWith("pbkdf2$"))
    }

    @Test
    fun `2_failed_secure_storage_init_keeps_old_prefs_intact_and_fails_closed`() {
        val existingPin = "7391"
        val legacyHash = sha256Hex(existingPin)

        oldPrefs.edit()
            .putString(SecurityManager.KEY_PIN_HASH, legacyHash)
            .putBoolean(SecurityManager.KEY_PIN_ENABLED, true)
            .commit()

        // Имитируем сбой инициализации EncryptedSharedPreferences / Android Keystore
        val brokenManager = SecurityManager(
            context = context,
            encryptedPrefsFactory = { throw GeneralSecurityException("Simulated Keystore failure") },
            clockMillis = { currentFakeTimeMs }
        )

        // 1. Хранилище помечено как недоступное, никаких записей в открытый fallback не создано
        assertFalse(brokenManager.isStorageAvailable())
        assertTrue(brokenManager.isStorageError())
        assertFalse(legacyFallbackPrefs.contains(SecurityManager.KEY_PIN_HASH))

        // 2. Fail Closed: приложение остаётся заблокированным (isPinEnabled() == true) и НЕ пускает по PIN
        assertTrue(brokenManager.isPinEnabled())
        assertTrue(brokenManager.isBlocked())
        assertFalse(brokenManager.isBiometricEnabled())
        assertFalse(brokenManager.verifyPin(existingPin))
        assertFalse(brokenManager.verifyGuestPin(existingPin))
        assertFalse(brokenManager.setPin("1234"))

        // 3. Исходные настройки в oldPrefs НЕ были удалены при сбое инициализации
        assertTrue(oldPrefs.contains(SecurityManager.KEY_PIN_HASH))
        assertEquals(legacyHash, oldPrefs.getString(SecurityManager.KEY_PIN_HASH, ""))
    }

    @Test
    fun `3_preserves_existing_pbkdf2_user_pin_across_restarts`() {
        val manager1 = createSecurityManagerWithTestStore()
        assertTrue(manager1.setPin("9042"))
        assertTrue(manager1.isPinEnabled())

        // Повторное создание SecurityManager (имитация перезапуска приложения)
        val manager2 = createSecurityManagerWithTestStore()
        assertTrue(manager2.isPinEnabled())
        assertFalse(manager2.verifyPin("0000"))
        assertTrue(manager2.verifyPin("9042"))
    }

    @Test
    fun `4_guest_pin_works_properly_and_cannot_modify_primary_pin`() {
        val manager = createSecurityManagerWithTestStore()
        assertTrue(manager.setPin("1122"))

        // Гостевой PIN не может совпадать с основным
        assertFalse(manager.setGuestPin("1122"))
        assertFalse(manager.isGuestPinEnabled())

        // Установка корректного гостевого PIN
        assertTrue(manager.setGuestPin("8899"))
        assertTrue(manager.isGuestPinEnabled())
        assertFalse(manager.isGuestSessionActive())

        // Вход по гостевому PIN активирует гостевую сессию
        assertTrue(manager.verifyPin("8899"))
        assertTrue(manager.isGuestSessionActive())

        // Гостевой PIN НЕ проходит проверку verifyPrimaryPinOnly (защита от смены основного PIN гостем)
        assertFalse(manager.verifyPrimaryPinOnly("8899"))

        // Вход по основному PIN выключает гостевую сессию
        assertTrue(manager.verifyPin("1122"))
        assertFalse(manager.isGuestSessionActive())
        assertTrue(manager.verifyPrimaryPinOnly("1122"))
    }

    @Test
    fun `5_brute_force_lockout_blocks_after_5_failed_attempts_for_primary_and_guest_pin`() {
        val manager = createSecurityManagerWithTestStore()
        assertTrue(manager.setPin("2580"))
        assertTrue(manager.setGuestPin("9900"))

        // 4 неверные попытки еще не блокируют вход
        repeat(4) { attempt ->
            assertFalse(manager.verifyPin("000$attempt"))
            assertFalse(manager.isBlocked())
        }
        assertEquals(4, manager.getFailedAttempts())

        // 5-я неверная попытка (через verifyGuestPin) вызывает блокировку на 30 секунд
        assertFalse(manager.verifyGuestPin("4444"))
        assertEquals(5, manager.getFailedAttempts())
        assertTrue(manager.isBlocked())
        assertEquals(30L, manager.getRemainingBlockTimeSeconds())

        // Во время активной блокировки даже ПРАВИЛЬНЫЙ основной и гостевой PIN отклоняются!
        assertFalse(manager.verifyPin("2580"))
        assertFalse(manager.verifyPin("9900"))
        assertFalse(manager.verifyGuestPin("9900"))
        assertFalse(manager.verifyPrimaryPinOnly("2580"))

        // Спустя 31 секунду блокировка снимается; 6-я неверная попытка блокирует уже на 60 секунд
        currentFakeTimeMs += 31_000L
        assertFalse(manager.isBlocked())
        assertFalse(manager.verifyPin("7777"))
        assertEquals(6, manager.getFailedAttempts())
        assertTrue(manager.isBlocked())
        assertEquals(60L, manager.getRemainingBlockTimeSeconds())

        // Спустя 61 секунду правильный PIN успешно проходит и сбрасывает счётчик попыток
        currentFakeTimeMs += 61_000L
        assertFalse(manager.isBlocked())
        assertTrue(manager.verifyPin("2580"))
        assertEquals(0, manager.getFailedAttempts())
        assertFalse(manager.isBlocked())
    }

    @Test
    fun `6_prevents_unauthorized_access_and_recovers_after_transient_keystore_error`() {
        var keystoreHealthy = true
        val manager = SecurityManager(
            context = context,
            encryptedPrefsFactory = {
                if (!keystoreHealthy) {
                    throw GeneralSecurityException("Keystore temporarily locked")
                }
                testSecureBackingPrefs
            },
            clockMillis = { currentFakeTimeMs }
        )

        assertTrue(manager.setPin("5566"))

        // Имитируем временный сбой Keystore при следующем запуске
        keystoreHealthy = false
        val failingManager = SecurityManager(
            context = context,
            encryptedPrefsFactory = {
                if (!keystoreHealthy) {
                    throw GeneralSecurityException("Keystore temporarily locked")
                }
                testSecureBackingPrefs
            },
            clockMillis = { currentFakeTimeMs }
        )

        // Несанкционированный доступ заблокирован (Fail Closed)
        assertTrue(failingManager.isStorageError())
        assertTrue(failingManager.isPinEnabled())
        assertFalse(failingManager.verifyPin("5566"))
        assertFalse(failingManager.verifyPin(""))

        // Когда Keystore снова становится доступен, verifyPin автоматически восстанавливает хранилище без потери PIN
        keystoreHealthy = true
        assertTrue(failingManager.verifyPin("5566"))
        assertFalse(failingManager.isStorageError())
        assertTrue(failingManager.isStorageAvailable())
    }
}
