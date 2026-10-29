package com.example.davidapp.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ScreenshotHelper
import com.example.ui.theme.BudgetTheme
import com.example.ui.theme.DarkBg
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Скриншот экрана David Live. Настоящий звонок требует сети и ключа Gemini,
 * поэтому экран рисуется отдельно в состоянии «Давид слушает».
 */
class DavidLiveScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureDavidLiveScreen() {
        composeRule.setContent {
            BudgetTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    DavidLiveSheet(
                        connectionState = DavidLiveConnectionState.LISTENING,
                        liveSubtitle = "Говорите, я слушаю...",
                        rayMagnitudes = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { i ->
                            0.25f + 0.45f * abs(sin(i * 0.37f))
                        },
                        overallEnergy = 0.55f,
                        activeSpeaker = DavidLiveSpeaker.USER,
                        isMicMuted = false,
                        errorMessage = null,
                        onToggleMicMute = {},
                        onRetryConnection = {},
                        onOpenApiKeySettings = {},
                        onEndSession = {}
                    )
                }
            }
        }

        try {
            composeRule.waitForIdle()
        } catch (_: Throwable) {
            // Бесконечные анимации могут мешать «простою» — для скриншота это не критично
        }
        Thread.sleep(2_500)
        ScreenshotHelper.takeScreenshot("04_david_live")
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun prepareDevice() {
            ScreenshotHelper.prepareDevice()
        }

        @JvmStatic
        @AfterClass
        fun restoreDevice() {
            ScreenshotHelper.restoreDevice()
        }
    }
}
