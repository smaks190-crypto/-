package com.example

import com.example.data.db.TransactionEntity
import com.example.ui.components.getProfileInitials
import com.example.ui.viewmodel.BudgetWelcomeNotificationHelper
import com.example.ui.viewmodel.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun profileInitials_and_periodFiltering_workCorrectly() {
        assertEquals("Б", getProfileInitials(""))
        assertEquals("М", getProfileInitials("Максим"))
        assertEquals("КД", getProfileInitials("Казума Десу"))

        val txs = listOf(
            TransactionEntity(id = "1", type = "expense", date = "2026-10-05", category = "Еда", subcategory = "", amount = 500.0),
            TransactionEntity(id = "2", type = "expense", date = "2026-10-31", category = "Кафе", subcategory = "", amount = 900.0),
            TransactionEntity(id = "3", type = "income", date = "2026-11-01", category = "Зарплата", subcategory = "", amount = 50000.0)
        )

        val dayFiltered = filterTransactionsForPeriod(txs, PeriodType.DAY, "2026-10-31", 9, 2026, "2026-01-01", "2026-12-31")
        assertEquals(1, dayFiltered.size)
        assertEquals("2", dayFiltered.first().id)

        val octFiltered = filterTransactionsForPeriod(txs, PeriodType.MONTH, "2026-10-31", 9, 2026, "2026-01-01", "2026-12-31")
        assertEquals(2, octFiltered.size)

        val rangeFiltered = filterTransactionsForPeriod(txs, PeriodType.ALL, "2026-10-31", 9, 2026, "2026-10-10", "2026-11-05")
        assertEquals(2, rangeFiltered.size)
    }

    @Test
    fun budgetWelcomeNotificationHelper_greetingsAndAuditSplitting_workCorrectly() {
        assertEquals("Доброе утро", BudgetWelcomeNotificationHelper.getGreetingForHour(8))
        assertEquals("Добрый день", BudgetWelcomeNotificationHelper.getGreetingForHour(14))
        assertEquals("Добрый вечер", BudgetWelcomeNotificationHelper.getGreetingForHour(19))
        assertEquals("Доброй ночи", BudgetWelcomeNotificationHelper.getGreetingForHour(2))

        val sections = BudgetWelcomeNotificationHelper.splitAuditIntoSections(
            """
            # Главный Вердикт
            Все отлично!
            # Прожарка
            Хватит покупать кофе.
            """.trimIndent()
        )
        assertEquals(2, sections.size)
        assertTrue(sections[0].contains("Главный Вердикт"))
        assertTrue(sections[1].contains("Прожарка"))
    }
}
