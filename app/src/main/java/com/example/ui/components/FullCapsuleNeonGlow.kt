package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FullCapsuleNeonGlow(
    isRecording: Boolean,
    amplitude: Float, // Громкость речи в диапазоне 0.0f..1.0f
    widthDp: Float,
    heightDp: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Вращение градиента
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleNeonRotate")
    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CapsulePhase"
    )

    // Динамическая пульсация размера неонового поля в такт голосу
    val glowScale by animateFloatAsState(
        targetValue = if (isRecording) 1.05f + (amplitude * 0.12f) else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "CapsuleGlowScale"
    )

    val neonColors = if (isRecording) {
        listOf(
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4), // Cyan
            Color(0xFF10B981), // Emerald
            Color(0xFFEC4899)
        )
    } else {
        listOf(
            Color(0xFF10B981).copy(alpha = 0.2f),
            Color(0xFF059669).copy(alpha = 0.1f)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (isRecording) {
            // Внешняя мягкая размытая аура
            Box(
                modifier = Modifier
                    .width((widthDp + 24f).dp)
                    .height((heightDp + 24f).dp)
                    .scale(glowScale)
                    .blur(24.dp)
                    .background(
                        brush = Brush.sweepGradient(neonColors),
                        shape = RoundedCornerShape(36.dp)
                    )
            )

            // Внутренний яркий контур свечения
            Box(
                modifier = Modifier
                    .width((widthDp + 8f).dp)
                    .height((heightDp + 8f).dp)
                    .scale(glowScale)
                    .blur(10.dp)
                    .background(
                        brush = Brush.linearGradient(neonColors),
                        shape = RoundedCornerShape(32.dp)
                    )
            )
        }

        content()
    }
}
