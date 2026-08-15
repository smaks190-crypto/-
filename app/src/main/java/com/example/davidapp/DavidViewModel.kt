package com.example.davidapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel для управления состоянием чата с финансовым консультантом Давидом Жабовым.
 */
class DavidViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _stage = MutableStateFlow(DavidStage.INITIAL)
    val stage = _stage.asStateFlow()

    private val _isDavidTyping = MutableStateFlow(false)
    val isDavidTyping = _isDavidTyping.asStateFlow()

    init {
        initInitialGreeting()
    }

    private fun currentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun initInitialGreeting() {
        _messages.value = listOf(
            ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    "Ква-ква! Я твой кибер-ассистент Давид 🐸.\n" +
                    "Готов провести аудит бюджета, проанализировать транзакции и составить отчет. Что требуется сделать?"
                ),
                timestamp = currentTime(),
                isUser = false
            )
        )
    }

    /**
     * Обработка быстрых действий
     */
    fun handleAction(action: String, soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playSend()

            val userText = when (action) {
                "START", "MAKE_REPORT" -> "Давид, сделай финансовый отчет"
                "AUDIT" -> "Проведи экспресс-аудит расходов"
                "GOALS" -> "Как продвигается накопление на цели?"
                else -> "Давид, сформируй финансовую сводку"
            }

            _messages.value = _messages.value + ChatMessage(
                sender = "Вы",
                type = ChatMessageType.Text(userText),
                timestamp = currentTime(),
                isUser = true
            )

            _stage.value = DavidStage.PROCESSING
            _isDavidTyping.value = true

            delay(1200)

            _isDavidTyping.value = false
            _stage.value = DavidStage.FILE_SELECTION
            soundManager?.playReceive()

            _messages.value = _messages.value + ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    "Принято в обработку! За какой временной период сформировать подробную выписку и аналитику?"
                ),
                timestamp = currentTime(),
                isUser = false
            )
        }
    }

    /**
     * Обработка выбора периода и генерация интерактивного отчета и графика
     */
    fun processFile(period: String, soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playSend()

            val periodFormatted = when (period.lowercase()) {
                "день", "day" -> "День"
                "неделя", "week" -> "Неделя"
                "месяц", "month" -> "Месяц"
                "год", "year" -> "Год"
                else -> period.replaceFirstChar { it.uppercase() }
            }

            val fileName = "Выписка_Финансы_${periodFormatted}.pdf"
            val fileSize = when (periodFormatted) {
                "День" -> "1.2 MB"
                "Неделя" -> "2.4 MB"
                "Месяц" -> "4.8 MB"
                "Год" -> "12.3 MB"
                else -> "3.1 MB"
            }

            // Добавляем файл запроса от пользователя
            _messages.value = _messages.value + ChatMessage(
                sender = "Вы",
                type = ChatMessageType.File(
                    name = fileName,
                    size = fileSize,
                    extension = "PDF"
                ),
                timestamp = currentTime(),
                isUser = true
            )

            _stage.value = DavidStage.PROCESSING
            _isDavidTyping.value = true

            delay(1600)

            _isDavidTyping.value = false
            soundManager?.playReceive()

            val (income, expense, delta, dataPoints, analysisText) = when (periodFormatted) {
                "День" -> Tuple5(
                    12500.0, 4200.0, 18.5,
                    listOf(15f, 25f, 40f, 30f, 65f, 85f),
                    "За сегодня чистый профицит составил +8 300 ₽. Основные траты пришлись на транспорт и обед. Дневной лимит соблюден на 100%!"
                )
                "Неделя" -> Tuple5(
                    65000.0, 31200.0, 24.0,
                    listOf(20f, 35f, 45f, 60f, 50f, 75f, 90f),
                    "Недельный баланс стабилен: норма сбережений 52%. Расходы на кофе и такси снизились на 14% относительно прошлой недели."
                )
                "Месяц" -> Tuple5(
                    240000.0, 142000.0, 15.2,
                    listOf(25f, 40f, 35f, 70f, 60f, 85f, 78f, 95f),
                    "Отличный месяц! Сформирован резерв в 98 000 ₽. Категории «Обязательные счета» и «Инвестиции» полностью закрыты."
                )
                "Год" -> Tuple5(
                    2850000.0, 1680000.0, 31.4,
                    listOf(30f, 45f, 55f, 65f, 70f, 80f, 75f, 90f, 85f, 95f, 92f, 100f),
                    "Годовой аудит завершен: общий капитал вырос на 31.4%! Накоплено 1 170 000 ₽, цели по пассивному доходу перевыполнены."
                )
                else -> Tuple5(
                    150000.0, 90000.0, 12.0,
                    listOf(30f, 50f, 40f, 70f, 60f, 85f),
                    "Финансовый отчет за выбранный период составлен без замечаний. Рисков кассового разрыва не обнаружено."
                )
            }

            // Добавляем сообщение с диаграммой и итогом
            _messages.value = _messages.value + ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Chart(
                    title = "Аналитический срез: $periodFormatted",
                    summary = analysisText,
                    income = income,
                    expense = expense,
                    deltaPercent = delta,
                    dataPoints = dataPoints
                ),
                timestamp = currentTime(),
                isUser = false
            )

            _stage.value = DavidStage.FOLLOW_UP
        }
    }

    /**
     * Отправка произвольного текстового сообщения
     */
    fun sendTextMessage(text: String, soundManager: SoundManager? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            soundManager?.playSend()

            _messages.value = _messages.value + ChatMessage(
                sender = "Вы",
                type = ChatMessageType.Text(text.trim()),
                timestamp = currentTime(),
                isUser = true
            )

            _isDavidTyping.value = true
            val prevStage = _stage.value
            _stage.value = DavidStage.PROCESSING

            delay(1400)

            _isDavidTyping.value = false
            soundManager?.playReceive()

            val reply = generateSmartDavidReply(text.trim())

            _messages.value = _messages.value + ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(reply),
                timestamp = currentTime(),
                isUser = false
            )

            _stage.value = if (prevStage == DavidStage.INITIAL) DavidStage.FILE_SELECTION else prevStage
        }
    }

    /**
     * Генерация ответа ассистента на свободный текст
     */
    private fun generateSmartDavidReply(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("привет") || q.contains("здравствуй") || q.contains("ку") ->
                "Приветствую! Чем могу помочь с твоим бюджетом сегодня? Могу сделать аудит или построить график доходности. 🐸"
            q.contains("отчет") || q.contains("аудит") || q.contains("баланс") ->
                "Запустил диагностику! Выбери период выше или прикрепи нужный документ для мгновенного парсинга."
            q.contains("цели") || q.contains("накоп") || q.contains("копить") ->
                "Твоя финансовая подушка безопасности на 78% сформирована. Рекомендую перевести 10% от последнего дохода в резервный фонд."
            q.contains("спасибо") || q.contains("благодар") ->
                "Всегда к вашим услугам! Держим курс на финансовую независимость 🚀"
            else ->
                "Запрос «$query» принят во внимание. Рекомендую придерживаться правила 50/30/20 и отслеживать категории с наибольшей волатильностью расходов."
        }
    }

    /**
     * Экспорт отчета в PDF
     */
    fun exportPdf(soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playClick()
            _messages.value = _messages.value + ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    "📄 Отчет успешно экспортирован в защищенный PDF с цифровой подписью.\nФайл сохранен в хранилище устройства."
                ),
                timestamp = currentTime(),
                isUser = false
            )
        }
    }

    /**
     * Сброс диалога для нового аудита
     */
    fun resetSession(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        _stage.value = DavidStage.INITIAL
        initInitialGreeting()
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
