package com.example.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.data.db.AppDatabase
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetActions {
    const val EXTRA_WIDGET_ACTION = "extra_david_widget_action"
    const val EXTRA_WIDGET_GOAL_ID = "extra_david_widget_goal_id"

    const val ACTION_OPEN_TODAY = "action_open_today"
    const val ACTION_ADD_EXPENSE = "action_add_expense"
    const val ACTION_ADD_INCOME = "action_add_income"
    const val ACTION_VOICE_INPUT = "action_voice_input"
    const val ACTION_OPEN_BUDGET = "action_open_budget"
    const val ACTION_OPEN_GOALS = "action_open_goals"
    const val ACTION_OPEN_ANALYTICS = "action_open_analytics"
    const val ACTION_OPEN_DAVID_CHAT = "action_open_david_chat"
}

data class RawWidgetFinancialSnapshot(
    val activeBudgetId: String,
    val todayIso: String,
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val goals: List<GoalEntity>,
    val explicitMonthlyLimit: Double?
)

object WidgetPreferencesStore {
    private const val PREFS_NAME = "david_widgets_prefs"
    private const val KEY_GLOBAL_PRIVACY = "global_widget_privacy"
    private const val PREFIX_PRIVACY = "widget_privacy_"
    private const val PREFIX_GOAL_ID = "widget_goal_id_"
    private const val PREFIX_ANALYTICS_PERIOD = "widget_analytics_period_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPrivacyMode(context: Context, appWidgetId: Int): Boolean {
        val p = prefs(context)
        val instanceKey = "$PREFIX_PRIVACY$appWidgetId"
        return if (p.contains(instanceKey)) {
            p.getBoolean(instanceKey, false)
        } else {
            p.getBoolean(KEY_GLOBAL_PRIVACY, false)
        }
    }

    fun setPrivacyMode(context: Context, appWidgetId: Int, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean("$PREFIX_PRIVACY$appWidgetId", enabled)
            .apply()
    }

    fun togglePrivacyMode(context: Context, appWidgetId: Int): Boolean {
        val next = !isPrivacyMode(context, appWidgetId)
        setPrivacyMode(context, appWidgetId, next)
        return next
    }

    fun setGlobalPrivacyMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_PRIVACY, enabled).apply()
    }

    fun getSelectedGoalId(context: Context, appWidgetId: Int): String? {
        return prefs(context).getString("$PREFIX_GOAL_ID$appWidgetId", null)
    }

    fun setSelectedGoalId(context: Context, appWidgetId: Int, goalId: String?) {
        val editor = prefs(context).edit()
        if (goalId.isNullOrBlank()) {
            editor.remove("$PREFIX_GOAL_ID$appWidgetId")
        } else {
            editor.putString("$PREFIX_GOAL_ID$appWidgetId", goalId)
        }
        editor.apply()
    }

    fun getAnalyticsPeriod(context: Context, appWidgetId: Int): MiniAnalyticsPeriod {
        val code = prefs(context).getString("$PREFIX_ANALYTICS_PERIOD$appWidgetId", null)
        return MiniAnalyticsPeriod.fromCode(code)
    }

    fun setAnalyticsPeriod(context: Context, appWidgetId: Int, period: MiniAnalyticsPeriod) {
        prefs(context).edit()
            .putString("$PREFIX_ANALYTICS_PERIOD$appWidgetId", period.code)
            .apply()
    }

    fun clearWidgetInstance(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("$PREFIX_PRIVACY$appWidgetId")
            .remove("$PREFIX_GOAL_ID$appWidgetId")
            .remove("$PREFIX_ANALYTICS_PERIOD$appWidgetId")
            .apply()
    }
}

object WidgetDataLoader {

    suspend fun loadSnapshot(context: Context): RawWidgetFinancialSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val budgetPrefs = appContext.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val db = AppDatabase.getDatabase(appContext)
        val profiles = try {
            db.budgetProfileDao().getAllProfiles().first()
        } catch (_: Exception) {
            emptyList()
        }

        val savedBudgetId = budgetPrefs.getString("last_selected_budget_id", null)
        val activeBudgetId = when {
            !savedBudgetId.isNullOrBlank() && (profiles.isEmpty() || profiles.any { it.id == savedBudgetId }) -> savedBudgetId
            profiles.isNotEmpty() -> profiles.first().id
            else -> "default"
        }

        val transactions = try {
            db.transactionDao().getTransactionsByBudgetId(activeBudgetId).first()
        } catch (_: Exception) {
            emptyList()
        }

        val categories = try {
            db.categoryDao().getCategoriesByBudgetId(activeBudgetId).first()
        } catch (_: Exception) {
            emptyList()
        }

        val goals = try {
            db.goalDao().getGoalsByBudgetId(activeBudgetId).first()
        } catch (_: Exception) {
            emptyList()
        }

        val explicitLimit = budgetPrefs.getFloat("monthly_budget_limit_$activeBudgetId", 0f).toDouble()
            .takeIf { it > 0.0 }

        RawWidgetFinancialSnapshot(
            activeBudgetId = activeBudgetId,
            todayIso = todayIso,
            transactions = transactions,
            categories = categories,
            goals = goals,
            explicitMonthlyLimit = explicitLimit
        )
    }
}

object DavidWidgetsUpdater {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        widgetScope.launch {
            updateAllSuspend(appContext)
        }
    }

    suspend fun updateAllSuspend(context: Context) {
        val appContext = context.applicationContext
        try {
            DavidTodayWidget().updateAll(appContext)
            QuickOperationWidget().updateAll(appContext)
            MyBudgetWidget().updateAll(appContext)
            FinancialGoalWidget().updateAll(appContext)
            MiniAnalyticsWidget().updateAll(appContext)
            ZhabometerWidget().updateAll(appContext)
        } catch (_: Exception) {
            // Safe no-op in unit/headless environments without AppWidgetManager service
        }
    }
}
