package com.example.davidapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel для управления состоянием чата с Давидом Жабовым.
 * Обеспечивает объединение неонового интерактивного графика и текстового саркастического аудита.
 */
class DavidViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _stage = MutableStateFlow(DavidStage.INITIAL)
    val stage: StateFlow<DavidStage> = _stage.asStateFlow()

    private val _isDavidTyping = MutableStateFlow(false)
    val isDavidTyping: StateFlow<Boolean> = _isDavidTyping.asStateFlow()

    private var currentProfileName: String = "Максим"

    init {
        initInitialGreeting()
    }

    fun setProfileName(name: String) {
        if (name.isNotBlank() && name != currentProfileName) {
            currentProfileName = name
            if (_messages.value.size <= 1 && _stage.value == DavidStage.INITIAL) {
                initInitialGreeting()
            }
        }
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /**
     * Начальное приветствие от Давида с персонализацией по имени профиля
     */
    private fun initInitialGreeting() {
        _messages.value = listOf(
            ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    "Салют, $currentProfileName! Я **Давид Жабов** — твой персональный финансовый аудитор и безжалостный критик транжирства.\n\n" +
                    "Готов провести жесткий разбор трат, прожарить нелепые расходы и выдать сочные ачивки. Выбери период или нажми **«🐸 Давид, сделай отчет»**!"
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

            when (action) {
                "START", "MAKE_REPORT" -> {
                    _messages.value = _messages.value + ChatMessage(
                        sender = "Вы",
                        type = ChatMessageType.Text("Давид, сделай полный финансовый отчет"),
                        timestamp = currentTime(),
                        isUser = true
                    )

                    _stage.value = DavidStage.PROCESSING
                    _isDavidTyping.value = true
                    delay(1000)

                    _isDavidTyping.value = false
                    _stage.value = DavidStage.FILE_SELECTION
                    soundManager?.playReceive()

                    _messages.value = _messages.value + ChatMessage(
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(
                            "Отличная идея. За какой временной период сформировать выписку, график и саркастический разбор?"
                        ),
                        timestamp = currentTime(),
                        isUser = false
                    )
                }
                "AUDIT" -> {
                    // Мгновенный экспресс-аудит
                    _messages.value = _messages.value + ChatMessage(
                        sender = "Вы",
                        type = ChatMessageType.Text("⚡ Проведи экспресс-аудит расходов"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    processFile("Месяц", soundManager)
                }
                "GOALS" -> {
                    _messages.value = _messages.value + ChatMessage(
                        sender = "Вы",
                        type = ChatMessageType.Text("Как продвигаются финансовые цели?"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    _isDavidTyping.value = true
                    delay(1200)
                    _isDavidTyping.value = false
                    soundManager?.playReceive()

                    _messages.value = _messages.value + ChatMessage(
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(
                            "🎯 **Прогресс по целям:**\n\n" +
                            "- 🛡️ **Подушка безопасности:** 78% (накоплено 156 000 ₽ из 200 000 ₽)\n" +
                            "- 🏖️ **Отпуск мечты:** 45% (накоплено 45 000 ₽ из 100 000 ₽)\n\n" +
                            "💡 *Совет Жабова:* Если перестанешь заказывать кофе навынос дважды в день, подушка безопасности закроется на 3 недели раньше!"
                        ),
                        timestamp = currentTime(),
                        isUser = false
                    )
                }
                else -> {
                    _messages.value = _messages.value + ChatMessage(
                        sender = "Вы",
                        type = ChatMessageType.Text("Давид, сформируй финансовую сводку"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    processFile("Неделя", soundManager)
                }
            }
        }
    }

    /**
     * Обработка выбора периода: объединяет интерактивный график И текстовый саркастический отчет
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

            // Добавляем сообщение-запрос с файлом от пользователя
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

            val auditData = getAuditDataForPeriod(periodFormatted)
            val sections = splitReportIntoSections(auditData.sarcasticReport)

            // 1. Отправляем каждую секцию отчета (заголовок + содержание) отдельным баблом
            for (section in sections) {
                _isDavidTyping.value = true
                delay(650)
                _isDavidTyping.value = false
                soundManager?.playReceive()

                _messages.value = _messages.value + ChatMessage(
                    sender = "Давид Жабов 🐸",
                    type = ChatMessageType.Text(section),
                    timestamp = currentTime(),
                    isUser = false
                )
                delay(200)
            }

            // 2. В конце отправляем карточку интерактивного графика как визуальное подтверждение
            _isDavidTyping.value = true
            delay(800)
            _isDavidTyping.value = false
            soundManager?.playReceive()

            _messages.value = _messages.value + ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Chart(
                    title = "Динамика баланса: $periodFormatted",
                    summary = auditData.chartSummary,
                    income = auditData.income,
                    expense = auditData.expense,
                    deltaPercent = auditData.deltaPercent,
                    dataPoints = auditData.dataPoints
                ),
                timestamp = currentTime(),
                isUser = false
            )

            _stage.value = DavidStage.FOLLOW_UP
        }
    }

    /**
     * Разделение отчета по смысловым блокам/заголовкам Markdown для раздельных баблов
     */
    private fun splitReportIntoSections(report: String): List<String> {
        if (report.isBlank()) return emptyList()
        val lines = report.lines()
        val sections = mutableListOf<String>()
        val currentSection = StringBuilder()

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
                if (currentSection.isNotBlank()) {
                    sections.add(currentSection.toString().trim())
                    currentSection.clear()
                }
            }
            currentSection.appendLine(line)
        }
        if (currentSection.isNotBlank()) {
            sections.add(currentSection.toString().trim())
        }
        return if (sections.isNotEmpty()) sections else listOf(report.trim())
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

            delay(1300)

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
     * Генерация саркастических и информативных ответов Давида на свободный ввод
     */
    private fun generateSmartDavidReply(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("привет") || q.contains("здравствуй") || q.contains("ку") || q.contains("салют") ->
                "И тебе привет! Кошелек в порядке или снова спасаем твой баланс от импульсивных заказов? Выбери период или нажми на кнопки внизу. 🐸"

            q.contains("отчет") || q.contains("аудит") || q.contains("график") || q.contains("прожар") ->
                "Так-так, чую запах незапланированных трат! Нажми на нужный период (**День**, **Неделя**, **Месяц**, **Год**) выше, и я выкачу детальный разбор с графиком и ачивками."

            q.contains("кофе") || q.contains("доставк") || q.contains("еда") || q.contains("ресторан") ->
                "Ага! Категория «Спонсирование рестораторов» обнаружена. Если сложить все твои чеки на латте и пиццу, можно было купить акции этой кофейни. Держи аппетиты в узде!"

            q.contains("деньги") || q.contains("копить") || q.contains("накоп") || q.contains("вклад") ->
                "Золотое правило Жабова: сначала плати себе (минимум 15-20% на накопительный счет), а уже потом корми маркетологов маркетплейсов. 🚀"

            q.contains("спасибо") || q.contains("спс") || q.contains("красав") ->
                "Пожалуйста! Мой сарказм бесплатен, а вот сэкономленные деньги — бесценны. Работаем дальше! 🐸✨"

            else ->
                "Запрос принят. Мой вердикт: держи дельту положительной, режь мелкие подписки и заглядывай в отчеты регулярно. Готов провести аудит за любой период!"
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
                    "📄 **Отчет и график экспортированы в PDF!**\n\n" +
                    "Файл сохранен с цифровой печатью Давида Жабова. Можешь распечатать и повесить на холодильник как напоминание о финансовой дисциплине."
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

    /**
     * Генератор данных и саркастического отчета по периодам
     */
    private fun getAuditDataForPeriod(period: String): AuditPeriodData {
        return when (period) {
            "День" -> AuditPeriodData(
                income = 12500.0,
                expense = 4200.0,
                deltaPercent = 18.5,
                dataPoints = listOf(15f, 25f, 40f, 30f, 65f, 85f),
                chartSummary = "Дневной профицит: +8 300 ₽. Лимит соблюден на 100%.",
                sarcasticReport = """
# Главный Вердикт: 🚨 ТАКТИЧЕСКИЙ РАЗГУЛ
Ты сегодня потратил **4 200 ₽** при заработанных **12 500 ₽**. Вроде бы в плюсе, но давай посмотрим правде в глаза: кофе навынос и доставка суши — это не инвестиции в основной капитал.

## Цифры и Динамика
- Доходы за день: **+12 500 ₽**
- Траты за день: **-4 200 ₽**
- Чистый остаток: **+8 300 ₽** (норма сбережений 66%)

## Прожарка Транжиры 🔥
Как писал Гоголь в «Мёртвых душах», Манилов тоже строил хрустальные мосты до обеда. Твои микротранзакции на фастфуд и снеки суммарно выглядят как спонсирование местной кофейни на пороге дефолта. Баланс спасен только потому, что ты не зашел на маркетплейс перед сном.

## Ачивки и Достижения 🏆
🏆 **Купеческий разгул** — спустил 35% дневного дохода на импульсивный обед
🥇 **Кофейный барон** — переплата за латте на овсяном молоке превысила ставку ЦБ

## Рекомендации Жабова 💡
Завтра обедай дома, а сэкономленную тысячу переведи на накопительный счет, пока она не растворилась в тарифе «Комфорт Плюс».
""".trimIndent()
            )

            "Неделя" -> AuditPeriodData(
                income = 65000.0,
                expense = 31200.0,
                deltaPercent = 24.0,
                dataPoints = listOf(20f, 35f, 45f, 60f, 50f, 75f, 90f),
                chartSummary = "Недельный баланс: +33 800 ₽. Норма сбережений 52%.",
                sarcasticReport = """
# Главный Вердикт: 📈 УМЕРЕННЫЙ ПРОФИЦИТ
Недельный результат изменился в **ЛУЧШУЮ** сторону (+24.0% к сбережениям). Но без драмы не обошлось: пятница чуть не пустила весь недельный бюджет под откос.

## Цифры и Динамика
- Доходы недели: **+65 000 ₽**
- Расходы недели: **-31 200 ₽**
- Свободный денежный поток: **+33 800 ₽**

## Прожарка Транжиры 🔥
Шекспировская трагедия развернулась в пятницу вечером: категория «Развлечения и рестораны» унесла треть бюджета. Ты распоряжался деньгами так, будто завтра революция 1917 года и накопления все равно национализируют. Хорошо хоть в понедельник включился режим аскезы по Раскольникову.

## Ачивки и Достижения 🏆
🏆 **Пятничный кутила** — закрыл счет за компанию в надежде на кэшбэк
🏅 **Мастер кассового маневра** — чудом не влез в кредитку к воскресенью

## Рекомендации Жабова 💡
Установи жесткий лимит на уикенд. Переведи 50% профицита (16 900 ₽) на вклад с капитализацией уже сегодня.
""".trimIndent()
            )

            "Месяц" -> AuditPeriodData(
                income = 240000.0,
                expense = 142000.0,
                deltaPercent = 15.2,
                dataPoints = listOf(25f, 40f, 35f, 70f, 60f, 85f, 78f, 95f),
                chartSummary = "Месячный чистый резерв: +98 000 ₽ (+15.2%).",
                sarcasticReport = """
# Главный Вердикт: 👑 ФИНАНСОВЫЙ ТРИУМФ
Месячный аудит закрыт с вердиктом: **ЛУЧШАЯ** динамика (+15.2% прироста). Капитал сбережен, хотя маркетплейсы отчаянно пытались тебя разорить.

## Цифры и Динамика
- Доходы за месяц: **+240 000 ₽**
- Расходы за месяц: **-142 000 ₽**
- Чистая прибыль: **+98 000 ₽** (норма сбережений 40.8%)

## Прожарка Транжиры 🔥
Пять доставок пиццы, спонтанный робот-пылесос и 12 подписок на сервисы, которые ты открывал один раз в жизни! Твой бюджет пережил набег почище Золотой Орды. Но благодаря своевременному закрытию обязательных платежей, кассового разрыва удалось избежать.

## Ачивки и Достижения 🏆
🏆 **Спонсор маркетплейсов** — 14 покупок категории «очень надо, потом разберусь»
🥇 **Выживший в распродажах** — сохранил почти 100 000 ₽ чистыми вопреки скидкам

## Рекомендации Жабова 💡
Отмени неиспользуемые автоподписки (экономия ~2 400 ₽/мес) и отправь 98 000 ₽ в целевой фонд подушки безопасности.
""".trimIndent()
            )

            "Год" -> AuditPeriodData(
                income = 2850000.0,
                expense = 1680000.0,
                deltaPercent = 31.4,
                dataPoints = listOf(30f, 45f, 55f, 65f, 70f, 80f, 75f, 90f, 85f, 95f, 92f, 100f),
                chartSummary = "Годовой капитал вырос на +31.4%! Накоплено 1 170 000 ₽.",
                sarcasticReport = """
# Главный Вердикт: 🚀 КАПИТАЛИСТИЧЕСКИЙ ЛЕВ
Годовой результат изменился в **ЛУЧШУЮ** сторону (+31.4% прироста капитала)! Ты не просто выжил в эпоху инфляции, но и приумножил баланс на зависть Уоррену Баффету.

## Цифры и Динамика
- Совокупный доход: **+2 850 000 ₽**
- Совокупный расход: **-1 680 000 ₽**
- Сформированный капитал: **+1 170 000 ₽**

## Прожарка Транжиры 🔥
За год ты прошел путь от графа Монте-Кристо в день зарплаты до бедного чиновника Акакия Акакиевича перед крупными платежами. Расходы на импульсивные гаджеты и такси сравнимы с бюджетом небольшой средневековой фактории в период Тюльпаномании. Однако железная дисциплина во втором полугодии совершила чудо!

## Ачивки и Достижения 🏆
🏆 **Магнат на максималках** — преодолел отметку в 1.1 млн чистых сбережений
🥇 **Укротитель импульсов** — устоял перед 100+ маркетинговыми акциями

## Рекомендации Жабова 💡
Диверсифицируй накопленный 1.17 млн ₽: часть во вклады, часть на долгосрочные цели, и не забудь порадовать Давида Жабова регулярным аудитом!
""".trimIndent()
            )

            else -> AuditPeriodData(
                income = 150000.0,
                expense = 90000.0,
                deltaPercent = 12.0,
                dataPoints = listOf(30f, 50f, 40f, 70f, 60f, 85f),
                chartSummary = "Баланс стабилен: +60 000 ₽.",
                sarcasticReport = """
# Главный Вердикт: ⚖️ СТАБИЛЬНЫЙ БАЛАНС
Финансовый срез показывает уверенный плюс: доходы превышают расходы на **60 000 ₽**.

## Цифры и Динамика
- Доходы: **+150 000 ₽**
- Расходы: **-90 000 ₽**

## Прожарка Транжиры 🔥
Серьезных финансовых преступлений не обнаружено, хотя мелкие утечки на снеки и фастфуд присутствуют. 

## Рекомендации Жабова 💡
Продолжай вести учет и оптимизировать регулярные платежи!
""".trimIndent()
            )
        }
    }
}

private data class AuditPeriodData(
    val income: Double,
    val expense: Double,
    val deltaPercent: Double,
    val dataPoints: List<Float>,
    val chartSummary: String,
    val sarcasticReport: String
)
