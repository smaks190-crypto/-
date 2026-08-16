package com.example.ui.components

import com.example.data.db.NotificationEntity

data class ExtractedOp(
    val type: String,
    val category: String,
    val subcategory: String,
    val amount: Double
)

data class NotifParsedInfo(
    val ops: List<ExtractedOp>,
    val userPhrase: String,
    val comment: String
)

/**
 * Parses operation data and comments from a NotificationEntity description string.
 */
fun extractOpsAndComment(notification: NotificationEntity): NotifParsedInfo {
    val ops = mutableListOf<ExtractedOp>()
    var userPhrase = ""
    var comment = ""
    try {
        if (notification.description.startsWith("||")) {
            val parts = notification.description.split("||")
            if (parts.size >= 3) {
                if (parts[1] == "MULTI") {
                    val opsRaw = parts[2].split(";")
                    for (opRaw in opsRaw) {
                        val opParts = opRaw.split("|")
                        if (opParts.size >= 4) {
                            ops.add(
                                ExtractedOp(
                                    type = opParts[0],
                                    category = opParts[1],
                                    subcategory = opParts[2],
                                    amount = opParts[3].toDoubleOrNull() ?: 0.0
                                )
                            )
                        }
                    }
                    if (parts.size >= 5) {
                        userPhrase = parts[3]
                        comment = parts[4]
                    } else if (parts.size >= 4) {
                        comment = parts[3]
                    }
                } else {
                    val txParts = parts[1].split("|")
                    if (txParts.size >= 4) {
                        ops.add(
                            ExtractedOp(
                                type = txParts[0],
                                category = txParts[1],
                                subcategory = txParts[2],
                                amount = txParts[3].toDoubleOrNull() ?: 0.0
                            )
                        )
                        if (txParts.size >= 5) {
                            userPhrase = txParts[4]
                        }
                    }
                    comment = parts[2]
                }
            }
        } else {
            // Обычное текстовое системное уведомление или приветствие (без операций)
            comment = notification.description
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return NotifParsedInfo(ops, userPhrase, comment)
}

/**
 * Splits audit raw markdown response into display sections based on section headers.
 */
fun splitIntoSections(auditText: String): List<String> {
    if (auditText.isBlank() || auditText == "ERROR_NO_CONNECTION") return emptyList()

    val headerRegex = Regex("(?m)^(?=#{1,6}\\s+|(?i)(?:Главный Вердикт|Цифры и Динамика|Прожарка|Ачивки|Выводы))")
    val rawBlocks = auditText.split(headerRegex)

    return rawBlocks
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "ERROR_NO_CONNECTION" }
}

sealed class ChatItem {
    abstract val timestamp: Long
    open val isFromUser: Boolean get() = false
    open val isRead: Boolean get() = true
}

data class ChatWelcomeItem(
    override val timestamp: Long = System.currentTimeMillis()
) : ChatItem()

data class ChatChangelogItem(
    override val timestamp: Long = System.currentTimeMillis()
) : ChatItem()

data class ChatAuditOfferItem(
    override val timestamp: Long = 1000L
) : ChatItem()

data class ChatUnreadSeparatorItem(
    override val timestamp: Long
) : ChatItem()

data class ChatNotificationUserItem(
    val notification: NotificationEntity
) : ChatItem() {
    override val timestamp: Long = notification.timestamp
    override val isFromUser: Boolean get() = true
    override val isRead: Boolean get() = true
}

data class ChatNotificationDavidItem(
    val notification: NotificationEntity
) : ChatItem() {
    override val timestamp: Long = notification.timestamp + 10L
    override val isFromUser: Boolean get() = false
    override val isRead: Boolean get() = notification.isRead
}

data class ChatAuditRequestItem(
    override val timestamp: Long,
    val text: String = "",
    val fileName: String? = null,
    val hasError: Boolean = false
) : ChatItem() {
    override val isFromUser: Boolean get() = true
    override val isRead: Boolean get() = true
}

data class ChatAuditSystemItem(
    override val timestamp: Long
) : ChatItem()

data class ChatAuditBlockItem(
    override val timestamp: Long,
    val text: String,
    val isFirst: Boolean
) : ChatItem()

data class ChatAuditRetryItem(
    override val timestamp: Long
) : ChatItem()

data class ChatTypingItem(
    override val timestamp: Long,
    val type: String = "audit"
) : ChatItem()

data class ChatConnectingItem(
    override val timestamp: Long,
    val isRestored: Boolean = false
) : ChatItem()
