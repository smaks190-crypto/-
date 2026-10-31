package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.example.data.api.GeminiModelConfig
import com.example.utils.GlobalConsoleLogger

/**
 * Единое защищённое хранилище API-ключа Gemini (Issue #69, Этап 2).
 * Изолирует инициализацию EncryptedSharedPreferences и атомарную миграцию из legacy budget_prefs.
 */
object ApiKeyStorage {
    const val SECURE_PREFS_NAME = "secure_prefs"
    const val LEGACY_PREFS_NAME = "budget_prefs"
    const val KEY_GEMINI_API_KEY = "gemini_api_key"

    private fun defaultEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    internal fun createSecurePrefsOrNull(
        context: Context,
        encryptedCreator: (Context) -> SharedPreferences = ::defaultEncryptedPrefs
    ): SharedPreferences? {
        return try {
            encryptedCreator(context)
        } catch (t: Throwable) {
            GlobalConsoleLogger.e(
                "SECURITY",
                "Failed to initialize EncryptedSharedPreferences ($SECURE_PREFS_NAME); refusing plaintext fallback",
                t
            )
            null
        }
    }

    internal fun resolveAndMigrateApiKey(
        securePrefs: SharedPreferences?,
        legacyPrefs: SharedPreferences
    ): String {
        val fromSecure = try {
            GeminiModelConfig.sanitizeApiKey(securePrefs?.getString(KEY_GEMINI_API_KEY, ""))
        } catch (_: Throwable) {
            ""
        }
        if (fromSecure.isNotBlank()) {
            if (legacyPrefs.contains(KEY_GEMINI_API_KEY)) {
                legacyPrefs.edit().remove(KEY_GEMINI_API_KEY).apply()
            }
            return fromSecure
        }

        val sanitizedLegacy = GeminiModelConfig.sanitizeApiKey(
            legacyPrefs.getString(KEY_GEMINI_API_KEY, null)
        )
        if (sanitizedLegacy.isNotBlank()) {
            if (securePrefs != null) {
                try {
                    val committed = securePrefs.edit()
                        .putString(KEY_GEMINI_API_KEY, sanitizedLegacy)
                        .commit()
                    val readBack = GeminiModelConfig.sanitizeApiKey(
                        securePrefs.getString(KEY_GEMINI_API_KEY, "")
                    )
                    if (committed && readBack == sanitizedLegacy) {
                        legacyPrefs.edit().remove(KEY_GEMINI_API_KEY).apply()
                    }
                } catch (_: Throwable) {
                    // Preserve legacy key until encrypted storage recovers
                }
            }
            return sanitizedLegacy
        }

        return ""
    }

    internal fun saveApiKeyWithPrefs(
        rawKey: String,
        securePrefs: SharedPreferences?,
        legacyPrefs: SharedPreferences
    ): Boolean {
        val sanitized = GeminiModelConfig.sanitizeApiKey(rawKey)
        if (securePrefs == null) {
            return false
        }
        return try {
            val committed = securePrefs.edit()
                .putString(KEY_GEMINI_API_KEY, sanitized)
                .commit()
            val readBack = GeminiModelConfig.sanitizeApiKey(
                securePrefs.getString(KEY_GEMINI_API_KEY, "")
            )
            if (committed && readBack == sanitized) {
                if (legacyPrefs.contains(KEY_GEMINI_API_KEY)) {
                    legacyPrefs.edit().remove(KEY_GEMINI_API_KEY).apply()
                }
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getSavedApiKey(context: Context, explicitKey: String = ""): String {
        val fromExplicit = GeminiModelConfig.sanitizeApiKey(explicitKey)
        if (fromExplicit.isNotBlank()) return fromExplicit

        val appContext = context.applicationContext ?: context
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val securePrefs = createSecurePrefsOrNull(appContext)
        val resolved = resolveAndMigrateApiKey(securePrefs, legacyPrefs)
        if (resolved.isNotBlank()) return resolved

        val defaultKey = GeminiModelConfig.sanitizeApiKey(BuildConfig.GEMINI_API_KEY)
        if (GeminiModelConfig.isValidGeminiApiKey(defaultKey)) {
            return defaultKey
        }
        return ""
    }

    fun saveApiKey(context: Context, rawKey: String): Boolean {
        val appContext = context.applicationContext ?: context
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val securePrefs = createSecurePrefsOrNull(appContext)
        return saveApiKeyWithPrefs(rawKey, securePrefs, legacyPrefs)
    }
}
