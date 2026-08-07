package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate700
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.abs
import kotlin.math.roundToInt

object SwipeToRevealController {
    val collapseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun requestCollapseAll() {
        collapseRequests.tryEmit(Unit)
    }
}

private var activeOpenedBox: Animatable<Float, *>? = null

enum class SwipeDirection {
    EndToStart, // Swipe right-to-left (reveals actions on the right)
    StartToEnd, // Swipe left-to-right (reveals actions on the left)
    Both        // Supports both directions
}

@Composable
fun SwipeToRevealBox(
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    swipeDirection: SwipeDirection = SwipeDirection.Both,
    resetSwipe: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    var isItemRemoved by remember { mutableStateOf(false) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    LaunchedEffect(isItemRemoved) {
        if (isItemRemoved) {
            delay(280)
            onDelete?.invoke()
        }
    }

    AnimatedVisibility(
        visible = !isItemRemoved,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 200)
        ),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
        ) {
            val parentWidthPx = with(density) { maxWidth.toPx() }
            val actionsCount = (if (onDelete != null) 1 else 0) + (if (onEdit != null) 1 else 0) + (if (onExport != null) 1 else 0)
            val isSingleDeleteOnly = onDelete != null && onEdit == null && onExport == null

            val dismissThresholdPx = parentWidthPx * 0.38f

            val maxRevealLeftPx = with(density) {
                when (swipeDirection) {
                    SwipeDirection.StartToEnd, SwipeDirection.Both -> {
                        if (isSingleDeleteOnly) parentWidthPx else (72 * actionsCount).dp.toPx()
                    }
                    SwipeDirection.EndToStart -> 0f
                }
            }

            val maxRevealRightPx = with(density) {
                when (swipeDirection) {
                    SwipeDirection.EndToStart, SwipeDirection.Both -> {
                        if (isSingleDeleteOnly) parentWidthPx else (72 * actionsCount).dp.toPx()
                    }
                    SwipeDirection.StartToEnd -> 0f
                }
            }

            val currentOffsetAbs = abs(offsetX.value)
            val isPastThreshold = isSingleDeleteOnly && currentOffsetAbs >= dismissThresholdPx

            // Haptic Feedback trigger on crossing threshold
            LaunchedEffect(isPastThreshold) {
                if (currentOffsetAbs > 10f) {
                    if (isPastThreshold && !hasCrossedThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasCrossedThreshold = true
                    } else if (!isPastThreshold && hasCrossedThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        hasCrossedThreshold = false
                    }
                }
            }

            // Reset handling
            LaunchedEffect(resetSwipe) {
                if (resetSwipe && offsetX.value != 0f) {
                    offsetX.snapTo(0f)
                }
            }

            // Collapse controller handling
            LaunchedEffect(Unit) {
                SwipeToRevealController.collapseRequests.collect {
                    if (offsetX.value != 0f) {
                        offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }

            // Background layer (Actions / Notification Style Delete Background)
            if (currentOffsetAbs > 0.5f) {
                val isStartToEnd = offsetX.value > 0f

                if (isSingleDeleteOnly) {
                    val fraction = (currentOffsetAbs / dismissThresholdPx).coerceIn(0f, 1f)
                    val iconScale = if (isPastThreshold) 1.18f else (0.55f + 0.45f * fraction)
                    val iconAlpha = (currentOffsetAbs / (dismissThresholdPx * 0.30f)).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shape)
                            .background(Rose500)
                    ) {
                        val iconAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 22.dp)
                                .align(iconAlignment),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        alpha = iconAlpha
                                    }
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Multi-action background handling (for other screens with edit/export)
                    val limitPx = if (isStartToEnd) maxRevealLeftPx else maxRevealRightPx
                    val swipeProgress = if (limitPx > 0f) (currentOffsetAbs / limitPx).coerceIn(0f, 1f) else 0f

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shape)
                            .clip(RevealedWidthShape(currentOffsetAbs, isStartToEnd))
                    ) {
                        val actionsWidthDp = with(density) { limitPx.toDp() }
                        Row(
                            modifier = Modifier
                                .width(actionsWidthDp)
                                .matchParentSize()
                                .align(if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = if (isStartToEnd) Arrangement.Start else Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onDelete != null && isStartToEnd && swipeDirection != SwipeDirection.Both) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .padding(horizontal = 2.dp)
                                        .graphicsLayer { alpha = swipeProgress }
                                        .clip(shape)
                                        .background(Rose500)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isItemRemoved = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (onExport != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .padding(horizontal = 2.dp)
                                        .graphicsLayer { alpha = swipeProgress }
                                        .clip(shape)
                                        .background(Emerald400)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch { offsetX.animateTo(0f) }
                                            onExport()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Скачать",
                                        tint = DarkBg,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            if (onEdit != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .padding(horizontal = 2.dp)
                                        .graphicsLayer { alpha = swipeProgress }
                                        .clip(shape)
                                        .background(Slate700)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch { offsetX.animateTo(0f) }
                                            onEdit()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Редактировать",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            if (onDelete != null && !isStartToEnd && swipeDirection != SwipeDirection.Both) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(64.dp)
                                        .padding(horizontal = 2.dp)
                                        .graphicsLayer { alpha = swipeProgress }
                                        .clip(shape)
                                        .background(Rose500)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isItemRemoved = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Foreground Content Item Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(swipeDirection, maxRevealLeftPx, maxRevealRightPx, isSingleDeleteOnly) {
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pointerId = down.id

                                var totalDragX = 0f
                                var isDraggingBox = false
                                val touchSlop = viewConfiguration.touchSlop
                                val startOffsetX = offsetX.value

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val dragEvent = event.changes.firstOrNull { it.id == pointerId } ?: break

                                    if (!dragEvent.pressed) {
                                        if (isDraggingBox) {
                                            dragEvent.consume()
                                            val currentOffsetX = offsetX.value
                                            val absOffset = abs(currentOffsetX)

                                            launch {
                                                if (isSingleDeleteOnly && absOffset >= dismissThresholdPx) {
                                                    val targetX = if (currentOffsetX > 0f) parentWidthPx else -parentWidthPx
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    offsetX.animateTo(
                                                        targetValue = targetX,
                                                        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                                    )
                                                    isItemRemoved = true
                                                } else if (isSingleDeleteOnly) {
                                                    if (hasCrossedThreshold) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        hasCrossedThreshold = false
                                                    }
                                                    offsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                } else {
                                                    // Multi-action reveal snap logic
                                                    if (currentOffsetX > 0f) {
                                                        if (currentOffsetX > maxRevealLeftPx * 0.35f) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            offsetX.animateTo(maxRevealLeftPx, spring(stiffness = Spring.StiffnessMediumLow))
                                                        } else {
                                                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                        }
                                                    } else {
                                                        if (currentOffsetX < -maxRevealRightPx * 0.35f) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            offsetX.animateTo(-maxRevealRightPx, spring(stiffness = Spring.StiffnessMediumLow))
                                                        } else {
                                                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        break
                                    }

                                    val dragDelta = dragEvent.position.x - dragEvent.previousPosition.x
                                    totalDragX += dragDelta

                                    if (!isDraggingBox) {
                                        if (abs(totalDragX) >= touchSlop) {
                                            val isValidDirection = when (swipeDirection) {
                                                SwipeDirection.StartToEnd -> totalDragX > 0f
                                                SwipeDirection.EndToStart -> totalDragX < 0f
                                                SwipeDirection.Both -> true
                                            }

                                            if (startOffsetX != 0f || isValidDirection) {
                                                isDraggingBox = true
                                                dragEvent.consume()
                                            } else {
                                                break
                                            }
                                        }
                                    } else {
                                        dragEvent.consume()
                                        val minLimit = -maxRevealRightPx
                                        val maxLimit = maxRevealLeftPx
                                        val newOffset = (offsetX.value + dragDelta).coerceIn(minLimit, maxLimit)
                                        launch { offsetX.snapTo(newOffset) }
                                    }
                                }
                            }
                        }
                    }
            ) {
                content()

                // Overlay to collapse when swiped open in multi-action mode
                if (!isSingleDeleteOnly && currentOffsetAbs > 0.5f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                    )
                }
            }
        }
    }
}

class RevealedWidthShape(private val widthPx: Float, private val isStartToEnd: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rect = if (isStartToEnd) {
            Rect(0f, 0f, widthPx, size.height)
        } else {
            Rect(size.width - widthPx, 0f, size.width, size.height)
        }
        return Outline.Rectangle(rect)
    }
}
