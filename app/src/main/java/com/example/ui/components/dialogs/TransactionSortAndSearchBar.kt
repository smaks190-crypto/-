package com.example.ui.components.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun TransactionSortAndSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    selectedCategoryFilter: String?,
    isDrilledDownToMixed: Boolean,
    remainingCategoryNames: List<String>,
    onClearCategoryFilter: () -> Unit,
    getCategoryColor: (String) -> Color,
    selectedPeriod: String?,
    customStartStr: String?,
    dynamicMonthLabel: String,
    onOpenDatePickerModal: () -> Unit,
    onResetPeriodLock: () -> Unit,
    sortOption: String,
    onSortOptionChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_and_sort_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Unified Obsidian Search Field (stable position, never jumps on scroll)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            color = Color(0xFF0E1525),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(
                1.dp,
                if (searchQuery.isNotEmpty()) Indigo500.copy(alpha = 0.7f) else Slate800
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                    modifier = Modifier.size(17.dp)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_input_field"),
                    singleLine = true,
                    cursorBrush = SolidColor(Emerald400),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Поиск по операциям, категориям или сумме...",
                                color = Slate500,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить поиск",
                        tint = Slate400,
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("search_clear_button")
                            .clickable { onSearchQueryChange("") }
                    )
                }
            }
        }

        // Sorting & Filter Chips Row (without duplicate type filter buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sort_and_filter_chips_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Сортировка:",
                    color = Slate400,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                val isCatActive = !selectedCategoryFilter.isNullOrBlank() || isDrilledDownToMixed
                if (isCatActive) {
                    val catLabel = selectedCategoryFilter
                        ?: if (remainingCategoryNames.size <= 1) "Прочие" else "Смешанные"
                    val catColor = if (!selectedCategoryFilter.isNullOrBlank()) {
                        getCategoryColor(selectedCategoryFilter)
                    } else {
                        Indigo500
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(catColor.copy(alpha = 0.22f))
                            .border(1.dp, catColor.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                            .testTag("active_category_filter_chip")
                            .clickable { onClearCategoryFilter() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = catLabel,
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "✕",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Period / Custom Date Range Selector Chip
                val isPeriodActive = !selectedPeriod.isNullOrBlank() || !customStartStr.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isPeriodActive) Indigo500.copy(alpha = 0.22f) else Slate900)
                        .border(
                            width = 1.dp,
                            color = if (isPeriodActive) Indigo500.copy(alpha = 0.65f) else Slate800,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .testTag("period_date_chip")
                        .clickable { onOpenDatePickerModal() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = if (isPeriodActive) Color.White else Slate400,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dynamicMonthLabel,
                            color = if (isPeriodActive) Color.White else Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isPeriodActive) {
                            Text(
                                text = "✕",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .testTag("period_chip_reset")
                                    .clickable { onResetPeriodLock() }
                            )
                        } else {
                            Text("▼", color = Slate400, fontSize = 8.sp)
                        }
                    }
                }

                // Sort by Amount Chip
                val isAmountActive = sortOption == "desc" || sortOption == "asc"
                val arrowRotation by animateFloatAsState(
                    targetValue = if (sortOption == "asc") 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "arrowRotation"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isAmountActive) Indigo500.copy(alpha = 0.22f) else Slate900)
                        .border(
                            width = 1.dp,
                            color = if (isAmountActive) Indigo500.copy(alpha = 0.65f) else Slate800,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .testTag("sort_amount_chip")
                        .clickable {
                            onSortOptionChange(
                                when (sortOption) {
                                    "desc" -> "asc"
                                    "asc" -> "date"
                                    else -> "desc"
                                }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "↓",
                            color = if (isAmountActive) Color.White else Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                        )
                        Text(
                            text = "Сумма",
                            color = if (isAmountActive) Color.White else Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sort A-Z / Z-A Chip
                val isNameActive = sortOption == "name" || sortOption == "name_desc"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isNameActive) Indigo500.copy(alpha = 0.22f) else Slate900)
                        .border(
                            width = 1.dp,
                            color = if (isNameActive) Indigo500.copy(alpha = 0.65f) else Slate800,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .testTag("sort_name_chip")
                        .clickable {
                            onSortOptionChange(
                                when (sortOption) {
                                    "name" -> "name_desc"
                                    "name_desc" -> "date"
                                    else -> "name"
                                }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (sortOption == "name_desc") "Z-A" else "A-Z",
                        color = if (isNameActive) Color.White else Slate400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
