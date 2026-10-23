package com.example.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun formatDayHeaderLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr

        val todayCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { time = date }

        val isSameYear = todayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)

        if (isSameYear && todayDayOfYear == targetDayOfYear) {
            "Сегодня"
        } else if (isSameYear && todayDayOfYear - targetDayOfYear == 1) {
            "Вчера"
        } else {
            val pattern = if (isSameYear) "d MMMM" else "d MMMM yyyy"
            val rawStr = SimpleDateFormat(pattern, Locale("ru", "RU")).format(date)
            rawStr.split(" ").mapIndexed { idx, word ->
                if (idx == 1) word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
                else word
            }.joinToString(" ")
        }
    } catch (e: Exception) {
        dateStr
    }
}
