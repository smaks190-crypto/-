package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityManagerTest {
    private lateinit var context: Context
    private lateinit var encrypted: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        for (name in listOf("app_security_prefs", "app_security_prefs_secure_fallback", "security_test_encrypted")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        encrypted = context.getSharedPreferences("security_test_encrypted", Context.MODE_PRIVATE)
    }

    private fun legacyPin(name: String): SharedPreferences {
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val hash = MessageDigest.getInstance("SHA-256").digest("1234".toByteArray()).joinToString("") { "%02x".format(it) }
        legacy.edit().putString("key_pin_hash", hash).putBoolean("key_pin_enabled", true).commit()
        return legacy
    }

    @Test
    fun `keystore failure keeps the app locked and preserves legacy PIN`() {
        val legacy = legacyPin("app_security_prefs")
        val manager = SecurityManager(context) { throw IllegalStateException("Keystore unavailable") }
        assertFalse(manager.isStorageAvailable)
        assertTrue(manager.isPinEnabled())
        assertFalse(manager.isBiometricEnabled())
        assertFalse(manager.verifyPin("1234"))
        assertFalse(manager.isGuestPinEnabled())
        assertTrue(legacy.contains("key_pin_hash"))
        assertTrue(context.getSharedPreferences("app_security_prefs_secure_fallback", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `failed migration commit preserves the only PIN copy and locks access`() {
        val legacy = legacyPin("app_security_prefs")
        val broken = object : SharedPreferences by encrypted {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by encrypted.edit() {
                override fun commit(): Boolean = false
            }
        }
        val manager = SecurityManager(context) { broken }
        assertFalse(manager.isStorageAvailable)
        assertTrue(manager.isPinEnabled())
        assertTrue(legacy.contains("key_pin_hash"))
    }

    @Test
    fun `legacy plaintext fallback migrates only after encrypted write is confirmed`() {
        val fallback = legacyPin("app_security_prefs_secure_fallback")
        val manager = SecurityManager(context) { encrypted }
        assertTrue(manager.isStorageAvailable)
        assertTrue(manager.isPinEnabled())
        assertTrue(fallback.all.isEmpty())
        assertTrue(manager.verifyPin("1234"))
        assertFalse(manager.verifyPin("0000"))
        assertTrue(encrypted.getString("key_pin_hash", "")!!.startsWith("pbkdf2$"))
    }

    @Test
    fun `normal storage supports primary and guest PINs without a plaintext fallback`() {
        val manager = SecurityManager(context) { encrypted }
        assertFalse(manager.isPinEnabled())
        manager.setPin("1234")
        manager.setGuestPin("9876")
        assertTrue(manager.verifyPin("9876"))
        assertTrue(manager.isGuestSessionActive())
        assertTrue(manager.verifyPin("1234"))
        assertFalse(manager.isGuestSessionActive())
        assertFalse(manager.verifyPin("0000"))
    }
}
