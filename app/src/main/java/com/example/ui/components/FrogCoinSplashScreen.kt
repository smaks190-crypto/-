package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.abs
import kotlin.math.sin

private val Night = Color(0xFF0D1716)
private val NightSoft = Color(0xFF172522)
private val Leaf = Color(0xFF568F69)
private val LeafLight = Color(0xFF89BB83)
private val Shadow = Color(0xFF2A654A)
private val Cream = Color(0xFFF0EAD4)
private val CoinGold = Color(0xFFF2BA51)
private val CoinLight = Color(0xFFFFDC83)
private val CoinEdge = Color(0xFF9D682E)

private fun smooth(a: Float, b: Float, t: Float): Float {
    val p = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

private fun DrawScope.coin(x: Float, y: Float, r: Float, turn: Float) {
    val width = (abs(kotlin.math.cos(turn * Math.PI.toFloat() * 2f)) * .90f + .10f) * r
    drawOval(CoinEdge, Offset(x - width * 1.08f, y - r * .94f), Size(width * 2.16f, r * 2.08f))
    drawOval(CoinGold, Offset(x - width, y - r), Size(width * 2f, r * 2f))
    if (width > r * .38f) {
        drawOval(CoinLight, Offset(x - width * .78f, y - r * .78f), Size(width * 1.56f, r * 1.56f), style = Stroke(r * .09f))
        val p = r * .15f
        drawRect(NightSoft, Offset(x - p * 1.5f, y - p), Size(p, p))
        drawRect(NightSoft, Offset(x - p * .5f, y - p * 1.5f), Size(p, p * 1.5f))
        drawRect(NightSoft, Offset(x + p * .5f, y - p), Size(p, p))
        drawRect(NightSoft, Offset(x - p * 1.5f, y), Size(p * 3f, p * .7f))
    }
}

/** One intentionally restrained, hand-drawn mascot; the arm has its own keyframed gesture. */
private fun DrawScope.frog(cx: Float, cy: Float, s: Float, gesture: Float, blink: Float) {
    withTransform({ translate(cx, cy); scale(s, s) }) {
        // Feet and silhouette, designed at 200 x 200 logical pixels.
        drawOval(Shadow, Offset(-73f, 59f), Size(146f, 29f))
        drawOval(Leaf, Offset(-81f, -17f), Size(162f, 112f))
        drawOval(LeafLight, Offset(-53f, 40f), Size(106f, 40f))
        drawCircle(Leaf, 30f, Offset(-47f, -32f))
        drawCircle(Leaf, 30f, Offset(47f, -32f))
        drawOval(Leaf, Offset(-78f, -52f), Size(156f, 111f))
        drawOval(LeafLight, Offset(-65f, -43f), Size(130f, 90f))
        listOf(-47f, 47f).forEach { x ->
            drawCircle(Cream, 17f, Offset(x, -37f))
            drawCircle(Night, 8f, Offset(x + 2f, -35f))
            if (blink > 0f) drawLine(Leaf, Offset(x - 17f, -37f), Offset(x + 17f, -37f), 34f * blink)
        }
        val smile = Path().apply {
            moveTo(-22f, 13f)
            quadraticTo(0f, 31f, 22f, 13f)
        }
        drawPath(smile, Night, style = Stroke(3.5f))
        drawCircle(Shadow, 3.5f, Offset(-29f, 6f))
        drawCircle(Shadow, 3.5f, Offset(29f, 6f))
        // The right hand anticipates the throw and follows through.
        val handY = 38f - gesture * 84f
        val handX = 77f + gesture * 7f
        drawLine(Shadow, Offset(55f, 57f), Offset(handX, handY), 19f)
        drawCircle(LeafLight, 12f, Offset(handX, handY))
    }
}

@Composable
fun FrogCoinSplashScreen(isExiting: Boolean, onExitFinished: () -> Unit) {
    val intro = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(800, easing = LinearEasing))
    }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            if (intro.value < 1f) intro.animateTo(1f, tween(800, easing = LinearEasing))
            delay(30)
            exit.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            onExitFinished()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val short = minOf(w, h)
            val centerX = w / 2f
            val mascotY = h * .54f
            val s = short / 315f
            val t = intro.value
            val reveal = exit.value
            val apexY = h * .22f
            val contactY = 0f
            val curtainY = if (isExiting) h * reveal else 0f
            drawRect(Night, Offset(0f, curtainY), Size(w, h - curtainY.coerceAtMost(h)))
            if (curtainY >= h) return@Canvas
            val rise = smooth(0f, .6875f, t)
            val fall = smooth(.6875f, 1f, t)
            val frogFade = 1f - smooth(.6875f, 1f, t)
            val squash = when {
                t < .15f -> 1f - .08f * (t / .15f)
                t < .32f -> .92f + .13f * ((t - .15f) / .17f)
                t < .52f -> 1.05f - .05f * ((t - .32f) / .20f)
                else -> 1f
            }
            // The mascot stays visible throughout the throw and fades only at the apex.
            withTransform({
                translate(centerX, mascotY)
                scale(1f - .15f * (1f - frogFade), squash * (1f - .15f * (1f - frogFade)))
                translate(-centerX, -mascotY)
            }) {
                // Draw into a layer to apply alpha to the complete character.
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), androidx.compose.ui.graphics.Paint().apply { alpha = frogFade })
                frog(centerX, mascotY, s, 0f, 0f)
                drawContext.canvas.restore()
            }
            val mouthY = mascotY + 57f * s
            val coinY = if (t <= .6875f) mouthY + (apexY - mouthY) * rise else apexY + (contactY - apexY) * fall
            val spin = t * 2.6f + reveal * 1.4f
            if (!isExiting || reveal <= 0f) {
                coin(centerX, coinY, short * .055f, spin)
                val trail = if (t <= .6875f) rise else fall
                drawLine(CoinLight.copy(alpha = .24f * trail), Offset(centerX, coinY + short * .07f), Offset(centerX, coinY + short * .18f), short * .006f)
                if (t > .6875f) {
                    drawLine(CoinLight.copy(alpha = .36f * fall), Offset(centerX - short * .04f, coinY - short * .17f), Offset(centerX - short * .04f, coinY - short * .08f), short * .004f)
                    drawLine(CoinLight.copy(alpha = .36f * fall), Offset(centerX + short * .04f, coinY - short * .21f), Offset(centerX + short * .04f, coinY - short * .09f), short * .004f)
                }
            } else {
                // Preserve the existing curtain reveal: coin stays on its leading edge.
                coin(centerX, curtainY, short * .055f * (1f + reveal * .24f), spin)
            }
        }
    }
}
