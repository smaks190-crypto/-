package com.example.data.safetospend

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object RecurringScheduleCalculator {

    /**
     * Computes the next scheduled due date in EpochDay strictly after [currentDueDateEpochDay],
     * preserving [anchorDayOfMonth] across short months (e.g. Jan 31 -> Feb 28 -> Mar 31)
     * and leap years (e.g. Jan 31 2028 -> Feb 29 2028 -> Mar 31 2028).
     * Purely based on LocalDate/EpochDay, completely invariant to timezone or DST shifts.
     */
    fun computeNextDueDate(
        currentDueDateEpochDay: Long,
        frequency: PaymentFrequency,
        anchorDayOfMonth: Int? = null,
        anchorDayOfWeek: Int? = null,
        customIntervalDays: Int? = null
    ): Long {
        val currentDate = LocalDate.ofEpochDay(currentDueDateEpochDay)
        val nextDate = when (frequency) {
            PaymentFrequency.DAILY -> currentDate.plusDays(1)
            PaymentFrequency.WEEKLY -> {
                if (anchorDayOfWeek != null && anchorDayOfWeek in 1..7) {
                    currentDate.with(TemporalAdjusters.next(DayOfWeek.of(anchorDayOfWeek)))
                } else {
                    currentDate.plusWeeks(1)
                }
            }
            PaymentFrequency.BIWEEKLY -> currentDate.plusWeeks(2)
            PaymentFrequency.MONTHLY -> addMonthsClamped(currentDate, 1, anchorDayOfMonth)
            PaymentFrequency.QUARTERLY -> addMonthsClamped(currentDate, 3, anchorDayOfMonth)
            PaymentFrequency.YEARLY -> addMonthsClamped(currentDate, 12, anchorDayOfMonth)
            PaymentFrequency.CUSTOM_DAYS -> {
                val interval = (customIntervalDays ?: 30).coerceAtLeast(1)
                currentDate.plusDays(interval.toLong())
            }
        }
        return nextDate.toEpochDay()
    }

    private fun addMonthsClamped(
        baseDate: LocalDate,
        monthsToAdd: Long,
        anchorDayOfMonth: Int?
    ): LocalDate {
        val targetYearMonth = YearMonth.from(baseDate).plusMonths(monthsToAdd)
        val desiredDay = (anchorDayOfMonth ?: baseDate.dayOfMonth).coerceIn(1, 31)
        val clampedDay = desiredDay.coerceAtMost(targetYearMonth.lengthOfMonth())
        return targetYearMonth.atDay(clampedDay)
    }

    /**
     * Generates all scheduled due dates for [payment] within [rangeStartEpochDay]..[rangeEndEpochDay]
     * starting from [payment.nextDueDateEpochDay].
     */
    fun generateOccurrencesInRange(
        payment: RecurringPaymentDomain,
        rangeStartEpochDay: Long,
        rangeEndEpochDay: Long
    ): List<Long> {
        if (payment.status != RecurringStatus.ACTIVE) return emptyList()
        if (rangeEndEpochDay < rangeStartEpochDay) return emptyList()

        val result = mutableListOf<Long>()
        var cursor = payment.nextDueDateEpochDay
        val anchorDay = payment.anchorDayOfMonth
            ?: LocalDate.ofEpochDay(payment.nextDueDateEpochDay).dayOfMonth

        var safetyCounter = 0
        while (cursor <= rangeEndEpochDay && safetyCounter < 400) {
            if (payment.endDateEpochDay != null && cursor > payment.endDateEpochDay) {
                break
            }
            if (cursor >= rangeStartEpochDay) {
                result.add(cursor)
            }
            val next = computeNextDueDate(
                currentDueDateEpochDay = cursor,
                frequency = payment.frequency,
                anchorDayOfMonth = anchorDay,
                anchorDayOfWeek = payment.anchorDayOfWeek,
                customIntervalDays = payment.customIntervalDays
            )
            if (next <= cursor) break
            cursor = next
            safetyCounter++
        }
        return result
    }

    /**
     * Parses a Russian natural-language voice or chat command into a structured [RecurringPaymentDraft].
     * Example: "Добавь подписку Яндекс Плюс 399 рублей 15 числа каждого месяца"
     */
    fun parseVoiceRecurringDraft(
        rawCommand: String,
        todayLocalDate: LocalDate
    ): RecurringPaymentDraft? {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale("ru", "RU"))

        // 1. Extract day of month ("15 числа", "25-го числа") first so we don't confuse it with the amount
        val dayMatch = Regex("""(\d{1,2})(?:-го|\s*го)?\s+числа""").find(lower)
        val anchorDayOfMonth = dayMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 31)

        // 2. Extract amount (number followed by руб/₽/р/тыс or largest number not equal to day match range)
        val textWithoutDay = if (dayMatch != null) {
            lower.removeRange(dayMatch.range)
        } else {
            lower
        }

        val amountWithCurrencyRegex = Regex("""(\d+(?:[\s.,]\d+)?)\s*(тыс(?:яч)?|к|k)?\s*(?:руб(?:лей|ля|ль|\.)?|₽|р\b)""")
        val explicitMatch = amountWithCurrencyRegex.find(textWithoutDay)
        val amountRub: Double = if (explicitMatch != null) {
            val rawNum = explicitMatch.groupValues[1].replace(" ", "").replace(",", ".").toDoubleOrNull() ?: return null
            val mult = if (explicitMatch.groupValues[2].isNotEmpty()) 1000.0 else 1.0
            rawNum * mult
        } else {
            val fallbackNum = Regex("""\b(\d+(?:[.,]\d+)?)\b""").findAll(textWithoutDay)
                .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
                .firstOrNull { it > 0.0 } ?: return null
            fallbackNum
        }

        if (amountRub <= 0.0) return null

        // 3. Determine income vs expense & priority
        val isIncome = lower.contains("зарплат") || lower.contains("аванс") || lower.contains("доход") || lower.contains("стипенд")
        val isSubscription = lower.contains("подписк") || lower.contains("плюс") || lower.contains("netflix") ||
            lower.contains("spotify") || lower.contains("музык") || lower.contains("кино")

        // 4. Determine frequency
        val frequency = when {
            lower.contains("каждый день") || lower.contains("ежедневно") -> PaymentFrequency.DAILY
            lower.contains("каждую неделю") || lower.contains("еженедельно") -> PaymentFrequency.WEEKLY
            lower.contains("раз в две недели") || lower.contains("каждые 2 недели") -> PaymentFrequency.BIWEEKLY
            lower.contains("каждый квартал") || lower.contains("ежеквартально") -> PaymentFrequency.QUARTERLY
            lower.contains("каждый год") || lower.contains("ежегодно") -> PaymentFrequency.YEARLY
            else -> PaymentFrequency.MONTHLY
        }

        // 5. Extract title cleanly
        var titleCandidate = trimmed
        listOf(
            Regex("""(?i)^(добавь|создай|запиши|поставь|оформи)\s+"""),
            Regex("""(?i)^(регулярный\s+плат[её]ж|автоплат[её]ж|подписку|подписка|оплату|плат[её]ж)\s+"""),
            Regex("""(?i)\d+(?:[\s.,]\d+)?\s*(?:тыс(?:яч)?|к|k)?\s*(?:руб(?:лей|ля|ль|\.)?|₽|р\b).*$"""),
            Regex("""(?i)\d{1,2}(?:-го|\s*го)?\s+числа.*$"""),
            Regex("""(?i)(?:каждого|каждый|ежемесячно|еженедельно|ежедневно).*$""")
        ).forEach { regex ->
            titleCandidate = titleCandidate.replace(regex, " ").trim()
        }
        val finalTitle = titleCandidate.trim(' ', ',', '.', '-', '«', '»', '"')
            .ifBlank {
                when {
                    isIncome -> "Регулярный доход"
                    isSubscription -> "Подписка"
                    else -> "Регулярный платёж"
                }
            }

        // 6. Compute nextDueDateEpochDay
        val resolvedAnchorDay = anchorDayOfMonth ?: todayLocalDate.dayOfMonth
        val currentYm = YearMonth.from(todayLocalDate)
        val thisMonthDate = currentYm.atDay(resolvedAnchorDay.coerceAtMost(currentYm.lengthOfMonth()))
        val nextDueLocalDate = if (!thisMonthDate.isBefore(todayLocalDate)) {
            thisMonthDate
        } else {
            val nextYm = currentYm.plusMonths(1)
            nextYm.atDay(resolvedAnchorDay.coerceAtMost(nextYm.lengthOfMonth()))
        }

        // 7. Determine category & priority
        val categoryName = when {
            isIncome -> "Зарплата"
            isSubscription -> "Подписки"
            lower.contains("аренд") || lower.contains("жкх") || lower.contains("коммунал") || lower.contains("квартир") -> "Жилье"
            lower.contains("интернет") || lower.contains("связь") || lower.contains("телефон") -> "Связь"
            lower.contains("кредит") || lower.contains("ипотек") || lower.contains("долг") -> "Кредиты"
            else -> "Обязательные платежи"
        }
        val priority = if (isSubscription) {
            ObligationPriority.DISCRETIONARY_SUBSCRIPTION
        } else {
            ObligationPriority.MANDATORY
        }

        return RecurringPaymentDraft(
            title = finalTitle,
            amountRub = amountRub,
            isIncome = isIncome,
            frequency = frequency,
            anchorDayOfMonth = if (frequency == PaymentFrequency.MONTHLY || frequency == PaymentFrequency.QUARTERLY || frequency == PaymentFrequency.YEARLY) resolvedAnchorDay else null,
            anchorDayOfWeek = null,
            nextDueDateEpochDay = nextDueLocalDate.toEpochDay(),
            categoryName = categoryName,
            priority = priority
        )
    }
}
