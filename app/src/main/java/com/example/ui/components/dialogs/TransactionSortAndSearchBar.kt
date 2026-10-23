package com.example.ui.components.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun TransactionSortAndSearchBar(
    isSearchExpandedInPlace: Boolean,
    onSetSearchExpandedInPlace: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    selectedCategoryFilter: String?,
    isDrilledDownToMixed: Boolean,
    remainingCategoryNames: List<String>,
    onClearCategoryFilter: () -> Unit,
    getCategoryColor: (String) -> Color,
    selectedPeriod: String,
    customStartStr: String?,
    dynamicMonthLabel: String,
    onOpenDatePickerModal: () -> Unit,
    onResetPeriodToAll: () -> Unit,
    sortOption: String,
    onSortOptionChange: (String) -> Unit,
    isScrolled: Boolean
) {
    AnimatedContent(
        targetState = isSearchExpandedInPlace,
        transitionSpec = {
            (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.98f))
                .togetherWith(fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.98f))
        },
        label = "search_row_in_sorting_morph"
    ) { expanded ->
        if (expanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(vertical = 2.dp),
                color = Slate900,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (searchQuery.isNotEmpty()) Indigo500 else Slate800
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Быстрый поиск...",
                                    color = Slate500,
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Скрыть поиск",
                        tint = Slate400,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                onSetSearchExpandedInPlace(false)
                            }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val isCatActive = !selectedCategoryFilter.isNullOrBlank() || isDrilledDownToMixed
                    if (isCatActive) {
                        val catLabel = selectedCategoryFilter ?: if (remainingCategoryNames.size <= 1) "Прочие" else "Смешанные"
                        val catColor = if (!selectedCategoryFilter.isNullOrBlank()) getCategoryColor(selectedCategoryFilter) else Indigo500
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(catColor.copy(alpha = 0.2f))
                                .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onClearCategoryFilter() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = catLabel,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.8f),
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
                    val isPeriodActive = selectedPeriod != "all" || !customStartStr.isNullOrBlank()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPeriodActive) Indigo500 else Slate900)
                            .border(1.dp, if (isPeriodActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPeriodActive) {
                                Text(
                                    text = "✕",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    modifier = Modifier.clickable { onResetPeriodToAll() }
                                )
                            } else {
                                Text("▼", color = Slate400, fontSize = 8.sp)
                            }
                        }
                    }

                    val isAmountActive = sortOption == "desc" || sortOption == "asc"
                    val arrowRotation by animateFloatAsState(
                        targetValue = if (sortOption == "asc") 180f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "arrowRotation"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAmountActive) Indigo500 else Slate900)
                            .border(1.dp, if (isAmountActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val isNameActive = sortOption == "name" || sortOption == "name_desc"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isNameActive) Indigo500 else Slate900)
                            .border(1.dp, if (isNameActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isScrolled) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (searchQuery.isNotEmpty()) Indigo500.copy(alpha = 0.2f) else Slate900)
                                .border(
                                    1.dp,
                                    if (searchQuery.isNotEmpty()) Indigo500 else Slate800,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSetSearchExpandedInPlace(true) }
                                .size(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Поиск",
                                tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
