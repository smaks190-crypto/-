package com.example.davidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Файл DavidChat.kt — рефакторинг модуля диалога с финансовым ассистентом Давидом Жабовым.
 *
 * Архитектура спроектирована по стандартам Clean Architecture / SRP:
 * 1. Модели данных: [ChatMessageType], [ChatMessage], [DavidStage] (см. DavidChatModels.kt)
 * 2. Звуковой движок: [SoundManager] с синтезом тонов и поддержкой SoundPool (см. DavidSoundManager.kt)
 * 3. Логика и состояние: [DavidViewModel] (см. DavidViewModel.kt)
 * 4. UI Компоненты: Dark Neon / Cyberpunk Minimalist компоненты (см. DavidChatComponents.kt)
 * 5. Экран: [DavidChatScreen] (см. DavidChatScreen.kt)
 */

/**
 * Точка входа для Composable экрана DavidChat
 */
@Composable
fun DavidChat(
    viewModel: DavidViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    soundManager: SoundManager? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    DavidChatScreen(
        viewModel = viewModel,
        soundManager = soundManager,
        onBack = onBack,
        modifier = modifier
    )
}
