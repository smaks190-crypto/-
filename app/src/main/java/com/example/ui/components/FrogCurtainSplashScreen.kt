package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val curtainColor = Color(0xFF080C14)
private fun phase(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerpFloat(a: Float, b: Float, p: Float) = a + (b - a) * p

/** Content is always composed underneath the opaque animated curtain. */
@Composable
fun GrowthChartSplashScreen(
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    val timeline = remember { Animatable(0f) }
    var showing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        timeline.animateTo(1650f, tween(1650, easing = LinearEasing))
        showing = false
        onSplashFinished()
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (showing) Canvas(Modifier.fillMaxSize()) {
            val t = timeline.value
            val cx = size.width / 2f
            val h = size.height
            val r = size.minDimension * .055f
            val frogY = h * .56f
            val mouth = frogY + r
            val apex = h * .18f
            val catchY = h * .30f
            if (t < 880f) {
                drawRect(curtainColor)
                val squash = phase(t, 0f, 155f)
                val fade = 1f - phase(t, 520f, 800f)
                val sx = if (squash < .5f) lerpFloat(1f, 1.12f, squash * 2f) else lerpFloat(1.12f, 1f, (squash - .5f) * 2f)
                val sy = if (squash < .5f) lerpFloat(1f, .86f, squash * 2f) else lerpFloat(.86f, 1f, (squash - .5f) * 2f)
                drawEmojiFrog(Offset(cx, frogY), r * 2.2f, fade, sx, sy)
                val rising = FastOutSlowInEasing.transform(phase(t, 0f, 520f))
                val falling = FastOutSlowInEasing.transform(phase(t, 520f, 880f))
                val coinY = if (t < 520f) lerpFloat(mouth, apex, rising) else lerpFloat(apex, catchY, falling)
                drawSpeedTrails(cx, coinY, r, if (t < 520f) rising else falling, t >= 520f)
                drawGoldenCoin(Offset(cx, coinY), r, if (t < 520f) cos(rising * PI.toFloat() * 4f) else 1f)
            } else {
                // Begin with the coin at the exact end of phase two, without a position jump.
                val p = FastOutSlowInEasing.transform(phase(t, 880f, 1500f))
                val edge = lerpFloat(catchY + r * .15f, h + r * 3f, p)
                val depth = r * .9f * sin(PI.toFloat() * p).coerceAtLeast(0f)
                drawElasticCurtain(edge, depth)
                val coinY = edge - r * .15f + depth
                if (coinY - r < h) {
                    drawSpeedTrails(cx, coinY, r, p, true)
                    drawGoldenCoin(Offset(cx, coinY), r, 1f)
                }
            }
        }
    }
}

private fun DrawScope.drawEmojiFrog(center: Offset, radius: Float, alpha: Float, sx: Float, sy: Float) {
    if (alpha <= 0f) return
    withTransform({ translate(center.x, center.y); scale(sx, sy, Offset.Zero) }) {
        drawOval(Color(0xFF15803D).copy(alpha = alpha), Offset(-radius, -radius * .68f), Size(radius * 2f, radius * 1.55f))
        drawOval(Color(0xFF22C55E).copy(alpha = alpha), Offset(-radius * .84f, -radius * .64f), Size(radius * 1.68f, radius * 1.08f))
        for (side in listOf(-.53f, .53f)) {
            val x = side * radius
            drawCircle(Color(0xFF22C55E).copy(alpha = alpha), radius * .37f, Offset(x, -radius * .6f))
            drawCircle(Color.White.copy(alpha = alpha), radius * .22f, Offset(x, -radius * .64f))
            drawCircle(Color(0xFF102A1C).copy(alpha = alpha), radius * .105f, Offset(x + radius * .04f, -radius * .64f))
        }
        val smile = Path().apply { moveTo(-radius * .4f, radius * .19f); quadraticBezierTo(0f, radius * .5f, radius * .4f, radius * .19f) }
        drawPath(smile, Color(0xFF064E3B).copy(alpha = alpha), style = Stroke(radius * .045f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawGoldenCoin(center: Offset, r: Float, rotationWidth: Float) {
    val w = (kotlin.math.abs(rotationWidth) * r).coerceAtLeast(r * .08f)
    drawOval(Color(0xFFB45309), Offset(center.x - w, center.y - r), Size(w * 2f, r * 2f))
    drawOval(Color(0xFFFBBF24), Offset(center.x - w * .83f, center.y - r * .85f), Size(w * 1.66f, r * 1.7f))
    if (w > r * .3f) drawLine(Color(0xFFFEF3C7), Offset(center.x, center.y - r * .47f), Offset(center.x, center.y + r * .47f), strokeWidth = r * .12f)
}

private fun DrawScope.drawSpeedTrails(cx: Float, y: Float, r: Float, progress: Float, falling: Boolean) {
    val a = (.4f * sin(PI.toFloat() * progress).coerceAtLeast(0f))
    if (a <= 0f) return
    val direction = if (falling) -1f else 1f
    for (i in -1..1) {
        val x = cx + i * r * .55f
        drawLine(Color(0xFFFBBF24).copy(alpha = a), Offset(x, y + direction * r * 1.4f), Offset(x, y + direction * r * (2.8f + kotlin.math.abs(i) * .45f)), strokeWidth = r * .075f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawElasticCurtain(edge: Float, depth: Float) {
    val cx = size.width / 2f
    val curtain = Path().apply {
        moveTo(0f, edge)
        cubicTo(size.width * .27f, edge, cx - size.width * .18f, edge + depth, cx, edge + depth)
        cubicTo(cx + size.width * .18f, edge + depth, size.width * .73f, edge, size.width, edge)
        lineTo(size.width, size.height + depth + 1f)
        lineTo(0f, size.height + depth + 1f)
        close()
    }
    drawPath(curtain, curtainColor)
}

@Preview(showBackground = true)
@Composable
fun SplashDemoPreview() {
    GrowthChartSplashScreen(onSplashFinished = {}) { Text("App content") }
}
