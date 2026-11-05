package com.example.davidapp.live

fun isDavidLiveVoiceCapableState(connectionState: DavidLiveConnectionState): Boolean {
    return connectionState == DavidLiveConnectionState.LISTENING ||
        connectionState == DavidLiveConnectionState.USER_SPEAKING ||
        connectionState == DavidLiveConnectionState.DAVID_THINKING ||
        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        connectionState == DavidLiveConnectionState.FINANCE_LOOKUP ||
        connectionState == DavidLiveConnectionState.ANALYZING ||
        connectionState == DavidLiveConnectionState.PREPARING_RESPONSE ||
        connectionState == DavidLiveConnectionState.SUCCESS ||
        connectionState == DavidLiveConnectionState.MUTED
}

/**
 * Resolves the visual animation phase for David Live based on the actual session state.
 */
fun resolveDavidLiveVisualPhase(
    connectionState: DavidLiveConnectionState,
    explicitVisualPhase: DavidLiveVisualPhase? = null,
    isWaitingForNetwork: Boolean = false,
    isReadyFlashActive: Boolean = false
): DavidLiveVisualPhase {
    if (explicitVisualPhase != null && explicitVisualPhase != DavidLiveVisualPhase.IDLE) {
        return explicitVisualPhase
    }
    return when (connectionState) {
        DavidLiveConnectionState.CONNECTING -> DavidLiveVisualPhase.INITIAL_CONNECTING
        DavidLiveConnectionState.OFFLINE,
        DavidLiveConnectionState.ERROR -> DavidLiveVisualPhase.CONNECTION_LOST
        DavidLiveConnectionState.RECONNECTING ->
            if (isWaitingForNetwork) DavidLiveVisualPhase.CONNECTION_LOST else DavidLiveVisualPhase.RECONNECTING
        DavidLiveConnectionState.LISTENING,
        DavidLiveConnectionState.USER_SPEAKING,
        DavidLiveConnectionState.DAVID_THINKING,
        DavidLiveConnectionState.DAVID_SPEAKING,
        DavidLiveConnectionState.FINANCE_LOOKUP,
        DavidLiveConnectionState.ANALYZING,
        DavidLiveConnectionState.PREPARING_RESPONSE,
        DavidLiveConnectionState.SUCCESS,
        DavidLiveConnectionState.MUTED ->
            if (isReadyFlashActive) DavidLiveVisualPhase.READY_FLASH else DavidLiveVisualPhase.CONVERSATION_ACTIVE
        DavidLiveConnectionState.IDLE,
        DavidLiveConnectionState.ENDED,
        DavidLiveConnectionState.NO_API_KEY -> DavidLiveVisualPhase.IDLE
    }
}

/**
 * Resolves the human-readable Russian status label for the David Live session,
 * shared between the full-screen interface and the compact minimized capsule.
 *
 * Strictly reflects the 5 owner-agreed connection phases:
 * - Первое подключение: «Пробуждаем Давида...»
 * - Потеря соединения / ошибка: «Связь с Давидом прервана»
 * - Автоматическое/ручное переподключение: «Восстанавливаем связь...»
 * - Сессия действительно готова (после setupComplete): «Давид на связи»
 * - Штатный диалог: «Слушаю тебя...» / «Говорит Давид...» / «Давид думает...» / «Микрофон выключен»
 */
fun resolveDavidLiveStatusLabel(
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    visualPhase: DavidLiveVisualPhase
): String {
    val effectivePhase = resolveDavidLiveVisualPhase(
        connectionState = connectionState,
        explicitVisualPhase = visualPhase
    )
    return when {
        connectionState == DavidLiveConnectionState.NO_API_KEY -> "Требуется API-ключ"
        connectionState == DavidLiveConnectionState.ERROR ||
            effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST -> "Связь с Давидом прервана"
        connectionState == DavidLiveConnectionState.RECONNECTING ||
            effectivePhase == DavidLiveVisualPhase.RECONNECTING -> "Восстанавливаем связь..."
        connectionState == DavidLiveConnectionState.CONNECTING ||
            effectivePhase == DavidLiveVisualPhase.INITIAL_CONNECTING -> "Пробуждаем Давида..."
        connectionState == DavidLiveConnectionState.ENDED ||
            connectionState == DavidLiveConnectionState.IDLE -> "Сессия завершена"
        isMicMuted && activeSpeaker != DavidLiveSpeaker.DAVID &&
            connectionState != DavidLiveConnectionState.DAVID_SPEAKING -> "Микрофон выключен"
        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
            activeSpeaker == DavidLiveSpeaker.DAVID -> "Говорит Давид..."
        connectionState == DavidLiveConnectionState.DAVID_THINKING -> "Давид думает..."
        effectivePhase == DavidLiveVisualPhase.READY_FLASH -> "Давид на связи"
        else -> "Слушаю тебя..."
    }
}

