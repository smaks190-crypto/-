package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
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
import java.text.NumberFormat
import java.util.Locale

data class CategoryVisualStyle(
    val icon: ImageVector,
    val color: Color
)

fun resolveCategoryVisual(categoryName: String, type: String = "expense"): CategoryVisualStyle {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("продукт") || lower.contains("супермаркет") || lower.contains("выживан") ->
            CategoryVisualStyle(Icons.Default.ShoppingCart, Color(0xFFFF6D00))
        lower.contains("кафе") || lower.contains("ресторан") || lower.contains("кофе") || lower.contains("шаурм") || lower.contains("лавандов") ->
            CategoryVisualStyle(Icons.Default.Restaurant, Color(0xFFFF4081))
        lower.contains("транспорт") || lower.contains("такси") || lower.contains("авто") || lower.contains("самокат") ->
            CategoryVisualStyle(Icons.Default.DirectionsCar, Color(0xFF00B0FF))
        lower.contains("жкх") || lower.contains("дом") || lower.contains("аренд") || lower.contains("коммунал") || lower.contains("ипотек") ->
            CategoryVisualStyle(Icons.Default.Home, Color(0xFF7C4DFF))
        lower.contains("здоров") || lower.contains("аптек") || lower.contains("врач") || lower.contains("нерв") || lower.contains("психолог") ->
            CategoryVisualStyle(Icons.Default.Favorite, Color(0xFFFF5252))
        lower.contains("развлеч") || lower.contains("кино") || lower.contains("подписк") || lower.contains("игр") || lower.contains("тусовк") ->
            CategoryVisualStyle(Icons.Default.SportsEsports, Color(0xFFE040FB))
        lower.contains("маркетплейс") || lower.contains("покупк") || lower.contains("шопинг") || lower.contains("озон") || lower.contains("wb") || lower.contains("импульсив") ->
            CategoryVisualStyle(Icons.Default.ShoppingBag, Color(0xFFFFAB00))
        lower.contains("одежд") || lower.contains("обув") || lower.contains("стиль") ->
            CategoryVisualStyle(Icons.Default.Checkroom, Color(0xFFEC4899))
        lower.contains("красот") || lower.contains("уход") || lower.contains("барбер") ->
            CategoryVisualStyle(Icons.Default.Spa, Color(0xFFF472B6))
        lower.contains("образован") || lower.contains("курс") || lower.contains("успешн") ->
            CategoryVisualStyle(Icons.Default.School, Color(0xFF38BDF8))
        lower.contains("питомц") || lower.contains("кот") || lower.contains("собак") ->
            CategoryVisualStyle(Icons.Default.Pets, Color(0xFFA78BFA))
        lower.contains("зарплат") || lower.contains("аванс") || lower.contains("оклад") ->
            CategoryVisualStyle(Icons.Default.AccountBalanceWallet, Color(0xFF00E676))
        lower.contains("фриланс") || lower.contains("халтур") || lower.contains("шабашк") || lower.contains("подработ") ->
            CategoryVisualStyle(Icons.Default.Work, Color(0xFF00E5FF))
        lower.contains("кэшбэк") || lower.contains("кешбэк") || lower.contains("процент") || lower.contains("дивиденд") || lower.contains("инвест") ->
            CategoryVisualStyle(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF69F0AE))
        lower.contains("подар") || lower.contains("бабушк") || lower.contains("сюрприз") ->
            CategoryVisualStyle(Icons.Default.CardGiftcard, Color(0xFFFFD600))
        lower.contains("крипт") || lower.contains("хомяк") || lower.contains("тапалк") ->
            CategoryVisualStyle(Icons.Default.Psychology, Color(0xFFF59E0B))
        type == "income" ->
            CategoryVisualStyle(Icons.Default.AttachMoney, Color(0xFF00E676))
        else ->
            CategoryVisualStyle(Icons.Default.Category, Color(0xFF90A4AE))
    }
}

@Composable
fun CategoryIconBadge(
    categoryName: String,
    type: String = "expense",
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val visual = resolveCategoryVisual(categoryName, type)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(visual.color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = categoryName,
            tint = visual.color,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Мгновенное (0 мс) семантическое определение категории и типа (расход/доход)
 * по ключевым словам во время набора текста пользователем.
 * Возвращает Pair(type, matchedCategoryName) или null, если совпадений нет.
 */
fun detectCategoryAndTypeByTitle(
    input: String,
    categories: List<CategoryEntity>,
    currentType: String
): Pair<String, String>? {
    val lower = input.lowercase().trim()
    if (lower.length < 2) return null

    // 1. Сначала проверяем явные маркеры дохода
    val incomeHints = listOf(
        "зарплат" to listOf("зарплат", "доход", "оклад"),
        "аванс" to listOf("зарплат", "аванс", "доход"),
        "оклад" to listOf("зарплат", "доход"),
        "преми" to listOf("зарплат", "преми", "доход"),
        "фриланс" to listOf("фриланс", "подработ", "шабашк", "доход"),
        "шабашк" to listOf("фриланс", "шабашк", "подработ", "доход"),
        "заказ" to listOf("фриланс", "доход"),
        "кэшбэк" to listOf("кэшбэк", "кешбэк", "бонус", "доход"),
        "кешбэк" to listOf("кешбэк", "кэшбэк", "бонус", "доход"),
        "дивиденд" to listOf("инвест", "дивиденд", "кэшбэк", "доход"),
        "вернул долг" to listOf("долг", "возврат", "доход"),
        "подарили" to listOf("подар", "бабушк", "доход")
    )

    for ((trigger, targetStems) in incomeHints) {
        if (lower.contains(trigger)) {
            val incomeCats = categories.filter { it.type == "income" }
            val matched = incomeCats.firstOrNull { cat ->
                targetStems.any { stem -> cat.name.lowercase().contains(stem) }
            } ?: incomeCats.firstOrNull()
            if (matched != null) {
                return "income" to matched.name
            }
        }
    }

    // 2. Семантический словарь расходов (из David-finance-main-2 + расширенный)
    val expenseSemanticMap = listOf(
        listOf("продукт", "супермаркет", "выживан", "еда") to listOf(
            "пятерочк", "пятёрочк", "магнит", "перекресток", "перекрёсток", "вкусвилл", "лента",
            "ашан", "дикси", "хлеб", "молок", "сыр", "мясо", "куриц", "овощ", "фрукт", "продукт",
            "самокат", "лавка", "колбас", "яйц", "творог", "макарон", "гречк", "пельмен", "доширак"
        ),
        listOf("кафе", "ресторан", "фастфуд", "кофе", "лавандов") to listOf(
            "кофе", "капучин", "латте", "раф", "обед", "ужин", "завтрак", "кафе", "ресторан",
            "шаурм", "шаверм", "пицц", "бургер", "суши", "ролл", "макдак", "вкусно и точк",
            "ростикс", "kfc", "додо", "столов", "булочн", "круассан", "пекарн", "бар"
        ),
        listOf("транспорт", "такси", "авто") to listOf(
            "такси", "яндекс го", "uber", "метро", "автобус", "трамвай", "троллейбус", "проезд",
            "тройка", "подорожник", "бензин", "азс", "лукойл", "газпром", "роснефть", "парковк",
            "каршеринг", "делимобиль", "ситидрайв", "электричк", "ржд", "билет", "шиномонтаж", "мойк"
        ),
        listOf("жкх", "жилье", "дом", "аренд", "коммунал") to listOf(
            "жкх", "квартплат", "коммуналк", "интернет", "ростелеком", "дом.ру", "мтс", "билайн",
            "мегафон", "теле2", "yota", "связь", "свет", "электричеств", "моэнерго", "вода",
            "водоканал", "газ", "отоплен", "аренд", "квартира", "домофон", "капремонт"
        ),
        listOf("здоров", "аптек", "медицин", "нерв") to listOf(
            "аптек", "лекарств", "таблетк", "врач", "клиник", "анализ", "инвитро", "гемотест",
            "стоматолог", "зуб", "витамин", "терапевт", "очки", "линз", "массаж", "психолог"
        ),
        listOf("развлеч", "отдых", "подписк", "кино") to listOf(
            "кино", "театр", "концерт", "бар", "клуб", "боулинг", "бильярд", "кальян", "steam",
            "псн", "playstation", "xbox", "игр", "нетфликс", "яндекс плюс", "кинопоиск", "спотифай",
            "подписк", "музей", "выставк", "квест", "парк"
        ),
        listOf("маркетплейс", "покупк", "шопинг", "импульсив", "одежд") to listOf(
            "озон", "ozon", "валберис", "вайлдберриз", "wb", "wildberries", "яндекс маркет",
            "алиэкспресс", "одежд", "обув", "кроссовк", "куртк", "джинс", "футболк", "рубашк",
            "платье", "косметик", "золотое яблок", "летуаль", "икеа", "леруа", "dns", "днс", "мвидео"
        )
    )

    val expenseCats = categories.filter { it.type == "expense" }
    for ((catStems, keywords) in expenseSemanticMap) {
        if (keywords.any { lower.contains(it) }) {
            val matched = expenseCats.firstOrNull { cat ->
                catStems.any { stem -> cat.name.lowercase().contains(stem) }
            }
            if (matched != null) {
                return "expense" to matched.name
            }
        }
    }

    // 3. Прямое совпадение по имени существующей категории
    val directMatch = categories.firstOrNull { cat ->
        val catLow = cat.name.lowercase()
        lower.contains(catLow) || (catLow.length >= 4 && lower.contains(catLow.take(4)))
    }
    if (directMatch != null) {
        return directMatch.type to directMatch.name
    }

    return null
}

fun formatRublesCompact(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = 0
    }
    return formatter.format(amount)
}
