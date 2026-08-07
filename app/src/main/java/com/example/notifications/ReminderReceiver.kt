package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (ReminderManager.isReminderEnabled(context)) {
                val (hour, minute) = ReminderManager.getReminderTime(context)
                ReminderManager.scheduleDailyReminder(context, hour, minute)
            }
        } else if (intent.action == ReminderManager.ACTION_DAILY_REMINDER) {
            val pendingResult = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    val db = com.example.data.db.AppDatabase.getDatabase(context)
                    val expenseCount = db.transactionDao().getExpenseCountForDateSync(todayIso)
                    if (expenseCount == 0) {
                        ReminderManager.showNotification(context)
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
