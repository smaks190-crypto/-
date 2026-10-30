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
 */
enum class DavidLiveConnectionState {
    IDLE,
    CONNECTING,
    LISTENING,
    USER_SPEAKING,
    DAVID_THINKING,
    DAVID_SPEAKING,
    MUTED,
    RECONNECTING,
    NO_API_KEY,
    ERROR,
    ENDED
}

/**
 * Визуальная фаза анимации состояния подключения David Live,
 * синхронизированная с фактическим состоянием WebSocket-сессии и сети.
 */
enum class DavidLiveVisualPhase {
    IDLE,
    INITIAL_CONNECTING,
    CONNECTION_LOST,
    RECONNECTING,
    READY_FLASH,
    CONVERSATION_ACTIVE
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
    val isFinal: Boolean = true,
    val wasInterrupted: Boolean = false
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
