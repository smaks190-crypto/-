package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.NotificationEntity
import com.example.davidapp.DavidChatScreen

/**
 * Диалог отчетов и чата с Давидом Жабовым.
 * Делегирует отображение в отрефакторенный модуль DavidChatScreen.
 */
@Composable
fun ReportDetailsDialog(
    periodTitle: String = "",
    auditText: String = "",
    income: Double? = null,
    expense: Double? = null,
    prevIncome: Double? = null,
    prevExpense: Double? = null,
    isLoading: Boolean = false,
    isGeneratingReaction: Boolean = false,
    auditTimestamp: Long? = null,
    profileName: String = "Максим",
    notifications: List<NotificationEntity> = emptyList(),
    initialTab: Int = 0,
    onRequestAudit: () -> Any = {},
    onDeleteNotification: (Long) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onMarkAllRead()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DavidChatScreen(
            onBack = onDismiss
        )
    }
}
