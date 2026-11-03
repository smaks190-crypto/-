package com.example.davidapp.live

import org.junit.Assert.assertEquals
import org.junit.Test

class DavidLiveStreamingTextTest {

    @Test
    fun appendedTranscriptContinuesFromCurrentRevealCursor() {
        assertEquals(
            7,
            speechBubbleRevealStartIndex(
                previousText = "Hello, ",
                nextText = "Hello, David",
                currentlyRevealedLength = 8
            )
        )
    }

    @Test
    fun revisedTranscriptReplaysFromLastSharedWordBoundary() {
        assertEquals(
            6,
            speechBubbleRevealStartIndex(
                previousText = "Hello old",
                nextText = "Hello new",
                currentlyRevealedLength = 9
            )
        )
    }

    @Test
    fun revealCursorIsClampedToPreviousTextLength() {
        assertEquals(
            5,
            speechBubbleRevealStartIndex(
                previousText = "Hello",
                nextText = "Hello!",
                currentlyRevealedLength = 99
            )
        )
    }

    @Test
    fun nextChunkIncludesTrailingWhitespace() {
        assertEquals(6, nextSpeechBubbleRevealEnd("Hello world", 0))
    }

    @Test
    fun nextChunkPreservesNewlineWhitespace() {
        assertEquals(7, nextSpeechBubbleRevealEnd("Hello\nworld", 0))
    }

    @Test
    fun endOfTextReturnsTextLength() {
        assertEquals(5, nextSpeechBubbleRevealEnd("Hello", 5))
    }
}
