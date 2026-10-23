package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.db.CategoryEntity
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Sky400
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CategoryVisualSpec(
    val icon: ImageVector,
    val color: Color
)

fun resolveCategoryVisualSpec(categoryName: String, type: String): CategoryVisualSpec {
    val lower = categoryName.lowercase(Locale("ru", "RU"))
    return when {
        lower.contains("продукт") || lower.contains("супермаркет") || lower.contains("еда") ->
            CategoryVisualSpec(Icons.Default.LocalGroceryStore, Emerald400)
        lower.contains("кафе") || lower.contains("ресторан") || lower.contains("кофе") || lower.contains("фастфуд") ->
            CategoryVisualSpec(Icons.Default.LocalCafe, Amber400)
        lower.contains("транспорт") || lower.contains("такси") || lower.contains("авто") || lower.contains("бензин") || lower.contains("метро") ->
            CategoryVisualSpec(Icons.Default.DirectionsCar, Sky400)
        lower.contains("жкх") || lower.contains("дом") || lower.contains("кварт") || lower.contains("аренд") || lower.contains("связь") ->
            CategoryVisualSpec(Icons.Default.Home, Indigo400)
        lower.contains("развлеч") || lower.contains("кино") || lower.contains("отдых") || lower.contains("игр") ->
            CategoryVisualSpec(Icons.Default.Movie, Rose400)
        lower.contains("покуп") || lower.contains("одежд") || lower.contains("маркетплейс") || lower.contains("шоппинг") ->
            CategoryVisualSpec(Icons.Default.ShoppingBag, Indigo400)
        lower.contains("здоров") || lower.contains("аптек") || lower.contains("врач") || lower.contains("спорт") ->
            CategoryVisualSpec(Icons.Default.Favorite, Rose400)
        lower.contains("подпис") || lower.contains("сервис") ->
            CategoryVisualSpec(Icons.Default.Subscriptions, Sky400)
        lower.contains("сбережен") || lower.contains("накоп") || lower.contains("цел") || lower.contains("копилк") ->
            CategoryVisualSpec(Icons.Default.Savings, Emerald400)
        lower.contains("зарплат") || lower.contains("аванс") || lower.contains("работа") ->
            CategoryVisualSpec(Icons.Default.Work, Emerald400)
        lower.contains("инвест") || lower.contains("дивиденд") || lower.contains("вклад") || lower.contains("кэшбэк") ->
            CategoryVisualSpec(Icons.Default.TrendingUp, Emerald400)
        type == "income" ->
            CategoryVisualSpec(Icons.Default.AccountBalanceWallet, Emerald400)
        else ->
            CategoryVisualSpec(Icons.Default.MoreHoriz, Sky400)
    }
}

@Composable
fun CategoryIconBadge(
    categoryName: String,
    type: String,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    modifier: Modifier = Modifier
) {
    val spec = resolveCategoryVisualSpec(categoryName, type)
    val corner = (size.value * 0.32f).dp
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(spec.color.copy(alpha = 0.14f))
            .border(1.dp, spec.color.copy(alpha = 0.32f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = categoryName,
            tint = spec.color,
            modifier = Modifier.size(iconSize)
        )
    }
}

fun formatRublesCompact(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    return DecimalFormat("#,##0.##", symbols).format(amount)
}

/**
 * 0ms local semantic matcher that maps a transaction title/description to (type, categoryName).
 */
fun detectCategoryAndTypeByTitle(
    title: String,
    categories: List<CategoryEntity>,
    currentType: String = "expense"
): Pair<String, String>? {
    val q = title.trim().lowercase(Locale("ru", "RU"))
    if (q.length < 2) return null

    // Direct match against user's configured categories
    val directCat = categories.firstOrNull { cat ->
        val nameLower = cat.name.lowercase(Locale("ru", "RU"))
        q == nameLower || (nameLower.length >= 4 && q.contains(nameLower))
    }
    if (directCat != null) {
        return directCat.type to directCat.name
    }

    val expenseHints = listOf(
        listOf("пятерочк", "магнит", "перекресток", "вкусвилл", "лента", "ашан", "дикси", "самокат", "лавка", "хлеб", "молок", "продукт", "мясо", "овощ", "фрукт", "сыр") to "Продукты",
        listOf("кофе", "капучино", "латте", "раф", "кафе", "ресторан", "обед", "ужин", "завтрак", "шаурм", "бургер", "пицц", "суши", "ролл", "додо", "вкусно и точка", "kfc", "rostics") to "Кафе и рестораны",
        listOf("такси", "яндекс го", "метро", "автобус", "проезд", "бензин", "азс", "лукойл", "газпромнефть", "роснефть", "парковк", "каршеринг", "самокат") to "Транспорт",
        listOf("аптек", "лекарств", "врач", "клиник", "анализ", "стоматолог", "таблетк") to "Здоровье",
        listOf("жкх", "квартплат", "интернет", "связь", "мтс", "билайн", "мегафон", "теле2", "yota", "электричеств", "вода", "аренда") to "ЖКХ",
        listOf("кино", "театр", "концерт", "подписк", "яндекс плюс", "музык", "игр", "steam", "боулинг", "бар") to "Развлечения",
        listOf("ozon", "озон", "wildberries", "вб", "вайлдберриз", "одежд", "обувь", "кроссовк", "куртк", "футболк", "маркет") to "Покупки"
    )

    val incomeHints = listOf(
        listOf("зарплат", "зп", "аванс", "оклад", "преми") to "Зарплата",
        listOf("фриланс", "заказ", "проект", "гонорар", "подработк") to "Фриланс",
        listOf("кэшбэк", "кешбэк", "процент", "дивиденд", "вклад") to "Кэшбэк",
        listOf("подарок", "подарили", "перевод от") to "Подарки"
    )

    for ((keywords, targetCategory) in incomeHints) {
        if (keywords.any { q.contains(it) }) {
            val matched = categories.firstOrNull {
                it.type == "income" && it.name.contains(targetCategory.take(4), ignoreCase = true)
            }?.name ?: targetCategory
            return "income" to matched
        }
    }

    for ((keywords, targetCategory) in expenseHints) {
        if (keywords.any { q.contains(it) }) {
            val matched = categories.firstOrNull {
                it.type == "expense" && it.name.contains(targetCategory.take(4), ignoreCase = true)
            }?.name ?: targetCategory
            return "expense" to matched
        }
    }

    return null
}
