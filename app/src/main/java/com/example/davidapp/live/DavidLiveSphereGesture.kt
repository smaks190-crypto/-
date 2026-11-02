package com.example.davidapp.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tap remains the primary action. A long press switches to the sphere's magnetic touch
 * interaction and subsequent pointer movement updates the projected point on the sphere.
 *
 * The gesture coroutine is cancelled with the pointer-input node, so it cannot leave a
 * stuck interaction behind when the sheet is removed or the pointer stream is cancelled.
 */
@Composable
internal fun Modifier.davidLiveSphereGesture(
    actionDescription: String,
    onTap: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit
): Modifier {
    val currentTap = rememberUpdatedState(onTap)
    val currentStart = rememberUpdatedState(onLongPressStart)
    val currentMove = rememberUpdatedState(onLongPressMove)
    val currentEnd = rememberUpdatedState(onLongPressEnd)
    val currentCancel = rememberUpdatedState(onLongPressCancel)

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var releasedBeforeLongPress = false
            var movedBeforeLongPress = false

            val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                    if (!change.pressed) {
                        releasedBeforeLongPress = true
                        change.consume()
                        return@withTimeoutOrNull false
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        movedBeforeLongPress = true
                        return@withTimeoutOrNull false
                    }
                    change.consume()
                }
                false
            } ?: true

            fun normalized(point: Offset): Offset = Offset(
                x = (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                y = (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            )

            if (longPressReached && !releasedBeforeLongPress && !movedBeforeLongPress) {
                currentStart.value(normalized(down.position))
                try {
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.pressed) {
                            change.consume()
                            released = true
                        } else {
                            if (change.position != change.previousPosition) {
                                currentMove.value(normalized(change.position))
                            }
                            change.consume()
                        }
                    }
                    currentEnd.value()
                } catch (cancelled: CancellationException) {
                    currentCancel.value()
                    throw cancelled
                }
            } else if (releasedBeforeLongPress && !movedBeforeLongPress) {
                currentTap.value()
            }
        }
    }.semantics {
        role = Role.Button
        contentDescription = actionDescription
        onClick(label = actionDescription) {
            currentTap.value()
            true
        }
    }
}
