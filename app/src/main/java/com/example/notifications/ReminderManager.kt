package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

enum class ReminderTone(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    SAVAGE_FROG(
        id = "SAVAGE_FROG",
        title = "Дерзкий контролер 🐸",
        subtitle = "Ироничные подколы от Давида Жабова и контроль импульсивных трат",
        badge = "ХИТ"
    ),
    CALM_ANALYST(
        id = "CALM_ANALYST",
        title = "Спокойный аналитик 📊",
        subtitle = "Деловой банковский тон, сухие цифры и остаток лимита",
        badge = "PRO"
    ),
    GOAL_MOTIVATOR(
        id = "GOAL_MOTIVATOR",
        title = "Мотиватор к целям 🎯",
        subtitle = "Фокус на накоплениях, дисциплине и росте капитала",
        badge = "ЦЕЛИ"
    );

    companion object {
        fun fromId(id: String?): ReminderTone {
            return entries.firstOrNull { it.id == id } ?: SAVAGE_FROG
        }
    }
}

enum class AuditorStrictness(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val levelIndex: Int,
    val sampleQuote: String,
    val systemPromptDirective: String
) {
    SOFT_MENTOR(
        id = "SOFT_MENTOR",
        title = "Мягкий наставник 🌱",
        subtitle = "Поддержка без давления, похвала за учёт и бережные советы",
        badge = "ДЗЕН",
        levelIndex = 1,
        sampleQuote = "«Ничего страшного, главное — записали расход! Завтра чуть притормозим с кафе, ква 🌿»",
        systemPromptDirective = "РЕЖИМ ЖЁСТКОСТИ: МЯГКИЙ НАСТАВНИК (Уровень 1/4). " +
            "Общайся тепло, дружелюбно и поддерживающе, с легкой доброй жабьей иронией. " +
            "Не стыди пользователя, а мягко подсказывай, как сохранить баланс и прийти к целям."
    ),
    CALM_ANALYST(
        id = "CALM_ANALYST",
        title = "Спокойный аналитик 📊",
        subtitle = "Холодный расчёт CFO, цифры, проценты и динамика лимитов",
        badge = "PRO",
        levelIndex = 2,
        sampleQuote = "«Отклонение от дневного лимита +18%. Рекомендую отложить необязательные покупки до пятницы.»",
        systemPromptDirective = "РЕЖИМ ЖЁСТКОСТИ: СПОКОЙНЫЙ АНАЛИТИК (Уровень 2/4). " +
            "Общайся в стиле профессионального финансового директора (CFO). " +
            "Делай упор на цифры, рентабельность, долю от бюджета и сухую рациональность без лишних эмоций."
    ),
    SAVAGE_ROAST(
        id = "SAVAGE_ROAST",
        title = "Дерзкий Roast-режим 🐸",
        subtitle = "Фирменный сарказм Жабова, мемы, классическая литература и ирония",
        badge = "ХИТ",
        levelIndex = 3,
        sampleQuote = "«Опять латте на кокосовом при открытой кредитке? Остап Бендер аплодирует стоя, ква!»",
        systemPromptDirective = "РЕЖИМ ЖЁСТКОСТИ: ДЕРЗКИЙ ROAST-РЕЖИМ (Уровень 3/4). " +
            "Используй фирменный сарказм Давида Жабова, сочные интернет-мемы, отсылки к классической литературе и истории. " +
            "Остроумно и иронично подкалывай за импульсивные траты."
    ),
    HARDCORE_TOXIC(
        id = "HARDCORE_TOXIC",
        title = "Хардкорный Ревизор 🔥",
        subtitle = "Беспощадный разнос за каждую лишнюю трату, фастфуд и раздувание долгов",
        badge = "MAX ЖЕСТЬ",
        levelIndex = 4,
        sampleQuote = "«Ты серьёзно спустил 4 500 ₽ на доставку?! Твой бюджет идёт ко дну быстрее Титаника! 🚨»",
        systemPromptDirective = "РЕЖИМ ЖЁСТКОСТИ: ХАРДКОРНЫЙ РЕВИЗОР (Уровень 4/4 — МАКСИМАЛЬНАЯ ЖЁСТКОСТЬ). " +
            "Включай режим безжалостного финансового аудитора! Устраивай жесткий, громкий, сатирический разнос за малейшее расточительство, " +
            "импульсивные покупки, фастфуд или игнорирование долгов и целей. Не жалей транжиру!"
    );

    companion object {
        fun fromId(id: String?): AuditorStrictness {
            return entries.firstOrNull { it.id == id } ?: SAVAGE_ROAST
        }
    }
}

object ReminderManager {
    const val CHANNEL_ID = "daily_budget_reminders"
    const val CHANNEL_NAME = "Напоминания и тревоги Давида"
    const val NOTIFICATION_ID = 1001
    const val CROAK_ALERT_NOTIFICATION_ID = 1002
    const val ACTION_DAILY_REMINDER = "com.example.ACTION_DAILY_REMINDER"

    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_TONE = "reminder_tone"
    private const val KEY_SMART_SUMMARY = "reminder_smart_summary"

    // AI Auditor Strictness & Alert Triggers keys
    private const val KEY_AUDITOR_STRICTNESS = "ai_auditor_strictness"
    private const val KEY_CROAK_ALERT_ENABLED = "ai_croak_alert_enabled"
    private const val KEY_CROAK_THRESHOLD_AMOUNT = "ai_croak_threshold_amount"
    private const val KEY_IMPULSE_CATEGORIES_WATCH = "ai_impulse_categories_watch"
    private const val KEY_NIGHT_WATCH_ENABLED = "ai_night_watch_enabled"
    private const val KEY_AUTO_RECOGNIZE_VOICE = "ai_auto_recognize_voice"
    private const val KEY_VOICE_SENSITIVITY = "ai_voice_sensitivity"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ежедневные напоминания и экстренные триггеры от ИИ-аудитора Давида Жабова"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isReminderEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun getReminderTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_HOUR, 20)
        val minute = prefs.getInt(KEY_MINUTE, 0)
        return Pair(hour, minute)
    }

    fun getReminderTone(context: Context): ReminderTone {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReminderTone.fromId(prefs.getString(KEY_TONE, ReminderTone.SAVAGE_FROG.id))
    }

    fun setReminderTone(context: Context, tone: ReminderTone) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TONE, tone.id).apply()
    }

    fun isSmartSummaryEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SMART_SUMMARY, true)
    }

    fun setSmartSummaryEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SMART_SUMMARY, enabled).apply()
    }

    // --- AI AUDITOR STRICTNESS & TRIGGERS GETTERS/SETTERS ---

    fun getAuditorStrictness(context: Context): AuditorStrictness {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AuditorStrictness.fromId(prefs.getString(KEY_AUDITOR_STRICTNESS, AuditorStrictness.SAVAGE_ROAST.id))
    }

    fun setAuditorStrictness(context: Context, strictness: AuditorStrictness) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val syncedTone = when (strictness) {
            AuditorStrictness.SOFT_MENTOR -> ReminderTone.GOAL_MOTIVATOR
            AuditorStrictness.CALM_ANALYST -> ReminderTone.CALM_ANALYST
            AuditorStrictness.SAVAGE_ROAST, AuditorStrictness.HARDCORE_TOXIC -> ReminderTone.SAVAGE_FROG
        }
        prefs.edit()
            .putString(KEY_AUDITOR_STRICTNESS, strictness.id)
            .putString(KEY_TONE, syncedTone.id)
            .apply()
    }

    fun isCroakAlertEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CROAK_ALERT_ENABLED, true)
    }

    fun setCroakAlertEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CROAK_ALERT_ENABLED, enabled).apply()
    }

    fun getCroakThresholdAmount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CROAK_THRESHOLD_AMOUNT, 3000).coerceIn(500, 100000)
    }

    fun setCroakThresholdAmount(context: Context, amount: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CROAK_THRESHOLD_AMOUNT, amount.coerceIn(500, 100000)).apply()
    }

    fun isImpulseCategoriesWatchEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IMPULSE_CATEGORIES_WATCH, true)
    }

    fun setImpulseCategoriesWatchEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IMPULSE_CATEGORIES_WATCH, enabled).apply()
    }

    fun isNightWatchEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NIGHT_WATCH_ENABLED, true)
    }

    fun setNightWatchEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NIGHT_WATCH_ENABLED, enabled).apply()
    }

    fun isAutoRecognizeVoiceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_RECOGNIZE_VOICE, true)
    }

    fun setAutoRecognizeVoiceEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_RECOGNIZE_VOICE, enabled).apply()
    }

    fun getVoiceSensitivity(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_VOICE_SENSITIVITY, 0.8f).coerceIn(0.1f, 1.0f)
    }

    fun setVoiceSensitivity(context: Context, value: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_VOICE_SENSITIVITY, value.coerceIn(0.1f, 1.0f)).apply()
    }

    fun isImpulseCategory(category: String, subcategory: String): Boolean {
        val text = "$category $subcategory".lowercase(Locale.getDefault())
        val keywords = listOf(
            "фастфуд", "кафе", "ресторан", "доставка", "бургер", "пицц", "шаурм", "кофе",
            "развлеч", "игр", "steam", "казик", "бар", "алко", "такси", "маркетплейс",
            "wildberries", "вайлдберриз", "ozon", "озон", "шопинг", "покупки", "сладост"
        )
        return keywords.any { text.contains(it) }
    }

    fun buildCroakAlertMessage(
        strictness: AuditorStrictness,
        category: String,
        amount: Int,
        threshold: Int,
        isNightTime: Boolean = false
    ): Pair<String, String> {
        val formattedAmount = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(amount)
        val formattedThreshold = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(threshold)
        val nightTag = if (isNightTime) " (Ночной дозор 🌙)" else ""

        return when (strictness) {
            AuditorStrictness.SOFT_MENTOR -> Pair(
                "🌱 Зафиксирована крупная трата: $formattedAmount ₽$nightTag",
                "Категория «$category» превысила ваш порог в $formattedThreshold ₽. Ничего страшного, просто учтём это в плане на неделю!"
            )
            AuditorStrictness.CALM_ANALYST -> Pair(
                "📊 Превышение лимита операции: $formattedAmount ₽$nightTag",
                "Расход в категории «$category» выше установленного порога ($formattedThreshold ₽) на ${((amount - threshold).coerceAtLeast(0) * 100 / threshold.coerceAtLeast(1))}%. Скорректируйте дневной бюджет."
            )
            AuditorStrictness.SAVAGE_ROAST -> Pair(
                "🐸 КВА-ТРЕВОГА! Минус $formattedAmount ₽ за раз!$nightTag",
                "Порог кваканья ($formattedThreshold ₽) пробит расходом на «$category»! Давид Жабов уже достал блокнот для вечерней прожарки."
            )
            AuditorStrictness.HARDCORE_TOXIC -> Pair(
                "🚨 СИРЕНА ЖАБОВА: −$formattedAmount ₽ НА «${category.uppercase()}»!$nightTag",
                "Лимит $formattedThreshold ₽ растоптан в пух и прах! Останови руку с картой, пока твой баланс не превратился в тыкву!"
            )
        }
    }

    fun triggerCroakAlertNotification(
        context: Context,
        category: String,
        amount: Int,
        overrideStrictness: AuditorStrictness? = null,
        overrideThreshold: Int? = null
    ) {
        createNotificationChannel(context)
        val strictness = overrideStrictness ?: getAuditorStrictness(context)
        val threshold = overrideThreshold ?: getCroakThresholdAmount(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = isNightWatchEnabled(context) && (hour >= 23 || hour < 5)

        val (title, body) = buildCroakAlertMessage(strictness, category, amount, threshold, isNight)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "$title\n$body", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CROAK_ALERT_NOTIFICATION_ID, notification)
    }

    fun evaluateAndTriggerCroakAlertIfNeeded(
        context: Context,
        type: String,
        category: String,
        subcategory: String,
        amount: Double
    ): Boolean {
        if (!type.equals("expense", ignoreCase = true)) return false
        if (!isCroakAlertEnabled(context)) return false

        val threshold = getCroakThresholdAmount(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNight = isNightWatchEnabled(context) && (hour >= 23 || hour < 5)
        val isImpulse = isImpulseCategoriesWatchEnabled(context) && isImpulseCategory(category, subcategory)

        val shouldTrigger = when {
            amount >= threshold -> true
            isNight && amount >= (threshold * 0.5).toInt().coerceAtLeast(300) -> true
            isImpulse && amount >= (threshold * 0.7).toInt().coerceAtLeast(500) -> true
            else -> false
        }

        if (shouldTrigger) {
            val label = if (subcategory.isNotBlank() && !subcategory.equals(category, ignoreCase = true)) {
                "$category ($subcategory)"
            } else {
                category.ifBlank { "Расход" }
            }
            triggerCroakAlertNotification(
                context = context,
                category = label,
                amount = amount.roundToLong().toInt(),
                overrideThreshold = threshold
            )
        }
        return shouldTrigger
    }

    fun setReminderEnabled(
        context: Context,
        enabled: Boolean,
        hour: Int = 20,
        minute: Int = 0,
        tone: ReminderTone = getReminderTone(context),
        smartSummary: Boolean = isSmartSummaryEnabled(context)
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putString(KEY_TONE, tone.id)
            .putBoolean(KEY_SMART_SUMMARY, smartSummary)
            .apply()

        if (enabled) {
            scheduleDailyReminder(context, hour, minute)
        } else {
            cancelDailyReminder(context)
        }
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun buildMessagePreview(
        tone: ReminderTone,
        todayExpenseCount: Int = 0,
        todayExpenseSum: Double = 0.0
    ): Pair<String, String> {
        val formattedSum = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
            .format(todayExpenseSum.roundToLong())

        return if (todayExpenseCount <= 0) {
            when (tone) {
                ReminderTone.SAVAGE_FROG -> Pair(
                    "🐸 Давид Жабов вызывает на ковер!",
                    "За сегодня 0 записей. Неужели ни рубля не потратил, или прячешь чеки от ква-аудита? Нажми и надиктуй траты за 5 секунд!"
                )
                ReminderTone.CALM_ANALYST -> Pair(
                    "📊 Вечерний финансовый чек-ин",
                    "За сегодня нет зафиксированных операций. Внесите расходы за день, чтобы сохранить точность расчёта дневного лимита."
                )
                ReminderTone.GOAL_MOTIVATOR -> Pair(
                    "🎯 Шаг к твоей финансовой цели!",
                    "День без лишних трат или просто забыл внести расходы? Зафиксируй итоги дня и приблизь свои накопления!"
                )
            }
        } else {
            when (tone) {
                ReminderTone.SAVAGE_FROG -> Pair(
                    "🐸 Итоги дня: −$formattedSum ₽ ($todayExpenseCount опер.)",
                    "Я всё посчитал! Если вечером купил что-то ещё — лучше признайся сразу, пока дневной лимит не треснул по швам."
                )
                ReminderTone.CALM_ANALYST -> Pair(
                    "📊 Сводка за день: −$formattedSum ₽",
                    "Зафиксировано операций: $todayExpenseCount. Проверьте актуальный остаток дневного бюджета и баланс счетов."
                )
                ReminderTone.GOAL_MOTIVATOR -> Pair(
                    "🎯 Дневной рубеж: −$formattedSum ₽ ($todayExpenseCount опер.)",
                    "Отличная дисциплина учета! Загляни в бюджет и посмотри, сколько удалось сэкономить сегодня для твоих целей."
                )
            }
        }
    }

    fun showNotification(
        context: Context,
        todayExpenseCount: Int = 0,
        todayExpenseSum: Double = 0.0,
        overrideTone: ReminderTone? = null
    ) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(context, "Отсутствует разрешение на отправку уведомлений!", Toast.LENGTH_LONG).show()
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeTone = overrideTone ?: getReminderTone(context)
        val (title, body) = buildMessagePreview(activeTone, todayExpenseCount, todayExpenseSum)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
