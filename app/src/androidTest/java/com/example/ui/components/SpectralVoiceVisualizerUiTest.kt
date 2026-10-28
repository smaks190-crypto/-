package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.theme.BudgetTheme
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.utils.VoiceSpectrumAnalyzer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class SpectralVoiceVisualizerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun synthesizePcm16Le(
        tones: List<Pair<Double, Double>>,
        sampleCount: Int = 1024,
        sampleRate: Int = 16000
    ): ByteArray {
        val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            var sum = 0.0
            for ((hz, amp) in tones) {
                sum += amp * sin(2.0 * PI * hz * i / sampleRate)
            }
            val s = (sum.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
            buffer.putShort(s)
        }
        return buffer.array()
    }

    private fun saveCapsuleScreenshot(name: String) {
        composeTestRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dirs = listOfNotNull(
            context.getExternalFilesDir("ui_screenshots"),
            File(context.cacheDir, "ui_screenshots"),
            File("/sdcard/Download/ui_screenshots")
        )
        val bitmap: Bitmap? = try {
            composeTestRule.onNodeWithTag("test_voice_capsule_root")
                .captureToImage()
                .asAndroidBitmap()
        } catch (_: Throwable) {
            null
        }
        if (bitmap != null) {
            dirs.forEach { dir ->
                try {
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(File(dir, "$name.png")).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    @Test
    fun testSpectralVisualizerAcrossAcousticStatesWithSyntheticPcm() {
        val analyzer = VoiceSpectrumAnalyzer()
        var currentBands by mutableStateOf(FloatArray(32))
        var isRecordingState by mutableStateOf(true)

        composeTestRule.setContent {
            BudgetTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VolumetricLiquidAuroraGlow(
                        isRecording = isRecordingState,
                        rmsLevel = if (isRecordingState) 4.5f else 0f,
                        cornerRadius = 36.dp,
                        auroraIntensityScale = 0.52f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .testTag("test_voice_capsule_root")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Emerald400.copy(alpha = 0.16f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            SpectralVoiceVisualizer(
                                spectrumBands = currentBands,
                                isRecording = isRecordingState,
                                barCount = 32,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            )
                        }
                    }
                }
            }
        }

        val visualizerNode = composeTestRule.onNodeWithTag("spectral_voice_visualizer")
        visualizerNode.assertIsDisplayed()

        // 1. Тишина
        analyzer.reset()
        currentBands = analyzer.processPcm16LeFrame(ByteArray(2048))
        composeTestRule.waitForIdle()
        visualizerNode.assert(
            SemanticsMatcher("Dominant region is SILENCE") { node ->
                node.config.getOrElse(SpectralDominantRegionKey) { "" } == "SILENCE"
            }
        )
        saveCapsuleScreenshot("10_voice_visualizer_silence")

        // 2. Обычная речь (доминируют центральные речевые частоты 600–1300 Гц)
        analyzer.reset()
        val normalSpeechPcm = synthesizePcm16Le(listOf(620.0 to 0.42, 1080.0 to 0.38, 1550.0 to 0.25))
        repeat(4) { currentBands = analyzer.processPcm16LeFrame(normalSpeechPcm) }
        composeTestRule.waitForIdle()
        visualizerNode.assert(
            SemanticsMatcher("Active energy > 0.08 in normal speech") { node ->
                node.config.getOrElse(SpectralActiveEnergyKey) { 0f } > 0.08f
            }
        )
        saveCapsuleScreenshot("11_voice_visualizer_normal_speech")

        // 3. Низкий голос (преобладают низкие частоты 135–240 Гц -> регион LOW)
        analyzer.reset()
        val lowVoicePcm = synthesizePcm16Le(listOf(135.0 to 0.60, 230.0 to 0.45))
        repeat(4) { currentBands = analyzer.processPcm16LeFrame(lowVoicePcm) }
        composeTestRule.waitForIdle()
        visualizerNode.assert(
            SemanticsMatcher("Dominant region is LOW for low voice") { node ->
                node.config.getOrElse(SpectralDominantRegionKey) { "" } == "LOW"
            }
        )
        saveCapsuleScreenshot("12_voice_visualizer_low_voice")

        // 4. Высокий голос (преобладают высокие частоты 3200–5000 Гц -> регион HIGH)
        analyzer.reset()
        val highVoicePcm = synthesizePcm16Le(listOf(3300.0 to 0.55, 4900.0 to 0.45))
        repeat(4) { currentBands = analyzer.processPcm16LeFrame(highVoicePcm) }
        composeTestRule.waitForIdle()
        visualizerNode.assert(
            SemanticsMatcher("Dominant region is HIGH for high voice") { node ->
                node.config.getOrElse(SpectralDominantRegionKey) { "" } == "HIGH"
            }
        )
        saveCapsuleScreenshot("13_voice_visualizer_high_voice")

        // 5. Остановка записи -> возврат к покою
        isRecordingState = false
        analyzer.reset()
        currentBands = analyzer.getSmoothedBands()
        composeTestRule.waitForIdle()
        visualizerNode.assert(
            SemanticsMatcher("Returns to SILENCE on stop") { node ->
                node.config.getOrElse(SpectralDominantRegionKey) { "" } == "SILENCE"
            }
        )
        saveCapsuleScreenshot("14_voice_visualizer_stopped")
    }
}
