package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (ReminderManager.isReminderEnabled(context)) {
                val (hour, minute) = ReminderManager.getReminderTime(context)
                ReminderManager.scheduleDailyReminder(context, hour, minute)
            }
        } else if (intent.action == ReminderManager.ACTION_DAILY_REMINDER) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val db = com.example.data.db.AppDatabase.getDatabase(context)
                    val expenseCount = db.transactionDao().getExpenseCountForDate(todayIso)
                    val expenseSum = db.transactionDao().getExpenseSumForDate(todayIso)

                    if (expenseCount == 0 || ReminderManager.isSmartSummaryEnabled(context)) {
                        ReminderManager.showNotification(
                            context = context,
                            todayExpenseCount = expenseCount,
                            todayExpenseSum = expenseSum
                        )
                    }
                } catch (_: Exception) {
                    ReminderManager.showNotification(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
