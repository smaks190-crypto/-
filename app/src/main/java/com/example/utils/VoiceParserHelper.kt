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

        // Find numbers/amounts and compile matches (supporting 'к', 'k', 'тыс' and punctuation boundaries)
        val numberRegex = "(\\d+(?:\\.\\d+)?)\\s*(тыс(?:яч[аи]?)?|к|k)?\\s*(?:руб(?:л(?:ей|я|ь)?)?|р|₽)?(?=[,;\\s]|$)".toRegex()
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

        for ((idx, match) in matches.withIndex()) {
            val baseAmount = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val multiplierGroup = match.groupValues.getOrNull(2).orEmpty()
            val amount = if (multiplierGroup.isNotEmpty() && baseAmount < 1000.0) {
                baseAmount * 1000.0
            } else {
                baseAmount
            }
            val matchIndex = match.range.first

            val prevMatchEnd = if (idx > 0) matches[idx - 1].range.last + 1 else 0
            val nextMatchStart = if (idx + 1 < matches.size) matches[idx + 1].range.first else cleanText.length

            val rawBefore = cleanText.substring(prevMatchEnd, matchIndex)
            val rawAfter = cleanText.substring(match.range.last + 1, nextMatchStart)

            // Ограничиваем контекст текущей клаузой (по разделителям `,`, `;`, ` и `, `\n`),
            // чтобы ключевое слово «зарплата» в одной части фразы не превращало остальные суммы
            // (баланс, долги, расходы) в доходы.
            val beforeClause = rawBefore.split(Regex("(?:[,;\\n]+|\\s+и\\s+|\\s+а\\s+)")).lastOrNull()?.trim().orEmpty()
            val afterClause = rawAfter.split(Regex("(?:[,;\\n]+|\\s+и\\s+|\\s+а\\s+)")).firstOrNull()?.trim().orEmpty()

            val localClauseContext = "$beforeClause $afterClause".trim()

            // Пропускаем чистые упоминания долгов («я должен», «мне должны») и констатации текущего баланса,
            // чтобы они не превращались в ложные операции дохода/расхода при разборе чата.
            val isDebtStatement = localClauseContext.contains("я долж") ||
                localClauseContext.contains("мне должн") ||
                localClauseContext.contains("должны мне") ||
                localClauseContext.contains("мой долг") ||
                localClauseContext.contains("взял в долг") ||
                localClauseContext.contains("дал в долг")
            val isBalanceStatement = localClauseContext.contains("текущий баланс") ||
                localClauseContext.contains("мой баланс") ||
                localClauseContext.contains("начальный баланс") ||
                localClauseContext.contains("остаток на счете") ||
                localClauseContext.contains("остаток на карте")
            if (isDebtStatement || isBalanceStatement) {
                continue
            }

            var title = ""
            var matchedCategory = ""
            var type = "expense"

            var foundIncomeKeyword = false
            for ((key, cat) in incomeKeywords) {
                if (localClauseContext.contains(key)) {
                    title = key.capitalizeFirstLetter()
                    matchedCategory = if (key == "зарплата" || key == "аванс") {
                        incomeCategories.firstOrNull { it.contains("Зарплата", true) }
                            ?: incomeCategories.firstOrNull { it.contains(cat, true) }
                            ?: "Зарплата"
                    } else {
                        incomeCategories.firstOrNull { it.contains(cat, true) } ?: cat
                    }
                    type = "income"
                    foundIncomeKeyword = true
                    break
                }
            }

            if (!foundIncomeKeyword) {
                for ((key, cat) in expenseKeywords) {
                    if (localClauseContext.contains(key)) {
                        title = key.capitalizeFirstLetter()
                        matchedCategory = expenseCategories.firstOrNull { it.contains(cat, true) } ?: cat
                        type = "expense"
                        break
                    }
                }
            }

            if (title.isEmpty()) {
                val words = beforeClause.split("\\s+".toRegex()).filter {
                    it.length > 2 && it != "потратил" && it != "купил" && it != "вчера" && it != "позавчера" && it != "сегодня"
                }
                title = if (words.isNotEmpty()) {
                    words.joinToString(" ").capitalizeFirstLetter()
                } else {
                    val afterWords = afterClause.split("\\s+".toRegex()).filter {
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
