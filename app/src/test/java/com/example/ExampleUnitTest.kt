package com.example

import com.example.data.db.TransactionEntity
import com.example.davidapp.DavidChatAuditSupport
import com.example.ui.components.getProfileInitials
import com.example.ui.screens.formatHumanDateHeader
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

    @Test
    fun stage6_periodBudgetAndDavidChatAuditSupport_workCorrectly() {
        assertEquals("Сегодня", formatHumanDateHeader("2026-10-31", "2026-10-31"))
        assertEquals("Вчера", formatHumanDateHeader("2026-10-30", "2026-10-31"))
        assertEquals("15 октября", formatHumanDateHeader("2026-10-15", "2026-10-31"))

        val txs = listOf(
            TransactionEntity(id = "1", type = "expense", date = "2026-10-15", category = "Кафе", subcategory = "Латте", amount = 450.0),
            TransactionEntity(id = "2", type = "expense", date = "2026-10-16", category = "Кафе", subcategory = "Ужин", amount = 1550.0)
        )
        val points = DavidChatAuditSupport.generateRealDataPoints("Месяц", txs, income = 50000.0, expense = 2000.0)
        assertTrue(points.size >= 6)

        val report = DavidChatAuditSupport.buildDefaultSarcasticReport(
            currentProfileName = "Максим",
            period = "Месяц",
            income = 50000.0,
            expense = 2000.0,
            net = 48000.0,
            transactions = txs
        )
        assertTrue(report.contains("Максим"))
        assertTrue(report.contains("Кафе"))

        val sections = DavidChatAuditSupport.splitReportIntoSections(report)
        assertTrue(sections.size >= 4)

        val reply = DavidChatAuditSupport.generateSmartDavidReply("Максим", "привет")
        assertTrue(reply.contains("Максим"))
    }
}
