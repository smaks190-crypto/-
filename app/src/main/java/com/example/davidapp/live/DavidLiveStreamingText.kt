package com.example.davidapp.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

/**
 * Reveals live transcript chunks while keeping previously received text stable.
 *
 * The full target text participates in layout immediately, while new words fade in.
 * A new stream key fades the previous phrase out before revealing the new stream.
 */
@Composable
internal fun DavidLiveStreamingText(
    text: String,
    streamKey: String,
    isStreaming: Boolean,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier = Modifier
) {
    var activeStreamKey by remember { mutableStateOf(streamKey) }
    var renderedText by remember { mutableStateOf(text) }
    var revealedEnd by remember {
        mutableIntStateOf(if (isStreaming) 0 else text.length)
    }
    var activeRange by remember { mutableStateOf<IntRange?>(null) }
    var previousTarget by remember(activeStreamKey) { mutableStateOf("") }
    var revealCursor by remember(activeStreamKey) { mutableIntStateOf(0) }

    val contentAlpha = remember { Animatable(1f) }
    val segmentAlpha = remember { Animatable(1f) }

    LaunchedEffect(streamKey) {
        if (activeStreamKey != streamKey) {
            contentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(110, easing = FastOutSlowInEasing)
            )
            activeStreamKey = streamKey
            contentAlpha.snapTo(0f)
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(170, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(activeStreamKey, streamKey, text, isStreaming) {
        if (activeStreamKey != streamKey) return@LaunchedEffect

        renderedText = text
        if (!isStreaming) {
            revealCursor = text.length
            revealedEnd = text.length
            activeRange = null
            previousTarget = text
            return@LaunchedEffect
        }

        revealCursor = speechBubbleRevealStartIndex(
            previousText = previousTarget,
            nextText = text,
            currentlyRevealedLength = revealCursor
        )
        revealedEnd = revealCursor
        activeRange = null
        previousTarget = text

        while (revealCursor < text.length) {
            val segmentEnd = nextSpeechBubbleRevealEnd(text, revealCursor)
            if (segmentEnd <= revealCursor) break

            activeRange = revealCursor until segmentEnd
            segmentAlpha.snapTo(0f)
            segmentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(105, easing = FastOutSlowInEasing)
            )
            revealCursor = segmentEnd
            revealedEnd = revealCursor
            activeRange = null
            delay(12L)
        }
    }

    val annotatedText = buildAnnotatedString {
        append(renderedText)
        val hiddenStart = revealedEnd.coerceIn(0, renderedText.length)
        if (hiddenStart < renderedText.length) {
            addStyle(
                SpanStyle(color = color.copy(alpha = 0f)),
                start = hiddenStart,
                end = renderedText.length
            )
        }
        activeRange?.let { range ->
            val start = range.first.coerceIn(0, renderedText.length)
            val end = (range.last + 1).coerceIn(start, renderedText.length)
            if (start < end) {
                addStyle(
                    SpanStyle(color = color.copy(alpha = segmentAlpha.value)),
                    start = start,
                    end = end
                )
            }
        }
    }

    BasicText(
        text = annotatedText,
        modifier = modifier.graphicsLayer { alpha = contentAlpha.value },
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight
        ),
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Appends resume at the current cursor; transcript revisions replay only the changed word tail. */
internal fun speechBubbleRevealStartIndex(
    previousText: String,
    nextText: String,
    currentlyRevealedLength: Int
): Int {
    if (nextText.startsWith(previousText)) {
        return currentlyRevealedLength.coerceIn(0, previousText.length)
    }

    var commonPrefixLength = 0
    val maxLength = minOf(previousText.length, nextText.length)
    while (
        commonPrefixLength < maxLength &&
        previousText[commonPrefixLength] == nextText[commonPrefixLength]
    ) {
        commonPrefixLength++
    }

    val sharedWordBoundary = if (commonPrefixLength == nextText.length) {
        commonPrefixLength
    } else {
        nextText.lastIndexOfAny(
            charArrayOf(' ', '\\t', '\\n', '\\r'),
            startIndex = (commonPrefixLength - 1).coerceAtLeast(0)
        ).let { index -> if (index < 0) 0 else index + 1 }
    }

    return minOf(sharedWordBoundary, currentlyRevealedLength)
        .coerceIn(0, nextText.length)
}

/** Returns the next word and its trailing whitespace, preserving whitespace boundaries. */
internal fun nextSpeechBubbleRevealEnd(text: String, startIndex: Int): Int {
    if (startIndex >= text.length) return text.length
    var end = startIndex
    while (end < text.length && text[end].isWhitespace()) end++
    while (end < text.length && !text[end].isWhitespace()) end++
    while (end < text.length && text[end].isWhitespace()) end++
    return end
}
