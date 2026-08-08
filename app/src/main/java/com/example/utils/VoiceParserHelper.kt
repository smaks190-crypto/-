package com.example.utils

import com.example.data.repository.ParsedVoiceOperation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object VoiceParserHelper {

    fun parseHeuristically(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): List<ParsedVoiceOperation> {
        val cleanText = text.lowercase().trim()
        if (cleanText.isEmpty()) return emptyList()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())

        // 1. Resolve Date
        var dateStr = todayStr
        if (cleanText.contains("вчера")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            dateStr = sdf.format(cal.time)
        } else if (cleanText.contains("позавчера")) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -2)
            dateStr = sdf.format(cal.time)
        }

        // Helper lists
        val expenseKeywords = mapOf(
            "такси" to "Транспорт", "автобус" to "Транспорт", "метро" to "Транспорт", "бензин" to "Транспорт", "заправка" to "Транспорт", "машина" to "Транспорт",
            "продукты" to "Продукты", "молоко" to "Продукты", "хлеб" to "Продукты", "супермаркет" to "Продукты", "еда" to "Продукты", "магнит" to "Продукты", "пятерочка" to "Продукты",
            "кафе" to "Кафе/Рестораны", "ресторан" to "Кафе/Рестораны", "пицца" to "Кафе/Рестораны", "кофе" to "Кафе/Рестораны", "бургер" to "Кафе/Рестораны", "фастфуд" to "Кафе/Рестораны",
            "кино" to "Развлечения", "театр" to "Развлечения", "игра" to "Развлечения", "подписка" to "Развлечения", "бильярд" to "Развлечения",
            "аптека" to "Здоровье", "лекарство" to "Здоровье", "доктор" to "Здоровье", "врач" to "Здоровье",
            "одежда" to "Одежда/Обувь", "куртка" to "Одежда/Обувь", "обувь" to "Одежда/Обувь", "кроссовки" to "Одежда/Обувь"
        )

        val incomeKeywords = mapOf(
            "зарплата" to "Доходы", "доход" to "Доходы", "аванс" to "Доходы", "перевод" to "Доходы", "кешбэк" to "Доходы", "пришло" to "Доходы"
        )

        // Find numbers/amounts and compile matches
        val numberRegex = "(\\d+(?:\\.\\d+)?)\\s*(?:руб|р|\\s|$)".toRegex()
        val matches = numberRegex.findAll(cleanText).toList()

        val operations = mutableListOf<ParsedVoiceOperation>()

        if (matches.isEmpty()) {
            if (cleanText.length > 3) {
                val type = if (incomeKeywords.keys.any { cleanText.contains(it) }) "income" else "expense"
                val category = if (type == "income") {
                    incomeCategories.firstOrNull { it.contains("Доход", true) } ?: incomeCategories.firstOrNull() ?: "Доходы"
                } else {
                    expenseCategories.firstOrNull() ?: "Прочее"
                }
                operations.add(
                    ParsedVoiceOperation(
                        title = text.capitalizeFirstLetter(),
                        type = type,
                        amount = 0.0,
                        category = category,
                        subcategory = "",
                        date = dateStr
                    )
                )
            }
            return operations
        }

        for (match in matches) {
            val amount = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val matchIndex = match.range.first

            val beforeMatch = cleanText.substring(0, matchIndex).trim()
            val afterMatch = cleanText.substring(match.range.last + 1).trim()

            var title = ""
            var matchedCategory = ""
            var type = "expense"

            val fullTextContext = "$beforeMatch $afterMatch"
            
            var foundIncomeKeyword = false
            for ((key, cat) in incomeKeywords) {
                if (fullTextContext.contains(key)) {
                    title = key.capitalizeFirstLetter()
                    matchedCategory = incomeCategories.firstOrNull { it.contains(cat, true) } ?: cat
                    type = "income"
                    foundIncomeKeyword = true
                    break
                }
            }

            if (!foundIncomeKeyword) {
                for ((key, cat) in expenseKeywords) {
                    if (fullTextContext.contains(key)) {
                        title = key.capitalizeFirstLetter()
                        matchedCategory = expenseCategories.firstOrNull { it.contains(cat, true) } ?: cat
                        type = "expense"
                        break
                    }
                }
            }

            if (title.isEmpty()) {
                val words = beforeMatch.split("\\s+".toRegex()).filter { 
                    it.length > 2 && it != "потратил" && it != "купил" && it != "вчера" && it != "позавчера" && it != "сегодня"
                }
                title = if (words.isNotEmpty()) {
                    words.joinToString(" ").capitalizeFirstLetter()
                } else {
                    val afterWords = afterMatch.split("\\s+".toRegex()).filter { 
                        it.length > 2 && it != "рублей" && it != "руб" && it != "вчера" && it != "позавчера" && it != "сегодня"
                    }
                    if (afterWords.isNotEmpty()) {
                        afterWords.joinToString(" ").capitalizeFirstLetter()
                    } else {
                        "Операция"
                    }
                }
            }

            if (matchedCategory.isEmpty()) {
                matchedCategory = if (type == "income") {
                    incomeCategories.firstOrNull() ?: "Доходы"
                } else {
                    expenseCategories.firstOrNull { it.contains("Прочее", true) } ?: expenseCategories.firstOrNull() ?: "Прочее"
                }
            }

            operations.add(
                ParsedVoiceOperation(
                    title = title,
                    type = type,
                    amount = amount,
                    category = matchedCategory,
                    subcategory = "",
                    date = dateStr
                )
            )
        }

        return operations
    }

    private fun String.capitalizeFirstLetter(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
