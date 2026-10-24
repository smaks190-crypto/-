package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

val LocalDialogSwipeEnabled = compositionLocalOf {
    mutableStateOf(true)
}

@Composable
fun SwipeToDismissDialog(
    onDismissRequest: () -> Unit,
    isAtTop: () -> Boolean = { true },
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val navBarInitialHeightPx = with(density) { 68.dp.roundToPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragStartedAtTop by remember { mutableStateOf(false) }
    val swipeEnabledState = remember { mutableStateOf(true) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "dialog_swipe_offset"
    )

    val nestedScrollConnection = remember(isAtTop, swipeEnabledState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!swipeEnabledState.value) return Offset.Zero

                if (source == NestedScrollSource.Drag && offsetY == 0f) {
                    dragStartedAtTop = isAtTop()
                }

                if (offsetY > 0f && available.y < 0f) {
                    val newOffset = (offsetY + available.y).coerceAtLeast(0f)
                    val consumedY = newOffset - offsetY
                    offsetY = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!swipeEnabledState.value) return Offset.Zero

                if (source == NestedScrollSource.Drag && dragStartedAtTop && isAtTop() && available.y > 0f) {
                    offsetY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!swipeEnabledState.value) return Velocity.Zero
                dragStartedAtTop = false
                if (offsetY > 120f) {
                    offsetY = 0f
                    onDismissRequest()
                } else {
                    offsetY = 0f
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!swipeEnabledState.value) return Velocity.Zero
                dragStartedAtTop = false
                if (offsetY > 120f) {
                    offsetY = 0f
                    onDismissRequest()
                } else {
                    offsetY = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val focusManager = LocalFocusManager.current
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        BackHandler(onBack = onDismissRequest)

        CompositionLocalProvider(LocalDialogSwipeEnabled provides swipeEnabledState) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(contentPadding)
                    .nestedScroll(nestedScrollConnection),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxAllowedHeight = maxHeight - 8.dp
                AnimatedVisibility(
                    visible = isVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom,
                        initialHeight = { navBarInitialHeightPx },
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        targetHeight = { navBarInitialHeightPx },
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut(animationSpec = tween(durationMillis = 120))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxAllowedHeight)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                focusManager.clearFocus()
                                SwipeToRevealController.requestCollapseAll()
                            }
                            .offset { IntOffset(0, animatedOffsetY.roundToInt().coerceAtLeast(0)) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
