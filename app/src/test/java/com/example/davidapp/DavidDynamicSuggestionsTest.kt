package com.example.davidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import com.example.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidDynamicSuggestionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val todayIso = "2026-10-20"

    @Before
    fun setUp() {
        DavidDynamicSuggestionsEngine.clearCacheForTesting()
    }

    @Test
    fun `permanent chips preserve all 5 mandatory actions and reflect API key state`() {
        val withoutKey = DavidDynamicSuggestionsEngine.getPermanentChips(hasApiKey = false)
        assertEquals(5, withoutKey.size)
        assertEquals(
            listOf("Анализ за день", "Анализ за неделю", "Анализ за месяц", "Анализ за год", "Активировать API"),
            withoutKey.map { it.label }
        )

        val withKey = DavidDynamicSuggestionsEngine.getPermanentChips(hasApiKey = true)
        assertEquals(
            listOf("Анализ за день", "Анализ за неделю", "Анализ за месяц", "Анализ за год", "Сменить API"),
            withKey.map { it.label }
        )
    }

    @Test
    fun `empty financial state never invents non-existent goals, debts, or budgets`() {
        val suggestions = DavidDynamicSuggestionsEngine.getDynamicSuggestions(
            budgetId = "empty_budget",
            todayIso = todayIso,
            transactions = emptyList(),
            categories = emptyList(),
            goals = emptyList(),
            accounts = emptyList()
        )
        assertTrue("Expected no hallucinated dynamic suggestions on empty database", suggestions.isEmpty())
    }

    @Test
    fun `generates accurate context-aware chips from real limits, growth, debts, goals, and habits`() {
        val categories = listOf(
            CategoryEntity(id = "c_food", budgetId = "b1", type = "expense", name = "Рестораны", monthlyLimit = 10_000.0)
        )
        val goals = listOf(
            GoalEntity(id = "g_mac", budgetId = "b1", name = "MacBook M4", targetAmount = 200_000.0, currentAmount = 120_000.0)
        )
        val accounts = listOf(
            AccountEntity(id = "d_card", budgetId = "b1", name = "Кредитка Т-Банк", type = "we_owe", balance = 45_000.0)
        )
        val transactions = listOf(
            // Previous month (2026-09): 8 000 ₽
            TransactionEntity(id = "tx_prev", budgetId = "b1", type = "expense", date = "2026-09-12", category = "Продукты", subcategory = "Супермаркет", amount = 8_000.0),
            // Current month (2026-10): 14 000 ₽ in Рестораны (exceeds 10 000 ₽ limit and >15% MoM growth)
            TransactionEntity(id = "tx_1", budgetId = "b1", type = "expense", date = "2026-10-03", category = "Рестораны", subcategory = "Кофе", amount = 4_000.0),
            TransactionEntity(id = "tx_2", budgetId = "b1", type = "expense", date = "2026-10-07", category = "Рестораны", subcategory = "Кофе", amount = 5_000.0),
            TransactionEntity(id = "tx_3", budgetId = "b1", type = "expense", date = "2026-10-15", category = "Рестораны", subcategory = "Кофе", amount = 5_000.0),
            TransactionEntity(id = "tx_inc", budgetId = "b1", type = "income", date = "2026-10-01", category = "Зарплата", subcategory = "Оклад", amount = 120_000.0)
        )

        val suggestions = DavidDynamicSuggestionsEngine.getDynamicSuggestions(
            budgetId = "b1",
            todayIso = todayIso,
            transactions = transactions,
            categories = categories,
            goals = goals,
            accounts = accounts,
            maxCount = 5
        )

        assertEquals(5, suggestions.size)
        val topics = suggestions.map { it.topic }.toSet()
        assertTrue(topics.contains(DynamicSuggestionTopic.LIMIT_ALERT))
        assertTrue(topics.contains(DynamicSuggestionTopic.EXPENSE_GROWTH))
        assertTrue(topics.contains(DynamicSuggestionTopic.DEBT_STRATEGY))
        assertTrue(topics.contains(DynamicSuggestionTopic.GOAL_ACCELERATION))
        assertTrue(topics.contains(DynamicSuggestionTopic.TOP_CATEGORY_SAVE))

        // Verify no duplicate labels or topicKeys
        assertEquals(suggestions.size, suggestions.map { it.topicKey }.distinct().size)
        assertEquals(suggestions.size, suggestions.map { it.chipLabel }.distinct().size)
    }

    @Test
    fun `caching returns identical list reference when data fingerprint has not changed`() {
        val txs = listOf(
            TransactionEntity(id = "t1", budgetId = "b1", type = "expense", date = todayIso, category = "Такси", subcategory = "Яндекс", amount = 1_500.0)
        )
        val firstCall = DavidDynamicSuggestionsEngine.getDynamicSuggestions(
            budgetId = "b1",
            todayIso = todayIso,
            transactions = txs,
            categories = emptyList(),
            goals = emptyList(),
            accounts = emptyList()
        )
        val secondCall = DavidDynamicSuggestionsEngine.getDynamicSuggestions(
            budgetId = "b1",
            todayIso = todayIso,
            transactions = txs,
            categories = emptyList(),
            goals = emptyList(),
            accounts = emptyList()
        )
        assertSame("Expected cached suggestions list instance on unchanged data", firstCall, secondCall)

        val updatedTxs = txs + TransactionEntity(
            id = "t2",
            budgetId = "b1",
            type = "income",
            date = todayIso,
            category = "Зарплата",
            subcategory = "",
            amount = 90_000.0
        )
        val thirdCall = DavidDynamicSuggestionsEngine.getDynamicSuggestions(
            budgetId = "b1",
            todayIso = todayIso,
            transactions = updatedTxs,
            categories = emptyList(),
            goals = emptyList(),
            accounts = emptyList()
        )
        assertNotSame("Expected recomputed suggestions list when transactions change", firstCall, thirdCall)
    }

    @Test
    fun `DavidSmartActionStrip renders permanent and dynamic chips and triggers callbacks`() {
        val sampleDynamic = DynamicChatSuggestion(
            id = "goal_1",
            topic = DynamicSuggestionTopic.GOAL_ACCELERATION,
            topicKey = "goal_1",
            priority = 90,
            chipLabel = "🎯 Как быстрее накопить на «Отпуск»?",
            userQuestion = "Как мне быстрее накопить на цель «Отпуск»?",
            geminiContextFacts = "Цель Отпуск",
            localDetailedAnswer = "Откладывай по 15%"
        )

        var selectedPeriodArg: String? = null
        var clickedDynamic: DynamicChatSuggestion? = null
        var apiDialogOpened = false

        composeTestRule.setContent {
            BudgetTheme {
                DavidSmartActionStrip(
                    stage = DavidStage.INITIAL,
                    hasApiKey = false,
                    isSelectingPeriod = false,
                    onTogglePeriodSelector = {},
                    onPeriodSelect = { selectedPeriodArg = it },
                    onSendPrompt = {},
                    onActionClick = {},
                    onExportPdf = {},
                    onOpenApiKeyDialog = { apiDialogOpened = true },
                    dynamicSuggestions = listOf(sampleDynamic),
                    onSelectDynamicSuggestion = { clickedDynamic = it }
                )
            }
        }

        // Dynamic chip and visual divider are displayed
        composeTestRule.onNodeWithTag("dynamic_chip_goal_1").assertIsDisplayed().performClick()
        assertEquals(sampleDynamic, clickedDynamic)
        composeTestRule.onNodeWithTag("chips_section_divider").assertExists()

        // All 5 permanent chips exist and work
        composeTestRule.onNodeWithTag("perm_chip_day").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("День", selectedPeriodArg)

        composeTestRule.onNodeWithTag("perm_chip_week").performScrollTo().performClick()
        assertEquals("Неделя", selectedPeriodArg)

        composeTestRule.onNodeWithTag("perm_chip_month").performScrollTo().performClick()
        assertEquals("Месяц", selectedPeriodArg)

        composeTestRule.onNodeWithTag("perm_chip_year").performScrollTo().performClick()
        assertEquals("Год", selectedPeriodArg)

        composeTestRule.onNodeWithTag("perm_chip_api").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Активировать API").assertExists()
        composeTestRule.onNodeWithTag("perm_chip_api").performClick()
        assertFalse(!apiDialogOpened)
    }
}
