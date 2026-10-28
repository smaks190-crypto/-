package com.example.ui.components.charts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeChartDataEngineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `buildHomeChartState for MONTH synchronizes selectedMonthIdx and selectedYear with exact sums`() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                type = "expense",
                category = "Продукты",
                amount = 4_200.0,
                date = "2026-10-05",
                note = "Супермаркет"
            ),
            TransactionEntity(
                id = 2,
                type = "expense",
                category = "Рестораны",
                amount = 1_800.0,
                date = "2026-10-18",
                note = "Ужин"
            ),
            TransactionEntity(
                id = 3,
                type = "income",
                category = "Зарплата",
                amount = 120_000.0,
                date = "2026-10-10",
                note = "Аванс"
            ),
            // Чужой месяц (сентябрь) не должен попадать в октябрьский график:
            TransactionEntity(
                id = 4,
                type = "expense",
                category = "Шоппинг",
                amount = 50_000.0,
                date = "2026-09-28",
                note = "Куртка"
            )
        )

        val state = HomeChartDataEngine.buildHomeChartState(
            transactions = transactions,
            period = HomeChartPeriod.MONTH,
            selectedMonthIdx = 9, // Октябрь (0-based)
            selectedYear = 2026
        )

        assertEquals(HomeChartPeriod.MONTH, state.period)
        assertEquals("Октябрь 2026", state.periodSubtitle)
        assertEquals(31, state.points.size)
        assertEquals(6_000.0, state.totalExpense, 0.01)
        assertEquals(120_000.0, state.totalIncome, 0.01)
        assertEquals(114_000.0, state.netBalance, 0.01)
        assertTrue(state.hasExpenses)
        assertTrue(state.hasIncomes)
        assertTrue(state.hasAnyData)
        assertFalse(state.isDeficit)
        assertEquals(4, state.peakExpensePointIndex) // 5 октября -> index 4
    }

    @Test
    fun `buildHomeChartState for WEEK anchors inside selected month and handles week sums accurately`() {
        val transactions = listOf(
            // Неделя ПН 12 окт 2026 .. ВС 18 окт 2026
            TransactionEntity(
                id = 1,
                type = "expense",
                category = "Кофе",
                amount = 350.0,
                date = "2026-10-12", // ПН
                note = ""
            ),
            TransactionEntity(
                id = 2,
                type = "expense",
                category = "Такси",
                amount = 950.0,
                date = "2026-10-15", // ЧТ
                note = ""
            ),
            TransactionEntity(
                id = 3,
                type = "income",
                category = "Кэшбэк",
                amount = 500.0,
                date = "2026-10-17", // СБ
                note = ""
            ),
            // Операция на предыдущей неделе не должна попадать в эту неделю:
            TransactionEntity(
                id = 4,
                type = "expense",
                category = "Аренда",
                amount = 45_000.0,
                date = "2026-10-02",
                note = ""
            )
        )

        val state = HomeChartDataEngine.buildHomeChartState(
            transactions = transactions,
            period = HomeChartPeriod.WEEK,
            selectedMonthIdx = 9,
            selectedYear = 2026,
            referenceDateIso = "2026-10-15"
        )

        assertEquals(HomeChartPeriod.WEEK, state.period)
        assertEquals("12 окт – 18 окт 2026", state.periodSubtitle)
        assertEquals(7, state.points.size)
        assertEquals("ПН", state.points.first().shortAxisLabel)
        assertEquals("ВС", state.points.last().shortAxisLabel)
        assertEquals(1_300.0, state.totalExpense, 0.01)
        assertEquals(500.0, state.totalIncome, 0.01)
        assertTrue(state.isDeficit)
        assertEquals(3, state.peakExpensePointIndex) // Четверг (index 3)
    }

    @Test
    fun `buildHomeChartState for YEAR aggregates all 12 months for selectedYear`() {
        val transactions = listOf(
            TransactionEntity(id = 1, type = "expense", category = "Еда", amount = 25_000.0, date = "2026-01-15", note = ""),
            TransactionEntity(id = 2, type = "expense", category = "Отпуск", amount = 180_000.0, date = "2026-07-20", note = ""),
            TransactionEntity(id = 3, type = "income", category = "Зарплата", amount = 500_000.0, date = "2026-07-05", note = ""),
            // Другой год (2025) игнорируется:
            TransactionEntity(id = 4, type = "expense", category = "Ремонт", amount = 300_000.0, date = "2025-12-10", note = "")
        )

        val state = HomeChartDataEngine.buildHomeChartState(
            transactions = transactions,
            period = HomeChartPeriod.YEAR,
            selectedMonthIdx = 9,
            selectedYear = 2026
        )

        assertEquals(HomeChartPeriod.YEAR, state.period)
        assertEquals("2026 год", state.periodSubtitle)
        assertEquals(12, state.points.size)
        assertEquals(205_000.0, state.totalExpense, 0.01)
        assertEquals(500_000.0, state.totalIncome, 0.01)
        assertEquals(6, state.peakExpensePointIndex) // Июль (index 6)
    }

    @Test
    fun `buildHomeChartState handles empty period and extreme values safely`() {
        val emptyState = HomeChartDataEngine.buildHomeChartState(
            transactions = emptyList(),
            period = HomeChartPeriod.MONTH,
            selectedMonthIdx = 1, // Февраль
            selectedYear = 2026
        )

        assertFalse(emptyState.hasAnyData)
        assertEquals(28, emptyState.points.size)
        assertEquals(0.0, emptyState.totalExpense, 0.0)
        assertNull(emptyState.peakExpensePointIndex)

        // Экстремально малые и большие значения
        val extremeTransactions = listOf(
            TransactionEntity(id = 1, type = "expense", category = "Копейки", amount = 0.45, date = "2026-02-01", note = ""),
            TransactionEntity(id = 2, type = "income", category = "Инвестиции", amount = 15_500_000.0, date = "2026-02-14", note = "")
        )
        val extremeState = HomeChartDataEngine.buildHomeChartState(
            transactions = extremeTransactions,
            period = HomeChartPeriod.MONTH,
            selectedMonthIdx = 1,
            selectedYear = 2026
        )

        assertTrue(extremeState.hasAnyData)
        assertEquals(0.45, extremeState.totalExpense, 0.001)
        assertEquals(15_500_000.0, extremeState.totalIncome, 0.01)
        assertTrue(HomeChartDataEngine.formatCompactChartAmount(0.45).contains("0,45"))
        assertTrue(HomeChartDataEngine.formatCompactChartAmount(15_500_000.0).contains("15,5M"))
    }

    @Test
    fun `buildCategorySlices filters non-positive amounts and computes accurate percentages`() {
        val slices = HomeChartDataEngine.buildCategorySlices(
            mapOf(
                "Продукты" to 6_000.0,
                "Кафе" to 4_000.0,
                "Пустая" to 0.0,
                "Отрицательная" to -500.0
            )
        )

        assertEquals(2, slices.size)
        assertEquals("Продукты", slices[0].category)
        assertEquals(60, slices[0].percent)
        assertEquals(216f, slices[0].sweepAngleDeg, 0.1f)
        assertEquals("Кафе", slices[1].category)
        assertEquals(40, slices[1].percent)
    }

    @Test
    fun `ExpenseDynamicsAreaChartCard switches periods and updates totals reactively on data change`() {
        var txList by mutableStateOf(
            listOf(
                TransactionEntity(
                    id = 1,
                    type = "expense",
                    category = "Продукты",
                    amount = 5_000.0,
                    date = "2026-10-15",
                    note = ""
                ),
                TransactionEntity(
                    id = 2,
                    type = "income",
                    category = "Зарплата",
                    amount = 80_000.0,
                    date = "2026-10-01",
                    note = ""
                ),
                TransactionEntity(
                    id = 3,
                    type = "expense",
                    category = "Отпуск",
                    amount = 40_000.0,
                    date = "2026-05-10",
                    note = ""
                )
            )
        )

        composeTestRule.setContent {
            ExpenseDynamicsAreaChartCard(
                transactions = txList,
                selectedMonthIdx = 9, // Октябрь
                selectedYear = 2026,
                referenceDateIso = "2026-10-15"
            )
        }

        // По умолчанию открыт период «Месяц» (Октябрь 2026)
        composeTestRule.onNodeWithTag("home_chart_period_subtitle", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Октябрь 2026", substring = true)

        composeTestRule.onNodeWithTag("home_chart_expense_total", useUnmergedTree = true)
            .assertTextContains("5 000", substring = true)

        // Переключаем на «Год» -> должен учесть и майский расход (5 000 + 40 000 = 45 000 -> 45,0к ₽)
        composeTestRule.onNodeWithTag("home_chart_period_year").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_chart_period_subtitle", useUnmergedTree = true)
            .assertTextContains("2026 год", substring = true)
        composeTestRule.onNodeWithTag("home_chart_expense_total", useUnmergedTree = true)
            .assertTextContains("45,0к", substring = true)

        // Переключаем обратно на «Месяц» и добавляем новую операцию в октябре
        composeTestRule.onNodeWithTag("home_chart_period_month").performClick()
        composeTestRule.waitForIdle()

        txList = txList + TransactionEntity(
            id = 4,
            type = "expense",
            category = "Такси",
            amount = 2_500.0,
            date = "2026-10-16",
            note = ""
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_chart_expense_total", useUnmergedTree = true)
            .assertTextContains("7 500", substring = true)
    }
}
