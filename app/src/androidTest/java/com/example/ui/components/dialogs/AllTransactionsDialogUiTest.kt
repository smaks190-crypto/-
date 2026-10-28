package com.example.ui.components.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.data.db.TransactionEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AllTransactionsDialogUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val threeMonthsFixtures = listOf(
        // Октябрь 2026 (текущий месяц при открытии с initialDate = "2026-10-15")
        TransactionEntity(
            id = "oct-exp-1",
            type = "expense",
            date = "2026-10-15",
            category = "Кафе",
            subcategory = "Октябрьский Раф",
            amount = 450.0,
            createdAt = 1760520000000L
        ),
        TransactionEntity(
            id = "oct-exp-2",
            type = "expense",
            date = "2026-10-02",
            category = "Жилье",
            subcategory = "Октябрьская Аренда",
            amount = 35000.0,
            createdAt = 1759396800000L
        ),
        TransactionEntity(
            id = "oct-inc-1",
            type = "income",
            date = "2026-10-10",
            category = "Зарплата",
            subcategory = "Октябрьский Аванс",
            amount = 85000.0,
            createdAt = 1760088000000L
        ),
        // Сентябрь 2026 (предыдущий месяц, offset = -1)
        TransactionEntity(
            id = "sep-exp-1",
            type = "expense",
            date = "2026-09-18",
            category = "Продукты",
            subcategory = "Сентябрьский Супермаркет",
            amount = 6400.0,
            createdAt = 1758187200000L
        ),
        TransactionEntity(
            id = "sep-inc-1",
            type = "income",
            date = "2026-09-15",
            category = "Фриланс",
            subcategory = "Сентябрьский Проект",
            amount = 42000.0,
            createdAt = 1757928000000L
        ),
        // Август 2026 (два месяца назад, offset = -2)
        TransactionEntity(
            id = "aug-exp-1",
            type = "expense",
            date = "2026-08-20",
            category = "Путешествия",
            subcategory = "Августовский Отель",
            amount = 38000.0,
            createdAt = 1755681600000L
        ),
        TransactionEntity(
            id = "aug-inc-1",
            type = "income",
            date = "2026-08-05",
            category = "Премия",
            subcategory = "Августовский Бонус",
            amount = 60000.0,
            createdAt = 1754385600000L
        )
    )

    private fun assertTransactionDisplayedInList(title: String) {
        composeTestRule.onNodeWithTag("transactions_lazy_list")
            .performScrollToNode(hasText(title))
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    private fun saveUiScreenshot(name: String) {
        composeTestRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dirs = listOfNotNull(
            context.getExternalFilesDir("ui_screenshots"),
            File(context.cacheDir, "ui_screenshots"),
            File("/sdcard/Download/ui_screenshots")
        )

        val bitmap: Bitmap? = try {
            composeTestRule.onNodeWithTag("all_transactions_root")
                .captureToImage()
                .asAndroidBitmap()
        } catch (_: Throwable) {
            null
        }

        dirs.forEach { dir ->
            try {
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, "$name.png")
                if (bitmap != null) {
                    FileOutputStream(outFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                } else {
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .takeScreenshot(outFile)
                }
            } catch (_: Throwable) {
            }
        }
    }

    @Test
    fun test1_initialOpenShowsCurrentMonthOnlyAndNoDuplicateChipsUnderSearch() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    AllTransactionsDialog(
                        transactions = threeMonthsFixtures,
                        initialFilterType = "all",
                        initialDate = "2026-10-15",
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. Отображаются верхние карточки расходов и доходов
        composeTestRule.onNodeWithTag("double_summary_cards").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summary_expense_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summary_income_card").assertIsDisplayed()

        // 2. В списке отображаются операции ТОЛЬКО за текущий месяц (Октябрь 2026 — 3 операции)
        composeTestRule.onNodeWithText("Показано 3 из 3 операций").assertIsDisplayed()
        assertTransactionDisplayedInList("Октябрьский Раф")
        assertTransactionDisplayedInList("Октябрьский Аванс")
        assertTransactionDisplayedInList("Октябрьская Аренда")

        // Сентябрьские и августовские операции НЕ должны отображаться при открытии
        composeTestRule.onNodeWithText("Сентябрьский Супермаркет").assertDoesNotExist()
        composeTestRule.onNodeWithText("Августовский Отель").assertDoesNotExist()

        // 3. Период при открытии НЕ зафиксирован (крестик сброса периода отсутствует)
        composeTestRule.onNodeWithTag("period_chip_reset").assertDoesNotExist()

        // 4. Под поиском отсутствуют дублирующие кнопки «Все / Траты / Доходы»
        composeTestRule.onNode(
            hasText("Все") and hasParent(hasTestTag("sort_and_filter_chips_row"))
        ).assertDoesNotExist()
        composeTestRule.onNode(
            hasText("Траты") and hasParent(hasTestTag("sort_and_filter_chips_row"))
        ).assertDoesNotExist()
        composeTestRule.onNode(
            hasText("Доходы") and hasParent(hasTestTag("sort_and_filter_chips_row"))
        ).assertDoesNotExist()

        // При этом кнопки сортировки и чип календаря на месте
        composeTestRule.onNodeWithTag("sort_amount_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sort_name_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_date_chip").assertIsDisplayed()

        saveUiScreenshot("01_initial_open_october_all")
    }

    @Test
    fun test2_topSummaryCardsFilterByTypeAndPeriodSelectorHasNoAllTabOrFalseHighlight() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    AllTransactionsDialog(
                        transactions = threeMonthsFixtures,
                        initialFilterType = "all",
                        initialDate = "2026-10-15",
                        onDismiss = {}
                    )
                }
            }
        }

        // Нажимаем на верхнюю карточку расходов
        composeTestRule.onNodeWithTag("summary_expense_card").performClick()
        composeTestRule.waitForIdle()

        // Открывается карточка с диаграммой и переключателем периодов
        composeTestRule.onNodeWithTag("single_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_selector_bar").assertIsDisplayed()

        // В переключателе периодов есть только Нед, Мес, Год — отдельной кнопки «Все» нет
        composeTestRule.onNodeWithTag("period_tab_week").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_year").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_all").assertDoesNotExist()

        // Первоначально кнопка «Мес» НЕ выглядит нажатой и индикатор фиксации периода отсутствует
        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsNotSelected()

        // Список отфильтрован только по расходам Октября (2 из 3 операций)
        composeTestRule.onNodeWithText("Показано 2 из 3 операций").assertIsDisplayed()
        assertTransactionDisplayedInList("Октябрьский Раф")
        assertTransactionDisplayedInList("Октябрьская Аренда")
        composeTestRule.onNodeWithText("Октябрьский Аванс").assertDoesNotExist()

        saveUiScreenshot("02_expense_chart_october_unlocked")

        // Возвращаемся ко всем операциям и нажимаем карточку доходов
        composeTestRule.onNodeWithTag("hero_reset_to_all_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("summary_income_card").performClick()
        composeTestRule.waitForIdle()

        // Список показывает только доходы Октября (1 из 3 операций)
        composeTestRule.onNodeWithText("Показано 1 из 3 операций").assertIsDisplayed()
        assertTransactionDisplayedInList("Октябрьский Аванс")
        composeTestRule.onNodeWithText("Октябрьский Раф").assertDoesNotExist()

        saveUiScreenshot("03_income_chart_october")
    }

    @Test
    fun test3_swipeChartToPreviousMonthsSynchronouslyUpdatesListWithoutLockingPeriod() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    AllTransactionsDialog(
                        transactions = threeMonthsFixtures,
                        initialFilterType = "expense",
                        initialDate = "2026-10-15",
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertTransactionDisplayedInList("Октябрьский Раф")
        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()

        // Свайп диаграммы вправо -> переход на предыдущий месяц (Сентябрь 2026)
        composeTestRule.onNodeWithTag("hero_chart_swipe_box").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()

        // Список синхронно переключился на Сентябрь 2026, а период остался незафиксированным
        assertTransactionDisplayedInList("Сентябрьский Супермаркет")
        composeTestRule.onNodeWithText("Октябрьский Раф").assertDoesNotExist()
        composeTestRule.onNodeWithText("Августовский Отель").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsNotSelected()

        saveUiScreenshot("04_swiped_to_september_unlocked")

        // Ещё один свайп вправо -> переход на Август 2026
        composeTestRule.onNodeWithTag("hero_chart_swipe_box").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()

        assertTransactionDisplayedInList("Августовский Отель")
        composeTestRule.onNodeWithText("Сентябрьский Супермаркет").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()

        // Свайп влево -> возврат на Сентябрь 2026
        composeTestRule.onNodeWithTag("hero_chart_swipe_box").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertTransactionDisplayedInList("Сентябрьский Супермаркет")
        composeTestRule.onNodeWithText("Августовский Отель").assertDoesNotExist()
    }

    @Test
    fun test4_lockDisplayedMonthWithMesAndUnlockOnSecondTapWithoutShowingAllTimeHistory() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    AllTransactionsDialog(
                        transactions = threeMonthsFixtures,
                        initialFilterType = "expense",
                        initialDate = "2026-10-15",
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Перелистываем диаграмму с Октября на Сентябрь 2026
        composeTestRule.onNodeWithTag("hero_chart_swipe_box").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()
        assertTransactionDisplayedInList("Сентябрьский Супермаркет")

        // Первое нажатие «Мес» -> фиксирует текущий отображаемый месяц (Сентябрь)
        composeTestRule.onNodeWithTag("period_tab_month").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_active_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsSelected()
        composeTestRule.onNodeWithTag("period_chip_reset").assertIsDisplayed()
        // Месяц не сбросился на Октябрь — отображается именно Сентябрь
        assertTransactionDisplayedInList("Сентябрьский Супермаркет")
        composeTestRule.onNodeWithText("Октябрьский Раф").assertDoesNotExist()

        saveUiScreenshot("05_september_locked_mes_active")

        // Повторное нажатие «Мес» -> снимает фиксацию периода, оставаясь на Сентябре (НЕ показывает все операции за всё время)
        composeTestRule.onNodeWithTag("period_tab_month").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_tab_month").assertIsNotSelected()
        composeTestRule.onNodeWithTag("period_chip_reset").assertDoesNotExist()
        assertTransactionDisplayedInList("Сентябрьский Супермаркет")
        composeTestRule.onNodeWithText("Октябрьский Раф").assertDoesNotExist()
        composeTestRule.onNodeWithText("Августовский Отель").assertDoesNotExist()

        saveUiScreenshot("06_september_unlocked_after_second_tap")
    }

    @Test
    fun test5_toggleWeekAndYearPeriodsAndReturnToMonthlyView() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                ) {
                    AllTransactionsDialog(
                        transactions = threeMonthsFixtures,
                        initialFilterType = "expense",
                        initialDate = "2026-10-15",
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Нажатие «Нед» -> включает недельный фильтр (неделя 12.10 - 18.10)
        composeTestRule.onNodeWithTag("period_tab_week").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_active_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_week").assertIsSelected()
        assertTransactionDisplayedInList("Октябрьский Раф")
        // Операция от 2 октября не входит в неделю 12-18 октября
        composeTestRule.onNodeWithText("Октябрьская Аренда").assertDoesNotExist()

        saveUiScreenshot("07_week_period_active")

        // Нажатие «Год» -> заменяет недельный фильтр на годовой (весь 2026 год: 4 расхода из 7 операций)
        composeTestRule.onNodeWithTag("period_tab_year").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_active_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("period_tab_year").assertIsSelected()
        composeTestRule.onNodeWithTag("period_tab_week").assertIsNotSelected()
        composeTestRule.onNodeWithText("Показано 4 из 7 операций").assertIsDisplayed()
        assertTransactionDisplayedInList("Октябрьский Раф")
        assertTransactionDisplayedInList("Сентябрьский Супермаркет")
        assertTransactionDisplayedInList("Августовский Отель")

        saveUiScreenshot("08_year_period_active")

        // Повторное нажатие «Год» -> снимает годовой фильтр и возвращает к просмотру Октября (2 расхода из 3 операций)
        composeTestRule.onNodeWithTag("period_tab_year").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("period_active_indicator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("period_tab_year").assertIsNotSelected()
        composeTestRule.onNodeWithText("Показано 2 из 3 операций").assertIsDisplayed()
        assertTransactionDisplayedInList("Октябрьский Раф")
        assertTransactionDisplayedInList("Октябрьская Аренда")
        composeTestRule.onNodeWithText("Сентябрьский Супермаркет").assertDoesNotExist()
        composeTestRule.onNodeWithText("Августовский Отель").assertDoesNotExist()
    }
}
