package com.example.davidapp.live

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class DavidLivePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionNoticeDoesNotAppearAsSpeech() {
        val state = mutableStateOf(DavidLiveConnectionState.NO_API_KEY)
        var settingsOpened = 0
        compose.setContent {
            BudgetTheme {
                DavidLiveSheet(
                    connectionState = state.value,
                    liveSubtitle = "Обсудим бюджет",
                    rayMagnitudes = List(32) { 0.1f }, overallEnergy = 0.1f,
                    activeSpeaker = DavidLiveSpeaker.DAVID, isMicMuted = false, errorMessage = "HTTP internal error",
                    onToggleMicMute = {}, onRetryConnection = {},
                    onOpenApiKeySettings = { settingsOpened++ }, onEndSession = {},
                    enableDebugStateSwitcher = false
                )
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        screenshot("voice-initial-layout")
        try {
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithTag("david_live_connection_notice").assertIsDisplayed(); true }.getOrDefault(false)
            }
        } catch (e: Throwable) {
            throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(), e)
        }
        compose.onNodeWithTag("david_live_connection_notice").assertIsDisplayed()
        compose.onNodeWithTag("david_live_subtitle_bubble").assertDoesNotExist()
        compose.onNodeWithTag("david_live_contextual_action_button").performClick()
        assertEquals(1, settingsOpened)
        screenshot("voice-needs-key")
        compose.runOnIdle { state.value = DavidLiveConnectionState.DAVID_SPEAKING }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("david_live_connection_notice").assertDoesNotExist()
        compose.onNodeWithTag("david_live_subtitle_bubble").assertIsDisplayed()
        screenshot("voice-conversation")
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "release-screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
