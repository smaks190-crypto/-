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

object ReminderManager {
    const val CHANNEL_ID = "daily_budget_reminders"
    const val CHANNEL_NAME = "Напоминания о бюджете"
    const val NOTIFICATION_ID = 1001
    const val ACTION_DAILY_REMINDER = "com.example.ACTION_DAILY_REMINDER"

    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_TONE = "reminder_tone"
    private const val KEY_SMART_SUMMARY = "reminder_smart_summary"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ежедневные напоминания о внесении расходов и проверке бюджета"
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
