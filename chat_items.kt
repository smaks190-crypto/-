sealed class ChatItem {
    abstract val timestamp: Long
}
data class ChatNotificationItem(val notification: com.example.data.db.NotificationEntity) : ChatItem() {
    override val timestamp: Long = notification.timestamp
}
data class ChatAuditRequestItem(override val timestamp: Long) : ChatItem()
data class ChatAuditSystemItem(override val timestamp: Long) : ChatItem()
data class ChatAuditBlockItem(override val timestamp: Long, val text: String, val isFirst: Boolean) : ChatItem()
data class ChatTypingItem(override val timestamp: Long, val type: String) : ChatItem()
