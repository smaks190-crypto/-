package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.SwipeDirection
import com.example.ui.components.SwipeToRevealBox
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ObsidianBentoCategoriesSection(
    categoryExpenseTotals: Map<String, Double>,
    categoriesList: List<CategoryEntity>,
    onOpenCategoryLimits: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "КАТЕГОРИИ И ЛИМИТЫ",
                color = Slate300,
                fontSize = 12.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Настроить ›",
                color = Emerald400,
                fontSize = 12.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenCategoryLimits)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        val defaultCategories = listOf(
            Triple("Гейминг и Аниме", 22000.0, Emerald400 to Icons.Default.SportsEsports),
            Triple("Бары и Тусовки", 4800.0, Indigo500 to Icons.Default.LocalBar),
            Triple("Такси и Транспорт", 1420.0, Rose500 to Icons.Default.DirectionsCar),
            Triple("Продукты и Еда", 1150.0, Emerald400 to Icons.Default.Restaurant),
            Triple("Форс-мажоры", 300.0, Indigo500 to Icons.Default.Warning)
        )

        val displayCategories = remember(categoryExpenseTotals, categoriesList) {
            val colors = listOf(Emerald400, Indigo500, Rose500, Color(0xFFF59E0B), Color(0xFF06B6D4))
            if (categoryExpenseTotals.isNotEmpty()) {
                val realSorted = categoryExpenseTotals.entries.sortedByDescending { it.value }.take(5)
                val realItems = realSorted.mapIndexed { idx, entry ->
                    val (col, ic) = getCategoryColorAndIcon(entry.key, "")
                    val finalCol = if (col == Slate400) colors[idx % colors.size] else col
                    Triple(entry.key, entry.value, finalCol to ic)
                }

                if (realItems.size < 5) {
                    val remainingDb = categoriesList
                        .filter { it.type == "expense" && realItems.none { item -> item.first.equals(it.name, true) } }
                        .mapIndexed { index, cat ->
                            val (col, ic) = getCategoryColorAndIcon(cat.name, "")
                            val finalCol = if (col == Slate400) colors[(realItems.size + index) % colors.size] else col
                            Triple(cat.name, 0.0, finalCol to ic)
                        }
                    (realItems + remainingDb).take(5)
                } else {
                    realItems
                }
            } else {
                val expenseDbCats = categoriesList.filter { it.type == "expense" }
                if (expenseDbCats.isNotEmpty()) {
                    expenseDbCats.mapIndexed { idx, cat ->
                        val (col, ic) = getCategoryColorAndIcon(cat.name, "")
                        val finalCol = if (col == Slate400) colors[idx % colors.size] else col
                        Triple(cat.name, 0.0, finalCol to ic)
                    }.take(5)
                } else {
                    defaultCategories.map { Triple(it.first, 0.0, it.third) }
                }
            }
        }

        val maxCategoryVal = remember(displayCategories) {
            displayCategories.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        }

        val rows = displayCategories.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { (name, amount, colorAndIcon) ->
                    val (baseColor, icon) = colorAndIcon
                    val limitObj = categoriesList.find { it.name.equals(name, ignoreCase = true) && it.type == "expense" }
                    val catLimit = limitObj?.monthlyLimit
                    val ratio = if (catLimit != null && catLimit > 0) {
                        (amount / catLimit).toFloat().coerceIn(0.06f, 1f)
                    } else {
                        (amount / maxCategoryVal).toFloat().coerceIn(0.08f, 1f)
                    }
                    val barColor = when {
                        catLimit != null && catLimit > 0 && amount > catLimit -> Rose500
                        catLimit != null && catLimit > 0 && amount >= catLimit * 0.8 -> Amber400
                        else -> baseColor
                    }

                    Card(
                        onClick = onOpenCategoryLimits,
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = BorderStroke(1.dp, GlassBorder),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(baseColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = baseColor,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        color = Slate200,
                                        fontSize = 12.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatShortCurrency(amount),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (catLimit != null && catLimit > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Лимит ${formatShortCurrency(catLimit)}",
                                            color = Slate500,
                                            fontSize = 9.sp,
                                            fontFamily = ManropeFontFamily
                                        )
                                        Text(
                                            text = "${Math.round((amount / catLimit) * 100)}%",
                                            color = barColor,
                                            fontSize = 9.sp,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.07f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = ratio)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }
                }

                if (rowItems.size == 1) {
                    Card(
                        onClick = onOpenCategoryLimits,
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Emerald400.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Все лимиты",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${categoriesList.count { it.type == "expense" }} категорий",
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentTransactionsFeedCard(
    filteredTransactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity>,
    todayIso: String,
    onOpenAllTransactions: () -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onEditTransaction: ((TransactionEntity) -> Unit)?
) {
    val recentTxList = remember(filteredTransactions) {
        filteredTransactions.take(7)
    }

    val groupedRecentTx = remember(recentTxList) {
        recentTxList.groupBy { it.date }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ИСТОРИЯ ОПЕРАЦИЙ",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Всего ${filteredTransactions.size} записей за период",
                        color = Slate500,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily
                    )
                }

                if (filteredTransactions.isNotEmpty() || allTransactions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.clickable(onClick = onOpenAllTransactions)
                    ) {
                        Text(
                            text = "Все операции ›",
                            color = Emerald400,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (recentTxList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет операций за выбранный период",
                        color = Slate500,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedRecentTx.forEach { (dateStr, dayTxs) ->
                        val dayLabel = remember(dateStr, todayIso) {
                            formatHumanDateHeader(dateStr, todayIso)
                        }
                        val dayNet = remember(dayTxs) {
                            dayTxs.sumOf { if (it.type == "income") it.amount else -it.amount }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayLabel,
                                color = Slate400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (dayNet >= 0) "+${formatShortCurrency(dayNet)}" else formatShortCurrency(dayNet),
                                color = Slate500,
                                fontSize = 11.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        dayTxs.forEach { tx ->
                            TransactionRowItem(
                                item = tx,
                                onDelete = onDeleteTransaction,
                                onClick = if (onEditTransaction != null) {
                                    { onEditTransaction(tx) }
                                } else null,
                                canDelete = false
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatHumanDateHeader(dateStr: String, todayIso: String): String {
    if (dateStr == todayIso) return "Сегодня"
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(todayIso) ?: return dateStr
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayIso = sdf.format(cal.time)
        if (dateStr == yesterdayIso) return "Вчера"

        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val monthNum = parts[1].toIntOrNull() ?: 1
            val dayNum = parts[2].toIntOrNull() ?: 1
            val monthsGenitive = listOf(
                "января", "февраля", "марта", "апреля", "мая", "июня",
                "июля", "августа", "сентября", "октября", "ноября", "декабря"
            )
            return "$dayNum ${monthsGenitive.getOrElse(monthNum - 1) { "" }}"
        }
    } catch (_: Exception) {}
    return dateStr
}

@Composable
fun PeriodButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Emerald400.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Emerald400.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Emerald400 else Slate400,
            fontSize = 11.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun getCategoryColorAndIcon(category: String, subcategory: String): Pair<Color, ImageVector> {
    val text = "$category $subcategory".lowercase()
    return when {
        text.contains("игра") || text.contains("гейм") || text.contains("аниме") || text.contains("кибер") -> Pair(Emerald400, Icons.Default.SportsEsports)
        text.contains("бар") || text.contains("тусов") || text.contains("алко") || text.contains("вечерин") -> Pair(Indigo500, Icons.Default.LocalBar)
        text.contains("форс") || text.contains("авар") || text.contains("штраф") -> Pair(Amber400, Icons.Default.Warning)
        text.contains("кредит") || text.contains("займ") || text.contains("ипотек") || text.contains("долг") || text.contains("банк") -> Pair(Rose500, Icons.Default.AccountBalance)
        text.contains("аптек") || text.contains("лекарст") || text.contains("здоровье") || text.contains("больниц") || text.contains("врач") || text.contains("медицин") -> Pair(Rose500, Icons.Default.MedicalServices)
        text.contains("сбережен") || text.contains("копилк") || text.contains("накоплен") || text.contains("вклад") || text.contains("инвест") || text.contains("фонд") || text.contains("цель") -> Pair(Indigo500, Icons.Default.Savings)
        text.contains("яндекс") || text.contains("yandex") -> Pair(Color(0xFFFC3F1D), Icons.Default.ShoppingBag)
        text.contains("сбер") || text.contains("перевод") || text.contains("тинькофф") || text.contains("карта") || text.contains("спб") -> Pair(Emerald400, Icons.Default.Refresh)
        text.contains("продукт") || text.contains("супермаркет") || text.contains("еда") || text.contains("магнит") || text.contains("пятерочк") || text.contains("ашан") || text.contains("магазин") -> Pair(Color(0xFFF59E0B), Icons.Default.Restaurant)
        text.contains("кафе") || text.contains("ресторан") || text.contains("фастфуд") || text.contains("доставк") || text.contains("кофе") || text.contains("столовая") -> Pair(Color(0xFFEC4899), Icons.Default.Restaurant)
        text.contains("транспорт") || text.contains("такси") || text.contains("авто") || text.contains("бензин") || text.contains("заправк") || text.contains("метро") || text.contains("автобус") -> Pair(Indigo400, Icons.Default.DirectionsCar)
        text.contains("развлечени") || text.contains("кино") || text.contains("подписк") || text.contains("музык") || text.contains("театр") || text.contains("спорт") -> Pair(Color(0xFFA855F7), Icons.Default.Theaters)
        text.contains("жилье") || text.contains("коммунал") || text.contains("дом") || text.contains("жкх") || text.contains("аренд") || text.contains("квартир") -> Pair(Color(0xFF06B6D4), Icons.Default.Home)
        text.contains("связь") || text.contains("интернет") || text.contains("телефон") || text.contains("мобильн") || text.contains("техник") -> Pair(Color(0xFF3B82F6), Icons.Default.Call)
        text.contains("зарплат") || text.contains("доход") || text.contains("преми") || text.contains("аванс") || text.contains("кэшбэк") || text.contains("халтур") -> Pair(Emerald400, Icons.Default.Payments)
        text.contains("одежд") || text.contains("обувь") || text.contains("гардероб") || text.contains("сумка") || text.contains("красот") || text.contains("салон") -> Pair(Color(0xFFEC4899), Icons.Default.Checkroom)
        text.contains("подарок") || text.contains("подарк") || text.contains("праздник") || text.contains("цветы") -> Pair(Rose500, Icons.Default.CardGiftcard)
        text.contains("книг") || text.contains("литератур") || text.contains("чтени") || text.contains("литрес") -> Pair(Color(0xFF8B5CF6), Icons.Default.List)
        text.contains("обучени") || text.contains("образовани") || text.contains("курсы") || text.contains("школа") -> Pair(Color(0xFF60A5FA), Icons.Default.School)
        else -> Pair(Slate400, Icons.Default.ShoppingBag)
    }
}

@Composable
fun TransactionRowItem(
    item: TransactionEntity,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    canDelete: Boolean = true
) {
    val numberFormat = remember {
        val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        DecimalFormat("#,##0.##", symbols).apply {
            isGroupingUsed = true
        }
    }
    val isExpense = item.type == "expense"
    val prefix = if (isExpense) "-" else "+"
    val (catColor, catIcon) = getCategoryColorAndIcon(item.category, item.subcategory)
    val hasSubcategory = item.subcategory.isNotBlank() && !item.subcategory.equals(item.category, ignoreCase = true)
    val topTitle = if (hasSubcategory) item.subcategory else item.category
    val bottomTitle = if (hasSubcategory) item.category else if (isExpense) "Расход" else "Доход"

    val cardContent: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E1525))
                .border(
                    BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.07f)
                    ),
                    RoundedCornerShape(18.dp)
                )
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(catColor.copy(alpha = 0.15f))
                        .border(1.dp, catColor.copy(alpha = 0.32f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = item.category,
                        tint = catColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bottomTitle,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$prefix${numberFormat.format(item.amount)} ₽",
                color = if (isExpense) Color.White else Emerald400,
                fontSize = 15.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    if (canDelete) {
        SwipeToRevealBox(
            swipeDirection = SwipeDirection.Both,
            onDelete = { onDelete(item.id) },
            shape = RoundedCornerShape(18.dp),
            modifier = modifier
        ) {
            cardContent(Modifier)
        }
    } else {
        cardContent(modifier)
    }
}
