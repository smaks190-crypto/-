package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0B1413)
private val Frog = Color(0xFF75B86B)
private val FrogLight = Color(0xFFACD58A)
private val Gold = Color(0xFFF5BA43)
private val GoldLight = Color(0xFFFFDF83)

// Coordinates are deliberately simple and shared by the splash and launcher icon.
internal fun DrawScope.drawCoin(cx: Float, cy: Float, radius: Float) {
    drawCircle(Color(0xFF9B631F), radius * 1.08f, Offset(cx, cy + radius * .08f))
    drawCircle(Gold, radius, Offset(cx, cy))
    drawCircle(GoldLight, radius * .77f, Offset(cx, cy))
    drawCircle(Gold, radius * .62f, Offset(cx, cy))
    // Tiny crown made of three square pixels.
    val p = radius * .19f
    drawRect(Ink, Offset(cx - p * 1.6f, cy - p), Size(p, p))
    drawRect(Ink, Offset(cx - p * .5f, cy - p * 1.5f), Size(p, p * 1.5f))
    drawRect(Ink, Offset(cx + p * .6f, cy - p), Size(p, p))
    drawRect(Ink, Offset(cx - p * 1.6f, cy), Size(p * 3.2f, p * .8f))
}

internal fun DrawScope.drawCoinFrog(cx: Float, cy: Float, unit: Float, armUp: Boolean) {
    drawOval(Frog, Offset(cx - unit * .83f, cy - unit * .15f), Size(unit * 1.66f, unit * 1.15f))
    drawCircle(Frog, unit * .28f, Offset(cx - unit * .43f, cy - unit * .25f))
    drawCircle(Frog, unit * .28f, Offset(cx + unit * .43f, cy - unit * .25f))
    drawOval(FrogLight, Offset(cx - unit * .52f, cy + unit * .25f), Size(unit * 1.04f, unit * .56f))
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(cx + side * unit * .43f, cy - unit * .29f)
        drawCircle(Color(0xFFFFF2CB), unit * .16f, eye)
        drawCircle(Ink, unit * .085f, Offset(eye.x + side * unit * .025f, eye.y))
    }
    drawLine(Ink, Offset(cx - unit * .24f, cy + unit * .21f), Offset(cx + unit * .24f, cy + unit * .21f), unit * .035f)
    val handY = if (armUp) cy - unit * .49f else cy + unit * .37f
    drawLine(Frog, Offset(cx + unit * .48f, cy + unit * .43f), Offset(cx + unit * .83f, handY), unit * .16f)
    drawCircle(FrogLight, unit * .105f, Offset(cx + unit * .83f, handY))
}

@Composable
fun FrogCoinSplashScreen(isExiting: Boolean, onExitFinished: () -> Unit) {
    val toss = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(Unit) {
        toss.animateTo(1f, tween(1050, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            // The falling coin drags the dark splash curtain down, revealing the actual UI above it.
            reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            onExitFinished()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().offset(y = screenHeight * reveal.value).background(Ink),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(220.dp)) {
                val u = size.minDimension * .40f
                drawCoinFrog(size.width * .5f, size.height * .58f, u, toss.value > .18f)
                if (!isExiting) {
                    // Upward arc followed by a small downward turn.
                    val t = toss.value
                    val arc = 4f * t * (1f - t)
                    val y = size.height * (.40f - .34f * arc)
                    drawCoin(size.width * (.77f - .12f * t), y, u * .23f)
                }
            }
        }
        if (isExiting) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * reveal.value
                drawCoin(size.width * .5f, y.coerceAtLeast(0f), 18.dp.toPx())
            }
        }
    }
}
