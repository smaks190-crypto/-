package com.example.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidWidgetStateCalculatorTest {

    private val todayIso = "2026-10-15"

    @Test
    fun `today widget handles empty transactions gracefully with ZEN mood`() {
        val state = DavidWidgetStateCalculator.computeTodayState(
            transactions = emptyList(),
            categories = emptyList(),
            todayIso = todayIso,
            explicitMonthlyLimit = null,
            isPrivacyMode = false
        )
        assertFalse(state.hasAnyTransactions)
        assertEquals(0.0, state.todayExpense, 0.01)
        assertNull(state.dailyBudgetLimit)
        assertEquals(ZhabometerMood.ZEN, state.mood)
        assertTrue(state.davidComment.contains("Добавь первую операцию"))
    }

    @Test
    fun `today widget detects daily budget exceeded and masks currency in privacy mode`() {
        val categories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Еда", monthlyLimit = 31_000.0)
        )
        val txs = listOf(
            TransactionEntity(
                id = "t1",
                budgetId = "b1",
                type = "expense",
                date = todayIso,
                category = "Еда",
                subcategory = "Ресторан",
                amount = 5_000.0
            )
        )
        val state = DavidWidgetStateCalculator.computeTodayState(
            transactions = txs,
            categories = categories,
            todayIso = todayIso,
            isPrivacyMode = true
        )
        assertTrue(state.isDailyBudgetExceeded)
        assertEquals(ZhabometerMood.ALARMED, state.mood)
        assertTrue(state.davidComment.contains("••••"))
        assertEquals("-••••\u00A0₽", DavidWidgetStateCalculator.formatWidgetCurrency(state.todayExpense, true, "-"))
    }

    @Test
    fun `my budget widget calculates spent, remaining, and over-budget state accurately`() {
        val categories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Продукты", monthlyLimit = 20_000.0),
            CategoryEntity(id = "c2", budgetId = "b1", type = "expense", name = "Такси", monthlyLimit = 10_000.0)
        )
        val txsWithin = listOf(
            TransactionEntity(id = "t1", budgetId = "b1", type = "expense", date = "2026-10-03", category = "Продукты", subcategory = "", amount = 12_000.0)
        )
        val withinState = DavidWidgetStateCalculator.computeBudgetState(
            transactions = txsWithin,
            categories = categories,
            todayIso = todayIso
        )
        assertTrue(withinState.isBudgetConfigured)
        assertEquals(30_000.0, withinState.monthlyBudgetLimit, 0.01)
        assertEquals(12_000.0, withinState.monthSpent, 0.01)
        assertEquals(18_000.0, withinState.remainingBudget, 0.01)
        assertFalse(withinState.isExceeded)

        val txsOver = txsWithin + TransactionEntity(
            id = "t2",
            budgetId = "b1",
            type = "expense",
            date = "2026-10-14",
            category = "Такси",
            subcategory = "",
            amount = 25_000.0
        )
        val overState = DavidWidgetStateCalculator.computeBudgetState(
            transactions = txsOver,
            categories = categories,
            todayIso = todayIso
        )
        assertTrue(overState.isExceeded)
        assertEquals(7_000.0, overState.overBudgetAmount, 0.01)
        assertTrue(overState.statusText.contains("Лимит превышен"))
    }

    @Test
    fun `financial goal widget handles selected goal, deleted goal fallback, and empty goals`() {
        val emptyState = DavidWidgetStateCalculator.computeGoalState(
            goals = emptyList(),
            selectedGoalId = "missing_id"
        )
        assertFalse(emptyState.isGoalFound)
        assertEquals(0, emptyState.totalGoalsCount)

        val goals = listOf(
            GoalEntity(id = "g1", budgetId = "b1", name = "MacBook Pro", targetAmount = 200_000.0, currentAmount = 50_000.0),
            GoalEntity(id = "g2", budgetId = "b1", name = "Отпуск в Японии", targetAmount = 300_000.0, currentAmount = 240_000.0)
        )

        // Exact selected goal
        val g1State = DavidWidgetStateCalculator.computeGoalState(goals = goals, selectedGoalId = "g1")
        assertTrue(g1State.isGoalFound)
        assertFalse(g1State.isGoalDeletedOrMissing)
        assertEquals("MacBook Pro", g1State.goalTitle)
        assertEquals(25, g1State.progressPercent)
        assertEquals(150_000.0, g1State.remainingAmount, 0.01)

        // Fallback when selected goal was deleted
        val deletedFallbackState = DavidWidgetStateCalculator.computeGoalState(goals = goals, selectedGoalId = "deleted_goal_99")
        assertTrue(deletedFallbackState.isGoalFound)
        assertTrue(deletedFallbackState.isGoalDeletedOrMissing)
        assertEquals("Отпуск в Японии", deletedFallbackState.goalTitle)
        assertEquals(80, deletedFallbackState.progressPercent)
    }

    @Test
    fun `mini analytics widget computes week, month, and year series and delta percent`() {
        val txs = listOf(
            // Previous month (2026-09)
            TransactionEntity(id = "p1", budgetId = "b1", type = "expense", date = "2026-09-10", category = "Еда", subcategory = "", amount = 10_000.0),
            // Current month (2026-10)
            TransactionEntity(id = "c1", budgetId = "b1", type = "expense", date = "2026-10-02", category = "Еда", subcategory = "", amount = 5_000.0),
            TransactionEntity(id = "c2", budgetId = "b1", type = "expense", date = "2026-10-14", category = "Такси", subcategory = "", amount = 10_000.0),
            TransactionEntity(id = "c3", budgetId = "b1", type = "income", date = "2026-10-05", category = "Зарплата", subcategory = "", amount = 80_000.0)
        )

        val monthAnalytics = DavidWidgetStateCalculator.computeMiniAnalyticsState(
            transactions = txs,
            period = MiniAnalyticsPeriod.MONTH,
            todayIso = todayIso
        )
        assertEquals(15_000.0, monthAnalytics.totalExpense, 0.01)
        assertEquals(80_000.0, monthAnalytics.totalIncome, 0.01)
        assertEquals(50, monthAnalytics.expenseDeltaPercent)
        assertEquals(6, monthAnalytics.chartExpensePoints.size)

        val yearAnalytics = DavidWidgetStateCalculator.computeMiniAnalyticsState(
            transactions = txs,
            period = MiniAnalyticsPeriod.YEAR,
            todayIso = todayIso
        )
        assertEquals(25_000.0, yearAnalytics.totalExpense, 0.01)
        assertEquals(12, yearAnalytics.chartExpensePoints.size)
    }

    @Test
    fun `zhabometer evaluates PROSPEROUS vs ALARMED moods from real financial metrics`() {
        val categories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Еда", monthlyLimit = 50_000.0)
        )
        val goals = listOf(
            GoalEntity(id = "g1", budgetId = "b1", name = "Подушка", targetAmount = 100_000.0, currentAmount = 85_000.0)
        )
        val prosperousTxs = listOf(
            TransactionEntity(id = "t1", budgetId = "b1", type = "income", date = "2026-10-01", category = "Зарплата", subcategory = "", amount = 120_000.0),
            TransactionEntity(id = "t2", budgetId = "b1", type = "expense", date = "2026-10-10", category = "Еда", subcategory = "", amount = 8_000.0)
        )
        val prosperousState = DavidWidgetStateCalculator.computeZhabometerState(
            transactions = prosperousTxs,
            categories = categories,
            goals = goals,
            todayIso = todayIso
        )
        assertEquals(ZhabometerMood.PROSPEROUS, prosperousState.mood)
        assertTrue(prosperousState.score >= 84)

        val alarmedTxs = listOf(
            TransactionEntity(id = "t3", budgetId = "b1", type = "expense", date = "2026-10-12", category = "Еда", subcategory = "", amount = 65_000.0)
        )
        val alarmedState = DavidWidgetStateCalculator.computeZhabometerState(
            transactions = alarmedTxs,
            categories = categories,
            goals = emptyList(),
            todayIso = todayIso
        )
        assertEquals(ZhabometerMood.ALARMED, alarmedState.mood)
    }

    @Test
    fun `per-widget preferences isolate settings by appWidgetId and render bitmaps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WidgetPreferencesStore.setSelectedGoalId(context, 101, "goal_alpha")
        WidgetPreferencesStore.setSelectedGoalId(context, 202, "goal_beta")
        WidgetPreferencesStore.setAnalyticsPeriod(context, 101, MiniAnalyticsPeriod.YEAR)
        WidgetPreferencesStore.setAnalyticsPeriod(context, 202, MiniAnalyticsPeriod.WEEK)
        WidgetPreferencesStore.setPrivacyMode(context, 101, true)
        WidgetPreferencesStore.setPrivacyMode(context, 202, false)

        assertEquals("goal_alpha", WidgetPreferencesStore.getSelectedGoalId(context, 101))
        assertEquals("goal_beta", WidgetPreferencesStore.getSelectedGoalId(context, 202))
        assertEquals(MiniAnalyticsPeriod.YEAR, WidgetPreferencesStore.getAnalyticsPeriod(context, 101))
        assertEquals(MiniAnalyticsPeriod.WEEK, WidgetPreferencesStore.getAnalyticsPeriod(context, 202))
        assertTrue(WidgetPreferencesStore.isPrivacyMode(context, 101))
        assertFalse(WidgetPreferencesStore.isPrivacyMode(context, 202))

        ZhabometerMood.entries.forEach { mood ->
            val bmp = DavidFrogBitmapRenderer.renderFrogAvatarBitmap(mood, sizePx = 128)
            assertNotNull(bmp)
            assertEquals(128, bmp.width)
        }
    }
}
