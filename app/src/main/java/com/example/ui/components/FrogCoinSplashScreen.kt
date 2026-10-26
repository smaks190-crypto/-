package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
        // A small anticipation, a quick throw, a weightless apex, then falling.
        intro.animateTo(1f, tween(1450, easing = LinearEasing))
    }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            // Wait for the toss before transitioning; no separate, disconnected coin.
            if (intro.value < 1f) intro.animateTo(1f, tween(240, easing = LinearEasing))
            delay(80)
            exit.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
            onExitFinished()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val short = minOf(w, h)
            val mascotScale = short / 315f
            val mascotY = h * .52f
            val t = intro.value
            val reveal = exit.value
            // Curtain is attached to the falling coin. Actual app is drawn behind this overlay.
            val curtainY = if (isExiting) h * reveal else 0f
            drawRect(Night, Offset(0f, curtainY), Size(w, h - curtainY))
            if (curtainY >= h) return@Canvas
            withTransform({ translate(0f, curtainY) }) {
                drawCircle(NightSoft, short * .28f, Offset(w * .5f, mascotY), alpha = .42f)
                val gesture = smooth(.08f, .30f, t)
                val blink = (1f - abs(t - .19f) / .035f).coerceIn(0f, 1f)
                frog(w * .5f, mascotY, mascotScale, gesture, blink)

                // Single coin throughout the sequence. Its position and spin remain continuous.
                val release = smooth(.19f, .32f, t)
                val flight = smooth(.30f, .98f, t)
                val airborne = (t - .30f).coerceIn(0f, .7f) / .7f
                val startY = mascotY + (38f - gesture * 84f) * mascotScale
                val apexY = mascotY - short * .55f
                val landingY = mascotY - short * .09f
                val arcY = apexY + (landingY - apexY) * (airborne * airborne)
                val coinY = startY * (1f - release) + (apexY + (arcY - apexY) * flight) * release
                val coinX = w * .5f + (77f + gesture * 7f) * mascotScale * (1f - release)
                val spinning = t * 2.1f + reveal * 1.4f
                // While the curtain falls, coin stays locked to the curtain's upper edge.
                if (isExiting && reveal > 0f) {
                    coin(w * .5f, 0f, short * .055f * (1f + reveal * .24f), spinning)
                } else {
                    coin(coinX, coinY, short * .055f, spinning)
                }
                if (t > .30f && t < .88f && !isExiting) {
                    val trail = (1f - abs(t - .60f) / .30f).coerceIn(0f, 1f)
                    drawLine(CoinLight.copy(alpha = .16f * trail), Offset(coinX, coinY + short * .08f), Offset(coinX, coinY + short * .17f), short * .007f)
                }
            }
        }
    }
}
