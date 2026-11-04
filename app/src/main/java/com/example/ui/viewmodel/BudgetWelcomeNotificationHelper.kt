package com.example.ui.viewmodel

import android.content.Context
import com.example.data.db.NotificationEntity
import com.example.data.preferences.BudgetPreferencesRepository
import com.example.data.repository.BudgetRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BudgetWelcomeNotificationHelper {

    fun getGreetingForHour(hour: Int): String = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    suspend fun ensureWelcomeNotification(
        context: Context,
        repository: BudgetRepository,
        currentBudgetId: String,
        profileName: String
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = getGreetingForHour(hour)
        val nameStr = if (profileName.isNotBlank() && profileName != "Вы") ", $profileName" else ""

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentProfileKey = currentBudgetId
        val lastGreetingDate = BudgetPreferencesRepository.getLastGreetingDate(context, currentProfileKey)
        val isFirstLaunch = !BudgetPreferencesRepository.hasWelcomedFirstTime(context, currentProfileKey)
        val now = System.currentTimeMillis()

        val currentNotifs = try {
            repository.getNotificationsForBudget(currentBudgetId).first()
        } catch (e: Exception) {
            emptyList()
        }

        val alreadyHasGreetingToday = currentNotifs.any {
            it.title == "Жабов Давид" &&
                (it.description.startsWith("Добр") || it.description.startsWith("Салют")) &&
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp)) == todayDate
        }
        if (alreadyHasGreetingToday) {
            return
        }

        val hasUnread = currentNotifs.any { !it.isRead }

        if (isFirstLaunch) {
            BudgetPreferencesRepository.markFirstTimeWelcomed(context, currentProfileKey, todayDate)
            val greetingMsg = "$timeGreeting$nameStr!"
            repository.insertNotification(
                NotificationEntity(
                    budgetId = currentBudgetId,
                    title = "Жабов Давид",
                    description = greetingMsg,
                    icon = "david",
                    color = "emerald400",
                    timestamp = now,
                    isRead = false
                )
            )
        } else if (!hasUnread && lastGreetingDate != todayDate) {
            BudgetPreferencesRepository.setLastGreetingDate(context, currentProfileKey, todayDate)
            val greetingMsg = "$timeGreeting$nameStr!"
            repository.insertNotification(
                NotificationEntity(
                    budgetId = currentBudgetId,
                    title = "Жабов Давид",
                    description = greetingMsg,
                    icon = "david",
                    color = "emerald400",
                    timestamp = now,
                    isRead = false
                )
            )
        }
    }

    fun splitAuditIntoSections(auditText: String): List<String> {
        if (auditText.isBlank() || auditText == "ERROR_NO_CONNECTION") return emptyList()
        val headerRegex = Regex("(?m)^(?=#{1,6}\\s+|(?i)(?:Главный Вердикт|Цифры и Динамика|Прожарка|Ачивки|Выводы))")
        val rawBlocks = auditText.split(headerRegex)
        return rawBlocks
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "ERROR_NO_CONNECTION" }
    }
}
