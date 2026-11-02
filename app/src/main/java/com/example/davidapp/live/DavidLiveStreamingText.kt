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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

/**
 * Reveals a live speech transcript without replacing the whole Text node on every partial update.
 *
 * The complete incoming string is measured immediately (so the bubble can animate its height),
 * while only the newly received word/tail is faded in. A stream-key change cross-fades the
 * previous speaker/status out before starting the next stream from the beginning.
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

    // Keep the outgoing phrase visible while it fades, then begin the new speaker/status stream.
    LaunchedEffect(streamKey) {
        if (activeStreamKey != streamKey) {
            contentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )
            activeStreamKey = streamKey
            contentAlpha.snapTo(0f)
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
        }
    }

    // The effect is keyed by the incoming transcript: a newer partial instantly cancels any
    // in-flight animation for stale text and resumes at the last fully revealed boundary.
    LaunchedEffect(activeStreamKey, streamKey, text, isStreaming) {
        // Do not replace the outgoing phrase until its cross-fade has finished.
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
                animationSpec = tween(
                    durationMillis = 105,
                    easing = FastOutSlowInEasing
                )
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

/**
 * Finds the point from which a new transcript version should be revealed.
 *
 * Appends continue from the current cursor. When Gemini revises already received words,
 * only the changed tail is replayed, starting at the last shared word boundary.
 */
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
            charArrayOf(' ', '\t', '\n', '\r'),
            startIndex = (commonPrefixLength - 1).coerceAtLeast(0)
        ).let { index -> if (index < 0) 0 else index + 1 }
    }

    return minOf(sharedWordBoundary, currentlyRevealedLength)
        .coerceIn(0, nextText.length)
}

/** Returns the end of the next word together with its trailing whitespace. */
internal fun nextSpeechBubbleRevealEnd(text: String, startIndex: Int): Int {
    if (startIndex >= text.length) return text.length

    var end = startIndex
    while (end < text.length && text[end].isWhitespace()) end++
    while (end < text.length && !text[end].isWhitespace()) end++
    while (end < text.length && text[end].isWhitespace()) end++

    return end
}
