package com.example.davidapp.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DavidLiveSpeechTest {
    @Test fun `connection messages and stale speech are not shown as dialogue`() {
        for (state in listOf(DavidLiveVisualState.API_MISSING, DavidLiveVisualState.ERROR,
            DavidLiveVisualState.OFFLINE, DavidLiveVisualState.CONNECTING, DavidLiveVisualState.RECONNECTING)) {
            assertNull(resolveDavidLiveSpeech(state, "Подключите API, повторите"))
        }
    }

    @Test fun `dialogue contains only the current transcript`() {
        assertEquals("Ваш бюджет", resolveDavidLiveSpeech(DavidLiveVisualState.DAVID_SPEAKING, " Ваш бюджет "))
        assertNull(resolveDavidLiveSpeech(DavidLiveVisualState.DAVID_SPEAKING, "  "))
    }
}
