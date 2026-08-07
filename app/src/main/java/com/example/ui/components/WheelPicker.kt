package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItemsCount: Int = 3,
    itemHeight: Dp = 44.dp,
    isCircular: Boolean = true
) {
    if (items.isEmpty()) return

    val totalItemCount = if (isCircular) items.size * 10000 else items.size
    val factor = if (isCircular) 10000 else 1

    val initialIdx = remember(items, initialIndex, isCircular) {
        val safeInit = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (isCircular && items.isNotEmpty()) {
            val midMultiplier = factor / 2
            (midMultiplier * items.size) + safeInit
        } else {
            safeInit
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIdx)
    val baseFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val flingBehavior = remember(baseFlingBehavior) {
        object : FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                val cappedVelocity = initialVelocity.coerceIn(-3000f, 3000f)
                return with(baseFlingBehavior) {
                    performFling(cappedVelocity)
                }
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()

    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(listState.isScrollInProgress) {
        swipeEnabledState.value = !listState.isScrollInProgress
    }

    val totalHeight = itemHeight * visibleItemsCount
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) { with(density) { itemHeight.toPx() } }

    var isFirstCenterEmission by remember { mutableStateOf(true) }

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                listState.firstVisibleItemIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }
                closest?.index ?: listState.firstVisibleItemIndex
            }
        }
    }

    val selectedIndex by remember {
        derivedStateOf {
            if (items.isEmpty()) 0 else centerIndex % items.size
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            onItemSelected(selectedIndex)
            if (isFirstCenterEmission) {
                isFirstCenterEmission = false
            } else {
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Indigo500.copy(alpha = 0.18f))
                .border(1.dp, Indigo500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * ((visibleItemsCount - 1) / 2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(totalItemCount) { index ->
                val actualIndex = index % items.size
                val isSelected = centerIndex == index
                val distance = abs(centerIndex - index)

                val alpha by animateFloatAsState(
                    targetValue = when (distance) {
                        0 -> 1f
                        1 -> 0.55f
                        else -> 0.25f
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "wheel_alpha"
                )

                val rotationAngle by animateFloatAsState(
                    targetValue = when {
                        index < centerIndex -> -25f
                        index > centerIndex -> 25f
                        else -> 0f
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "wheel_rotation"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            this.alpha = alpha
                            this.rotationX = rotationAngle
                            val scale = if (isSelected) 1.08f else 0.88f
                            this.scaleX = scale
                            this.scaleY = scale
                        }
                        .clickable {
                            if (index != centerIndex) {
                                coroutineScope.launch {
                                    val delta = (index - centerIndex) * itemHeightPx
                                    listState.animateScrollBy(delta)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[actualIndex],
                        color = if (isSelected) Color.White else Color(0xFF818CF8).copy(alpha = 0.65f),
                        fontSize = if (isSelected) 20.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MonthWheelPickerDialog(
    initialMonthIdx: Int,
    months: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (monthIdx: Int) -> Unit
) {
    var selectedMonthIdx by remember { mutableIntStateOf(initialMonthIdx.coerceIn(0, months.size - 1)) }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Slate700)
                        )
                    }

                    Text(
                        text = "Выберите месяц",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    WheelPicker(
                        items = months,
                        initialIndex = selectedMonthIdx,
                        onItemSelected = { selectedMonthIdx = it },
                        modifier = Modifier.width(200.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onConfirm(selectedMonthIdx)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Text("Применить", color = DarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthYearWheelPickerDialog(
    initialMonthIdx: Int,
    initialYear: Int,
    months: List<String>,
    years: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (monthIdx: Int, year: Int) -> Unit
) {
    var selectedMonthIdx by remember { mutableIntStateOf(initialMonthIdx.coerceIn(0, months.size - 1)) }
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    val yearStrings = remember(years) { years.map { "$it год" } }
    val initialYearIdx = remember(years, initialYear) {
        val idx = years.indexOf(initialYear)
        if (idx >= 0) idx else 0
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Slate700)
                        )
                    }

                    Text(
                        text = "Выберите период",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("МЕСЯЦ", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            WheelPicker(
                                items = months,
                                initialIndex = selectedMonthIdx,
                                onItemSelected = { selectedMonthIdx = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ГОД", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            WheelPicker(
                                items = yearStrings,
                                initialIndex = initialYearIdx,
                                onItemSelected = { idx ->
                                    if (idx in years.indices) {
                                        selectedYear = years[idx]
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isCircular = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onConfirm(selectedMonthIdx, selectedYear)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Text("Применить", color = DarkBg, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun YearWheelPickerDialog(
    initialYear: Int,
    years: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (year: Int) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    val yearStrings = remember(years) { years.map { "$it год" } }
    val initialYearIdx = remember(years, initialYear) {
        val idx = years.indexOf(initialYear)
        if (idx >= 0) idx else 0
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                    .border(1.dp, Slate800, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Slate700)
                        )
                    }

                    Text(
                        text = "Выберите год",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    WheelPicker(
                        items = yearStrings,
                        initialIndex = initialYearIdx,
                        onItemSelected = { idx ->
                            if (idx in years.indices) {
                                selectedYear = years[idx]
                            }
                        },
                        modifier = Modifier.width(180.dp),
                        isCircular = false
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onConfirm(selectedYear)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                    ) {
                        Text("Применить", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
