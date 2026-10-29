package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 32-полосный спектральный визуализатор голоса для голосового ввода финансовых операций (`VoiceInputManager`).
 * Делегирует рендеринг реальному FFT-компоненту [SpectralVoiceVisualizer].
 */
@Composable
fun Neon32BandVoiceVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    isRecording: Boolean = true
) {
    SpectralVoiceVisualizer(
        isRecording = isRecording,
        rmsLevel = amplitude,
        barCount = barCount,
        modifier = modifier
    )
}
