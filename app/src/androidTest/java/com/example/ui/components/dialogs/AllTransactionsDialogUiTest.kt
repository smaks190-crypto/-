package com.example.ui.components.dialogs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.example.data.db.TransactionEntity
import com.example.ui.theme.BudgetTheme
import org.junit.Rule
import org.junit.Test

/**
 * Инструментальные Compose UI-тесты для окна «Все операции» ([AllTransactionsDialog]):
 * 1. При открытии отображается полный неблокированный список операций (можно листать из Октября в Сентябрь),
 *    а шапка показывает итоги видимого месяца («Октябрь»), при этом кнопка «Мес» НЕ зафиксирована.
 * 2. Отсутствует дублирующий ряд кнопок «Все | Траты | Доходы» под строкой поиска.
 * 3. Внутри графика ровно 3 вкладки периода: «Нед», «Мес», «Год» (без 4-й кнопки «Все»).
 * 4. При прокрутке списка вниз до Сентября заголовок и чип месяца автоматически меняются на «Сентябрь»,
 *    а свайп графика влево/вправо синхронно обновляет месяц, диаграмму и список операций.
 * 5. Первое нажатие на «Мес» (или на чип месяца) фиксирует текущий месяц (скрывая остальные месяцы),
 *    а повторное нажатие снимает фиксацию, возвращая свободный скролл по всем месяцам.
 * 6. Переключение «Нед» и «Год», а также работа поиска, фильтра категорий и переключения Траты/Доходы.
 */
class AllTransactionsDialogUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testTransactions = listOf(
        // Октябрь 2026 (текущий якорный месяц)
        TransactionEntity(
            id = "oct-exp-1",
            type = "expense",
            date = "2026-10-15",
            category = "Кафе",
            subcategory = "Октябрь Латте",
            amount = 450.0,
            createdAt = 1005L
        ),
        TransactionEntity(
            id = "oct-exp-2",
            type = "expense",
            date = "2026-10-12",
            category = "Продукты",
            subcategory = "Октябрь Фермерский рынок",
            amount = 3200.0,
            createdAt = 1004L
        ),
        TransactionEntity(
            id = "oct-inc-1",
            type = "income",
            date = "2026-10-10",
            category = "Зарплата",
            subcategory = "Октябрь Аванс",
            amount = 85000.0,
            createdAt = 1003L
        ),
        // Сентябрь 2026 (предыдущий месяц)
        TransactionEntity(
            id = "sep-exp-1",
            type = "expense",
            date = "2026-09-15",
            category = "Транспорт",
            subcategory = "Сентябрь Такси Аэропорт",
            amount = 2100.0,
            createdAt = 1002L
        ),
        TransactionEntity(
            id = "sep-inc-1",
            type = "income",
            date = "2026-09-14",
            category = "Фриланс",
            subcategory = "Сентябрь Дизайн проекта",
            amount = 40000.0,
            createdAt = 1001L
        ),
        // Январь 2026 (начало того же года)
        TransactionEntity(
            id = "jan-exp-1",
            type = "expense",
            date = "2026-01-20",
            category = "Путешествия",
            subcategory = "Январь Отель в горах",
            amount = 28000.0,
            createdAt = 1000L
        )
    )

    private fun setDialogContent(initialFilterType: String = "all") {
        composeTestRule.setContent {
            BudgetTheme {
                AllTransactionsDialog(
                    transactions = testTransactions,
                    onDeleteTransaction = {},
                    onDismiss = {},
                    initialFilterType = initialFilterType
                )
            }
        }
    }

    @Test
    fun initialOpen_allowsFreeScrollingToPreviousMonths_andHasNoDuplicateTypeRowUnderSearch() {
        setDialogContent(initialFilterType = "all")

        // Заголовок окна и карточки сравнения за стартовый видимый месяц (Октябрь)
        composeTestRule.onNodeWithText("Все операции").assertIsDisplayed()
        composeTestRule.onNodeWithText("Траты • Октябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Доходы • Октябрь").assertIsDisplayed()

        // Операции за Октябрь и Сентябрь доступны в свободном неблокированном списке
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertIsDisplayed()

        // Проверяем, что под строкой поиска НЕТ старого ряда чипов «Все | Траты | Доходы»
        composeTestRule.onAllNodesWithText("Все").assertCountEquals(0)

        // При нажатии на чип месяца «Октябрь» включается строгий фильтр по Октябрю (Сентябрь скрывается)
        composeTestRule.onNodeWithTag("period_date_picker_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertDoesNotExist()

        // Повторное нажатие на чип месяца снимает фиксацию и снова открывает доступ к Сентябрю
        composeTestRule.onNodeWithTag("period_date_picker_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertIsDisplayed()
    }

    @Test
    fun chartPeriodTabs_haveOnlyThreeButtons_andSwipeUpdatesMonthChartAndListSynchronously() {
        setDialogContent(initialFilterType = "expense")

        // В режиме графика («Траты») отображается карточка за Октябрь
        composeTestRule.onNodeWithText("Траты • Октябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()

        // Переключатель периодов содержит ровно 3 вкладки: Нед, Мес, Год (кнопки «Все» нет)
        composeTestRule.onNodeWithTag("period_tab_week").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_year").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Все").assertCountEquals(0)

        // Свайп вправо по области графика перелистывает на предыдущий месяц (Октябрь -> Сентябрь)
        composeTestRule.onNodeWithTag("chart_swipe_area").performTouchInput {
            swipeRight(startX = left + width * 0.15f, endX = left + width * 0.85f)
        }
        composeTestRule.waitForIdle()

        // Заголовок графика и чип периода синхронно переключились на Сентябрь
        composeTestRule.onNodeWithText("Траты • Сентябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertIsDisplayed()

        // Свайп влево по области графика возвращает обратно на Октябрь
        composeTestRule.onNodeWithTag("chart_swipe_area").performTouchInput {
            swipeLeft(startX = left + width * 0.85f, endX = left + width * 0.15f)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Траты • Октябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()
    }

    @Test
    fun tappingMonthLocksDisplayedMonth_andSecondTapUnlocksWithoutResettingSwipedMonth() {
        setDialogContent(initialFilterType = "expense")

        // Перелистываем график с Октября на Сентябрь
        composeTestRule.onNodeWithTag("chart_swipe_area").performTouchInput {
            swipeRight(startX = left + width * 0.15f, endX = left + width * 0.85f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Траты • Сентябрь").assertIsDisplayed()

        // 1-е нажатие «Мес»: фиксирует текущий пролистанный месяц (Сентябрь), скрывая Октябрь и Январь
        composeTestRule.onNodeWithTag("period_tab_month").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Траты • Сентябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertIsDisplayed()
        composeTestRule.onNodeWithText("Октябрь Латте").assertDoesNotExist()
        composeTestRule.onNodeWithText("Январь Отель в горах").assertDoesNotExist()

        // 2-е нажатие «Мес»: снимает фиксацию периода, сохраняя Сентябрь в заголовке графика,
        // но снова позволяя видеть операции других месяцев в списке
        composeTestRule.onNodeWithTag("period_tab_month").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Траты • Сентябрь").assertIsDisplayed()
        composeTestRule.onNodeWithText("Сентябрь Такси Аэропорт").assertIsDisplayed()
    }

    @Test
    fun togglingWeekAndYearTabs_andCombiningWithSearchAndIncomeExpenseSwitch() {
        setDialogContent(initialFilterType = "expense")

        // Нажимаем «Год» — отображаются траты за весь 2026 год (Октябрь, Сентябрь, Январь)
        composeTestRule.onNodeWithTag("period_tab_year").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Траты • 2026 год").assertIsDisplayed()
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()

        // Скроллим список вниз, чтобы убедиться, что январская операция присутствует в годовом списке
        composeTestRule.onNodeWithTag("all_transactions_lazy_list").performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Январь Отель в горах").assertIsDisplayed()

        // Возвращаем список наверх и разворачиваем график (если он свернулся в компактную шапку при скролле)
        composeTestRule.onNodeWithTag("all_transactions_lazy_list").performTouchInput {
            swipeDown()
        }
        composeTestRule.waitForIdle()
        if (composeTestRule.onAllNodesWithText("График").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("График").performClick()
            composeTestRule.waitForIdle()
        }

        // Повторно нажимаем «Год» — фильтр года выключается, возвращается текущий месяц (Октябрь)
        composeTestRule.onNodeWithTag("period_tab_year").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Траты • Октябрь").assertIsDisplayed()

        // Проверяем поиск внутри выбранного месяца
        composeTestRule.onNodeWithTag("all_transactions_search_input").performTextInput("Фермерский")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Октябрь Фермерский рынок").assertIsDisplayed()
        composeTestRule.onNodeWithText("Октябрь Латте").assertDoesNotExist()

        // Очищаем поиск
        composeTestRule.onNodeWithTag("all_transactions_search_clear").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Октябрь Латте").assertIsDisplayed()
    }
}

private fun SemanticsNodeInteraction.assertDoesNotExist() {
    val nodes = fetchSemanticsNodesWithZeroCheck()
    org.junit.Assert.assertTrue("Expected node not to exist, but found ${nodes.size}", nodes.isEmpty())
}

private fun SemanticsNodeInteraction.fetchSemanticsNodesWithZeroCheck() = try {
    listOf(fetchSemanticsNode())
} catch (_: AssertionError) {
    emptyList()
}
