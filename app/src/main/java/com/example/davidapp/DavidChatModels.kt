package com.example.davidapp

import java.util.UUID

/**
 * Типы сообщений в чате с Давидом Жабовым
 */
sealed class ChatMessageType {
    /**
     * Обычное текстовое сообщение
     */
    data class Text(val content: String) : ChatMessageType()

    /**
     * Файловое вложение (выписка, квитанция, документ)
     */
    data class File(
        val name: String,
        val size: String,
        val extension: String = "PDF"
    ) : ChatMessageType()

    /**
     * Интерактивная финансовая диаграмма / аналитический график
     */
    data class Chart(
        val title: String,
        val summary: String = "",
        val income: Double = 145000.0,
        val expense: Double = 89400.0,
        val deltaPercent: Double = 14.8,
        val dataPoints: List<Float> = listOf(28f, 42f, 35f, 60f, 52f, 85f, 74f, 92f)
    ) : ChatMessageType()

    /**
     * Карточка операции (добавленный расход / доход) с фразой пользователя
     */
    data class Operation(
        val type: String, // "expense" or "income"
        val category: String,
        val subcategory: String,
        val amount: Double,
        val userPhrase: String = "",
        val isRead: Boolean = true
    ) : ChatMessageType()
}

/**
 * Модель отдельного сообщения в диалоге
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val type: ChatMessageType,
    val timestamp: String = "12:00",
    val isUser: Boolean = false
)

/**
 * Этапы диалогового сценария
 */
enum class DavidStage {
    INITIAL,
    FILE_SELECTION,
    PROCESSING,
    FOLLOW_UP
}
