package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Централизованный репозиторий пользовательских настроек budget_prefs и app_prefs (Issue #69, Этап 2).
 * Устраняет прямые вызовы context.getSharedPreferences(...) из Composable и ViewModel.
 */
object BudgetPreferencesRepository {
    const val BUDGET_PREFS_NAME = "budget_prefs"
    const val APP_PREFS_NAME = "app_prefs"

    const val KEY_GEMINI_CONSENT = "gemini_consent_given"
    const val KEY_FIRST_LAUNCH_SELECTION = "is_first_launch_selection"
    const val KEY_LAST_SELECTED_BUDGET_ID = "last_selected_budget_id"
    const val KEY_IS_FIRST_RUN = "is_first_run"

    fun budgetPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(BUDGET_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun appPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return appContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isGeminiConsentGiven(context: Context): Boolean =
        budgetPrefs(context).getBoolean(KEY_GEMINI_CONSENT, false)

    fun setGeminiConsentGiven(context: Context, given: Boolean) {
        budgetPrefs(context).edit().putBoolean(KEY_GEMINI_CONSENT, given).apply()
    }

    fun isFirstLaunchSelection(context: Context): Boolean =
        budgetPrefs(context).getBoolean(KEY_FIRST_LAUNCH_SELECTION, true)

    fun getLastSelectedBudgetId(context: Context): String? =
        budgetPrefs(context).getString(KEY_LAST_SELECTED_BUDGET_ID, null)

    fun setSelectedBudget(context: Context, budgetId: String) {
        budgetPrefs(context).edit()
            .putString(KEY_LAST_SELECTED_BUDGET_ID, budgetId)
            .putBoolean(KEY_FIRST_LAUNCH_SELECTION, false)
            .apply()
    }

    fun resetBudgetSelectionOnClear(context: Context) {
        budgetPrefs(context).edit()
            .remove(KEY_LAST_SELECTED_BUDGET_ID)
            .putBoolean(KEY_FIRST_LAUNCH_SELECTION, true)
            .apply()
    }

    fun markSetupModeConfirmed(context: Context) {
        budgetPrefs(context).edit().putBoolean(KEY_IS_FIRST_RUN, false).apply()
    }

    fun getLastGreetingDate(context: Context, profileKey: String): String =
        budgetPrefs(context).getString("last_greeting_date_$profileKey", "") ?: ""

    fun hasWelcomedFirstTime(context: Context, profileKey: String): Boolean =
        budgetPrefs(context).getBoolean("has_welcomed_first_time_$profileKey", false)

    fun markFirstTimeWelcomed(context: Context, profileKey: String, todayDate: String) {
        budgetPrefs(context).edit()
            .putBoolean("has_welcomed_first_time_$profileKey", true)
            .putString("last_greeting_date_$profileKey", todayDate)
            .apply()
    }

    fun setLastGreetingDate(context: Context, profileKey: String, todayDate: String) {
        budgetPrefs(context).edit()
            .putString("last_greeting_date_$profileKey", todayDate)
            .apply()
    }

    fun hasOpenedDavidChatBefore(context: Context, profileName: String): Boolean =
        appPrefs(context).getBoolean("has_opened_david_chat_before_$profileName", false)

    fun markDavidChatOpened(context: Context, profileName: String) {
        appPrefs(context).edit()
            .putBoolean("has_opened_david_chat_before_$profileName", true)
            .apply()
    }

    fun hasOpenedProfileBefore(context: Context, profileKey: String): Boolean =
        budgetPrefs(context).getBoolean("has_opened_profile_$profileKey", false)

    fun markProfileOpened(context: Context, profileKey: String) {
        budgetPrefs(context).edit()
            .putBoolean("has_opened_profile_$profileKey", true)
            .apply()
    }

    fun isSoundEnabled(context: Context): Boolean {
        val aPrefs = appPrefs(context)
        val bPrefs = budgetPrefs(context)
        return aPrefs.getBoolean("sound_enabled", true) &&
            aPrefs.getBoolean("is_sound_enabled", true) &&
            bPrefs.getBoolean("sound_enabled", true) &&
            bPrefs.getBoolean("is_sound_enabled", true)
    }
}
