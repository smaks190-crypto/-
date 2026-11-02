package com.example.davidapp.live

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * Режим отображения голосового интерфейса David Live.
 * Позволяет плавно сворачивать полноэкранный режим в компактную нижнюю капсулу
 * без разрыва WebSocket-соединения, остановки аудиопотока или потери контекста.
 */
enum class DavidLiveDisplayMode {
    HIDDEN,
    EXPANDED,
    MINIMIZED
}

/**
 * Состояния голосового режима David Live.
 * Сохраняет обратную совместимость со всеми существующими вызовами и дополняет
 * детализированными фазами обработки для единой системы визуальных состояний (Issue #56).
 */
enum class DavidLiveConnectionState {
    IDLE,
    CONNECTING,
    LISTENING,
    USER_SPEAKING,
    DAVID_THINKING,
    FINANCE_LOOKUP,
    ANALYZING,
    PREPARING_RESPONSE,
    DAVID_SPEAKING,
    MUTED,
    RECONNECTING,
    OFFLINE,
    NO_API_KEY,
    ERROR,
    SUCCESS,
    ENDED
}

/**
 * Причина сбоя или недоступности сессии David Live.
 * Позволяет строго различать потерю интернет-соединения (Offline),
 * отсутствие API-ключа и неверный/заблокированный API-ключ или ошибку протокола.
 */
enum class DavidLiveConnectionFailureKind {
    NONE,
    NETWORK_OFFLINE,
    MISSING_API_KEY,
    INVALID_API_KEY,
    PROTOCOL_OR_SERVER_ERROR
}

/**
 * Единая централизованная модель визуальных состояний 3D Mesh-сферы David Live (Issue #56).
 *
 * Каждое состояние однозначно определяет цветовую палитру, динамику 3D-сетки,
 * поведение волн/орбит/частиц, заголовок, подзаголовок и контекстное действие.
 */
enum class DavidLiveVisualState {
    /** Ожидание и прослушивание: медленное дыхание, мягкое изумрудное свечение. */
    IDLE_LISTENING,
    /** Пользователь говорит: реакция на входящий аудиосигнал, бирюзовый акцент, эквалайзер снизу. */
    USER_SPEAKING,
    /** Давид думает: перестроение сетки, орбитальные кольца и световые импульсы. */
    THINKING,
    /** Поиск финансовых данных: горизонтальное кольцо сканирования и финансовые микро-акценты (только при реальном вызове tool). */
    FINANCE_LOOKUP,
    /** Анализ финансов: глубокие согласованные волны, перекрестные аналитические орбиты, cyan-изумрудный градиент. */
    ANALYZING,
    /** Подготовка ответа: плавная фокусировка от анализа к голосовому ответу Давида. */
    PREPARING_RESPONSE,
    /** Говорит Давид: реакция на реальное воспроизводимое аудио, акустические дуги по бокам, изумрудно-бирюзовые волны. */
    DAVID_SPEAKING,
    /** Первичное подключение к Gemini Live (ожидание подтверждённого setupComplete и готовности аудио). */
    CONNECTING,
    /** Переподключение после потери связи. */
    RECONNECTING,
    /** Потеря соединения: красная mesh-сфера, затухание движения, иконка «!» внутри сферы, нажатие на сферу запускает reconnect. */
    OFFLINE,
    /** Отсутствует API-ключ: золотистая mesh-сфера с иконкой ключа, контекстное действие «Добавить API». */
    API_MISSING,
    /** Критическая ошибка (неверный API-ключ, отказ доступа или сбой сервера): фиолетовая mesh-сфера с «!». */
    ERROR,
    /** Кратковременное подтверждение успешного завершения операции/проверки: ярко-зелёная сфера с галочкой. */
    SUCCESS
}

/**
 * Тип контекстного действия в облачке состояния и при интерактивном нажатии на сферу.
 */
enum class DavidLiveVisualActionType {
    NONE,
    RECONNECT,
    ADD_API_KEY,
    UPDATE_API_KEY,
    OPEN_API_SETTINGS,
    RETRY_ERROR
}

/**
 * Полное описание визуального состояния для UI-компонентов David Live (шапка, облачко, подписи, доступность).
 */
@Immutable
data class DavidLiveVisualMetadata(
    val visualState: DavidLiveVisualState,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val actionType: DavidLiveVisualActionType,
    val actionLabel: String?,
    val sphereTapTriggersReconnect: Boolean,
    val sphereTapOpensApiSettings: Boolean
)

/**
 * Детерминированный резолвер единого визуального состояния [DavidLiveVisualState]
 * на основе реальных сигналов сессии, аудиодвижка, финансовых инструментов и сетевых событий.
 *
 * Строгий порядок приоритетов (исключает конфликтующие состояния):
 * 1. Тестовый/отладочный override (`debugOverrideState`), если активирован в debug-режиме.
 * 2. Отсутствие API-ключа (`NO_API_KEY` или `MISSING_API_KEY`) -> [DavidLiveVisualState.API_MISSING].
 * 3. Потеря сетевого соединения (`OFFLINE` или `ERROR` с `NETWORK_OFFLINE`) -> [DavidLiveVisualState.OFFLINE]
 *    (имеет абсолютный приоритет над Thinking, Finance Lookup, Analyzing и Speaking).
 * 4. Критическая ошибка (`ERROR`, включая неверный API-ключ) -> [DavidLiveVisualState.ERROR].
 * 5. Переподключение (`RECONNECTING`) -> [DavidLiveVisualState.RECONNECTING].
 * 6. Подключение (`CONNECTING` или сессия ещё не подтвердила готовность `isSessionReady == false`) -> [DavidLiveVisualState.CONNECTING].
 * 7. Реальная голосовая речь Давида (`DAVID_SPEAKING` или `activeSpeaker == DAVID` при наличии невычерпанного playback) -> [DavidLiveVisualState.DAVID_SPEAKING].
 * 8. Реальная речь пользователя (`USER_SPEAKING` или `activeSpeaker == USER` или удержание голосовой паузы `isWithinSpeechHoldWindow`) -> [DavidLiveVisualState.USER_SPEAKING].
 * 9. Активный вызов финансового инструмента (`isToolLookupActive == true` или `FINANCE_LOOKUP`) -> [DavidLiveVisualState.FINANCE_LOOKUP].
 * 10. Фаза анализа полученных финансовых данных (`isAnalyzingToolData == true` или `ANALYZING`) -> [DavidLiveVisualState.ANALYZING].
 * 11. Подготовка голосового ответа (`isPreparingResponse == true` или `PREPARING_RESPONSE`) -> [DavidLiveVisualState.PREPARING_RESPONSE].
 * 12. Размышление модели (`isModelThinking == true` или `DAVID_THINKING`) -> [DavidLiveVisualState.THINKING].
 * 13. Кратковременное подтверждение успеха (`isSuccessPulseActive == true` или `SUCCESS`) -> [DavidLiveVisualState.SUCCESS].
 * 14. По умолчанию -> [DavidLiveVisualState.IDLE_LISTENING].
 */
object DavidLiveVisualStateResolver {

    const val SPEECH_PAUSE_HOLD_MS = 420L
    const val SUCCESS_PULSE_DURATION_MS = 1800L

    fun resolve(
        connectionState: DavidLiveConnectionState,
        activeSpeaker: DavidLiveSpeaker,
        overallEnergy: Float = 0f,
        isMicMuted: Boolean = false,
        isSessionReady: Boolean = true,
        isToolLookupActive: Boolean = false,
        activeToolName: String? = null,
        isAnalyzingToolData: Boolean = false,
        isPreparingResponse: Boolean = false,
        isModelThinking: Boolean = false,
        isPlaybackDrained: Boolean = true,
        isWithinUserSpeechHold: Boolean = false,
         isWithinDavidSpeechHold: Boolean = false,
        isSuccessPulseActive: Boolean = false,
        failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE,
        debugOverrideState: DavidLiveVisualState? = null
    ): DavidLiveVisualState {
        if (debugOverrideState != null) {
            return debugOverrideState
        }

        // 1. Отсутствие API-ключа (никогда не запускает бесконечный reconnect)
        if (connectionState == DavidLiveConnectionState.NO_API_KEY ||
            failureKind == DavidLiveConnectionFailureKind.MISSING_API_KEY
        ) {
            return DavidLiveVisualState.API_MISSING
        }

        // 2. Потеря соединения (Offline) имеет приоритет над Thinking, Finance Lookup, Analyzing и Speaking
        if (connectionState == DavidLiveConnectionState.OFFLINE ||
            (connectionState == DavidLiveConnectionState.ERROR &&
                failureKind == DavidLiveConnectionFailureKind.NETWORK_OFFLINE)
        ) {
            return DavidLiveVisualState.OFFLINE
        }

        // 3. Отдельное состояние ошибки (неверный API-ключ, отказ доступа, ошибка протокола)
        if (connectionState == DavidLiveConnectionState.ERROR) {
            return DavidLiveVisualState.ERROR
        }

        // 4. Подключение и переподключение (Ready не ставится только по onOpen — ждём подтверждения сессии)
        if (connectionState == DavidLiveConnectionState.RECONNECTING) {
            return DavidLiveVisualState.RECONNECTING
        }
        if (connectionState == DavidLiveConnectionState.CONNECTING ||
            (connectionState != DavidLiveConnectionState.IDLE &&
                connectionState != DavidLiveConnectionState.ENDED &&
                !isSessionReady)
        ) {
            return DavidLiveVisualState.CONNECTING
        }

        // 5. Говорит Давид (только при реальном воспроизведении аудио или в коротком окне удержания между фразами)
        val davidSpeakingNow = activeSpeaker == DavidLiveSpeaker.DAVID ||
            (connectionState == DavidLiveConnectionState.DAVID_SPEAKING && !isPlaybackDrained) ||
            isWithinDavidSpeechHold
        if (davidSpeakingNow) {
            return DavidLiveVisualState.DAVID_SPEAKING
        }

        // 6. Пользователь говорит (с защитой от сброса на коротких паузах между словами)
        val userSpeakingNow = !isMicMuted && (
            activeSpeaker == DavidLiveSpeaker.USER ||
                connectionState == DavidLiveConnectionState.USER_SPEAKING ||
                isWithinUserSpeechHold ||
                (overallEnergy > 0.20f && connectionState == DavidLiveConnectionState.LISTENING)
            )
        if (userSpeakingNow) {
            return DavidLiveVisualState.USER_SPEAKING
        }

        // 7. Поиск финансовых данных (СТРОГО только когда реально запущен финансовый инструмент!)
        if (isToolLookupActive ||
            !activeToolName.isNullOrBlank() ||
            connectionState == DavidLiveConnectionState.FINANCE_LOOKUP
        ) {
            return DavidLiveVisualState.FINANCE_LOOKUP
        }

        // 8. Анализ полученных финансовых данных
        if (isAnalyzingToolData || connectionState == DavidLiveConnectionState.ANALYZING) {
            return DavidLiveVisualState.ANALYZING
        }

        // 9. Подготовка ответа (плавный переход от анализа к речи Давида)
        if (isPreparingResponse || connectionState == DavidLiveConnectionState.PREPARING_RESPONSE) {
            return DavidLiveVisualState.PREPARING_RESPONSE
        }

        // 10. Давид думает (обработка пользовательского запроса до ответа)
        if (isModelThinking || connectionState == DavidLiveConnectionState.DAVID_THINKING) {
            return DavidLiveVisualState.THINKING
        }

        // 11. Кратковременный импульс успешного завершения
        if (isSuccessPulseActive || connectionState == DavidLiveConnectionState.SUCCESS) {
            return DavidLiveVisualState.SUCCESS
        }

        // 12. Спокойное ожидание и прослушивание
        return DavidLiveVisualState.IDLE_LISTENING
    }

    fun resolveMetadata(
        visualState: DavidLiveVisualState,
        isMicMuted: Boolean = false,
        activeToolName: String? = null,
        errorMessage: String? = null,
        failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE,
        liveSubtitle: String? = null
    ): DavidLiveVisualMetadata {
        val customSpokenSubtitle = liveSubtitle?.trim()?.takeIf {
            it.isNotBlank() &&
                it != "Говорите, я слушаю..." &&
                it != "Слушаю вас..." &&
                it != "Слушаю тебя..." &&
                it != "Микрофон отключён" &&
                it != "Давид отвечает..." &&
                it != "Подключаюсь к Давиду..."
        }
        return when (visualState) {
            DavidLiveVisualState.IDLE_LISTENING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = if (isMicMuted) "Микрофон выключен" else "Ожидание",
                subtitle = if (isMicMuted) {
                    "Нажми на кнопку микрофона, чтобы продолжить разговор"
                } else {
                    customSpokenSubtitle ?: "Я здесь, слушаю тебя"
                },
                badgeText = "LIVE",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.USER_SPEAKING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Ты говоришь",
                subtitle = customSpokenSubtitle ?: "Слушаю...",
                badgeText = "LIVE",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.THINKING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Думаю...",
                subtitle = liveSubtitle?.takeIf { it.isNotBlank() && it != "Говорите, я слушаю..." } ?: "Анализирую запрос",
                badgeText = "THINKING",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.FINANCE_LOOKUP -> {
                val toolHint = when (activeToolName) {
                    DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW ->
                        "Ищу сводку по балансу, доходам и расходам..."
                    DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST ->
                        "Рассчитываю безопасный лимит трат Safe-to-Spend..."
                    DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF ->
                        "Моделирую финансовый сценарий «Что если»..."
                    DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING ->
                        "Проверяю расходы и лимиты по категориям..."
                    DavidLiveFinancialToolsProvider.TOOL_GOALS_AND_DEBTS ->
                        "Проверяю цели накоплений и долговые обязательства..."
                    DavidLiveFinancialToolsProvider.TOOL_COMPARE_PERIODS ->
                        "Сравниваю показатели между периодами..."
                    DavidLiveFinancialToolsProvider.TOOL_RECENT_TRANSACTIONS ->
                        "Загружаю последние финансовые операции..."
                    else -> "Ищу данные о твоих доходах и расходах..."
                }
                DavidLiveVisualMetadata(
                    visualState = visualState,
                    title = "Ищу финансы",
                    subtitle = toolHint,
                    badgeText = "SCAN",
                    actionType = DavidLiveVisualActionType.NONE,
                    actionLabel = null,
                    sphereTapTriggersReconnect = false,
                    sphereTapOpensApiSettings = false
                )
            }

            DavidLiveVisualState.ANALYZING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Анализирую",
                subtitle = "Проверяю цифры и составляю выводы...",
                badgeText = "ANALYZE",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.PREPARING_RESPONSE -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Готовлю ответ",
                subtitle = "Формулирую выводы по твоим финансам...",
                badgeText = "READY",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.DAVID_SPEAKING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Говорит Давид",
                subtitle = liveSubtitle?.takeIf { it.isNotBlank() } ?: "Вот что я думаю...",
                badgeText = "LIVE",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.CONNECTING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Подключение",
                subtitle = "Устанавливаю соединение с Gemini...",
                badgeText = "CONNECT",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.RECONNECTING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Переподключение",
                subtitle = "Восстанавливаю защищённый голосовой канал...",
                badgeText = "RECONNECT",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.OFFLINE -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Связь прервалась",
                subtitle = errorMessage?.takeIf { it.isNotBlank() }
                    ?: liveSubtitle?.takeIf { it.isNotBlank() && it != "Говорите, я слушаю..." }
                    ?: "Проверь интернет и нажми на сферу или кнопку ниже",
                badgeText = "OFFLINE",
                actionType = DavidLiveVisualActionType.RECONNECT,
                actionLabel = "Повторить",
                sphereTapTriggersReconnect = true,
                sphereTapOpensApiSettings = false
            )

            DavidLiveVisualState.API_MISSING -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Нет API-ключа",
                subtitle = "Добавь API-ключ Gemini для продолжения",
                badgeText = "API KEY",
                actionType = DavidLiveVisualActionType.ADD_API_KEY,
                actionLabel = "Добавить API",
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = true
            )

            DavidLiveVisualState.ERROR -> {
                val isInvalidKey = failureKind == DavidLiveConnectionFailureKind.INVALID_API_KEY ||
                    (errorMessage?.contains("API", ignoreCase = true) == true &&
                        (errorMessage.contains("ключ", ignoreCase = true) ||
                            errorMessage.contains("key", ignoreCase = true) ||
                            errorMessage.contains("403") ||
                            errorMessage.contains("401")))
                DavidLiveVisualMetadata(
                    visualState = visualState,
                    title = if (isInvalidKey) "Неверный API-ключ" else "Ошибка",
                    subtitle = errorMessage?.takeIf { it.isNotBlank() }
                        ?: liveSubtitle?.takeIf { it.isNotBlank() && it != "Говорите, я слушаю..." }
                        ?: "Что-то пошло не так. Попробуй ещё раз.",
                    badgeText = "ERROR",
                    actionType = if (isInvalidKey) {
                        DavidLiveVisualActionType.UPDATE_API_KEY
                    } else {
                        DavidLiveVisualActionType.RETRY_ERROR
                    },
                    actionLabel = if (isInvalidKey) "Настроить API" else "Повторить",
                    sphereTapTriggersReconnect = !isInvalidKey,
                    sphereTapOpensApiSettings = isInvalidKey
                )
            }

            DavidLiveVisualState.SUCCESS -> DavidLiveVisualMetadata(
                visualState = visualState,
                title = "Готово!",
                subtitle = "Отлично, я всё понял!",
                badgeText = "DONE",
                actionType = DavidLiveVisualActionType.NONE,
                actionLabel = null,
                sphereTapTriggersReconnect = false,
                sphereTapOpensApiSettings = false
            )
        }
    }
}

/**
 * Участник диалога в David Live.
 */
enum class DavidLiveSpeaker {
    NONE,
    USER,
    DAVID,
    SYSTEM
}

/**
 * Реплика диалога, зафиксированная в ходе Live-сессии (из аудио-транскрипции или текста модели).
 */
@Immutable
data class DavidLiveTranscriptTurn(
    val id: String = UUID.randomUUID().toString(),
    val speaker: DavidLiveSpeaker,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isFinal: Boolean = true
)

/**
 * Проверенный финансовый факт, полученный через контролируемый вызов функции приложения (Tool Call).
 * Используется для предотвращения галлюцинаций как во время диалога, так и при генерации резюме.
 */
@Immutable
data class DavidLiveToolFact(
    val toolName: String,
    val summary: String = "",
    val argsJson: String = "",
    val resultJson: String = "",
    val humanReadableFact: String = summary,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Структурированное резюме состоявшегося разговора David Live.
 */
@Immutable
data class DavidLiveSummary(
    val sessionId: String = UUID.randomUUID().toString(),
    val durationSeconds: Int = 0,
    val topics: List<String> = emptyList(),
    val keyFindings: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val nextActions: List<String> = emptyList(),
    val verifiedFactsUsed: List<String> = emptyList()
)

/**
 * Результат классификации жеста на кнопке отправки сообщения в чате Давида.
 */
enum class SendButtonGestureAction {
    NONE,
    SHORT_TAP_SEND,
    LONG_PRESS_START_LIVE
}

/**
 * Чистый детерминированный классификатор короткого нажатия и удержания (550 мс)
 * на существующей кнопке отправки сообщения.
 *
 * Гарантирует:
 * - Короткое нажатие (< 550 мс) при непустом тексте отправляет сообщение.
 * - Удержание (>= 550 мс) запускает David Live, вызывает haptic feedback,
 *   НЕ отправляет и НЕ очищает уже набранный в поле текст, а также блокирует
 *   случайную отправку сообщения при отпускании пальца после долгого нажатия.
 */
object DavidSendButtonGestureHelper {
    const val LONG_PRESS_DURATION_MS = 550L

    data class GestureEvaluation(
        val action: SendButtonGestureAction,
        val shouldTriggerHaptic: Boolean,
        val resultingDraftText: String,
        val messageSent: String?
    )

    /**
     * Вычисляет результат жеста по длительности удержания и текущему тексту в поле ввода.
     */
    fun evaluatePress(
        pressDurationMs: Long,
        currentDraftText: String,
        wasCancelled: Boolean = false
    ): GestureEvaluation {
        if (wasCancelled) {
            return GestureEvaluation(
                action = SendButtonGestureAction.NONE,
                shouldTriggerHaptic = false,
                resultingDraftText = currentDraftText,
                messageSent = null
            )
        }

        return if (pressDurationMs >= LONG_PRESS_DURATION_MS) {
            // Долгое нажатие: запускаем David Live, сохраняем набранный текст нетронутым, ничего не отправляем
            GestureEvaluation(
                action = SendButtonGestureAction.LONG_PRESS_START_LIVE,
                shouldTriggerHaptic = true,
                resultingDraftText = currentDraftText,
                messageSent = null
            )
        } else {
            // Короткое нажатие: отправляем только если текст не пустой
            val trimmed = currentDraftText.trim()
            if (trimmed.isNotEmpty()) {
                GestureEvaluation(
                    action = SendButtonGestureAction.SHORT_TAP_SEND,
                    shouldTriggerHaptic = false,
                    resultingDraftText = "",
                    messageSent = currentDraftText
                )
            } else {
                GestureEvaluation(
                    action = SendButtonGestureAction.NONE,
                    shouldTriggerHaptic = false,
                    resultingDraftText = currentDraftText,
                    messageSent = null
                )
            }
        }
    }
}
