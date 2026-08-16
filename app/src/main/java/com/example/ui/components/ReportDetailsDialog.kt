package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.NotificationEntity
import com.example.davidapp.DavidChatScreen
import com.example.davidapp.DavidViewModel
import com.example.davidapp.SoundManager

/**
 * Диалог финансового аудита и интеллектуального чата с Давидом Жабовым.
 *
 * Выполнен в палитре Dark Neon / Slate950 / Emerald / Indigo.
 * Архитектура:
 * 1. Модели данных: ChatMessageType, ChatMessage, DavidStage
 * 2. Звуковой движок: SoundManager (SoundPool + ToneGenerator)
 * 3. Бизнес-логика: DavidViewModel
 * 4. UI-слой: DavidChatComponents (TopBar, MessageItem, Bézier Chart, QuickActions, Dynamic Input)
 */
@Composable
fun ReportDetailsDialog(
    budgetId: String = "default",
    apiKey: String = "",
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

    val davidViewModel: DavidViewModel = viewModel()
    LaunchedEffect(budgetId, profileName, apiKey) {
        davidViewModel.bindProfile(budgetId, profileName, apiKey)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DavidChatScreen(
            budgetId = budgetId,
            profileName = profileName,
            apiKey = apiKey,
            viewModel = davidViewModel,
            onBack = onDismiss
        )
    }
}
