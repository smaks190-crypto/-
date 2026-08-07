package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WalletSplashIcon(
    size: Dp = 140.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    // Constant scale = 1.0f to eliminate sudden scale jump
    val entryAlpha = remember { Animatable(if (animated) 0f else 1f) }

    val bill1Anim = remember { Animatable(0f) } // 5000 ₽ (first, parallel to wallet)
    val bill2Anim = remember { Animatable(0f) } // 500 ₽
    val bill3Anim = remember { Animatable(0f) } // 50 ₽
    val flyOffAnim = remember { Animatable(0f) } // Outro slide DOWN

    if (animated) {
        LaunchedEffect(Unit) {
            // Smooth entry fade without scale jump
            launch {
                entryAlpha.animateTo(1f, tween(250, easing = LinearOutSlowInEasing))
            }
            delay(200)

            // 1. Banknote 1 (5000 ₽) slides straight down into wallet
            launch {
                bill1Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(160)

            // 2. Banknote 2 (500 ₽) un-rotates to 0° and slides straight down into wallet
            launch {
                bill2Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(160)

            // 3. Banknote 3 (50 ₽) un-rotates to 0° and slides straight down into wallet
            launch {
                bill3Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(350)

            // 4. Outro: Wallet smoothly slides DOWN off-screen
            launch {
                flyOffAnim.animateTo(1f, tween(450, easing = FastOutLinearInEasing))
            }
        }
    }

    val flyOffVal = flyOffAnim.value
    // Fade out as it nears bottom exit
    val alphaVal = entryAlpha.value * (if (flyOffVal > 0.5f) 1f - ((flyOffVal - 0.5f) / 0.5f) else 1f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                alpha = alphaVal.coerceIn(0f, 1f)
                // Slide DOWN off the bottom of the screen
                translationY = flyOffVal * size.toPx() * 3.5f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Dimensions
            val walletW = w * 0.65f
            val walletH = h * 0.34f
            val walletX = (w - walletW) / 2f
            val walletY = h * 0.52f

            // 1. WALLET BACK INTERIOR SLOT
            drawRoundRect(
                color = DarkBg,
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 2. BANKNOTES (Physical sliding down behind wallet front body)

            // Banknote 3: 50 ₽ (Cyan-Blue #0284C7) - Initial angle +8° -> slides down & rotates to 0°
            val b3Progress = bill3Anim.value
            val b3W = w * 0.48f
            val b3H = h * 0.22f
            val b3StartY = walletY - b3H * 0.85f
            val b3EndY = walletY + b3H * 0.60f // Fully inside wallet front body
            val b3Y = if (animated) b3StartY + b3Progress * (b3EndY - b3StartY) else b3StartY
            val b3Rot = if (animated) 8f * (1f - b3Progress) else 8f

            withTransform({
                translate(left = w / 2f, top = b3Y)
                rotate(degrees = b3Rot, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = Color(0xFF0284C7),
                    topLeft = Offset(-b3W / 2f, -b3H / 2f),
                    size = Size(b3W, b3H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                // Light blue accent stripe
                drawRoundRect(
                    color = Color(0xFFBAE6FD),
                    topLeft = Offset(-b3W * 0.28f, -b3H / 2f),
                    size = Size(b3W * 0.12f, b3H),
                )
                // White badge with '50 ₽'
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b3W * 0.08f, -b3H * 0.32f),
                    size = Size(b3W * 0.35f, b3H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Banknote 2: 500 ₽ (Purple-Violet #7C3AED) - Initial angle -8° -> slides down & rotates to 0°
            val b2Progress = bill2Anim.value
            val b2W = w * 0.52f
            val b2H = h * 0.22f
            val b2StartY = walletY - b2H * 0.58f
            val b2EndY = walletY + b2H * 0.60f // Fully inside wallet front body
            val b2Y = if (animated) b2StartY + b2Progress * (b2EndY - b2StartY) else b2StartY
            val b2Rot = if (animated) -8f * (1f - b2Progress) else -8f

            withTransform({
                translate(left = w / 2f, top = b2Y)
                rotate(degrees = b2Rot, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = Color(0xFF7C3AED),
                    topLeft = Offset(-b2W / 2f, -b2H / 2f),
                    size = Size(b2W, b2H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                // Light purple stripe
                drawRoundRect(
                    color = Color(0xFFC7D2FE),
                    topLeft = Offset(b2W * 0.22f, -b2H / 2f),
                    size = Size(b2W * 0.14f, b2H),
                )
                // White badge with '500 ₽'
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b2W * 0.30f, -b2H * 0.32f),
                    size = Size(b2W * 0.38f, b2H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Banknote 1: 5000 ₽ (Red-Orange Rose500) - STRICTLY PARALLEL (0°) -> slides straight down
            val b1Progress = bill1Anim.value
            val b1W = w * 0.58f
            val b1H = h * 0.22f
            val b1StartY = walletY - b1H * 0.30f
            val b1EndY = walletY + b1H * 0.60f // Fully inside wallet front body
            val b1Y = if (animated) b1StartY + b1Progress * (b1EndY - b1StartY) else b1StartY

            withTransform({
                translate(left = w / 2f, top = b1Y)
            }) {
                drawRoundRect(
                    color = Rose500,
                    topLeft = Offset(-b1W / 2f, -b1H / 2f),
                    size = Size(b1W, b1H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                // Gold accent stripe
                drawRoundRect(
                    color = Amber400,
                    topLeft = Offset(-b1W * 0.35f, -b1H / 2f),
                    size = Size(b1W * 0.14f, b1H),
                )
                // White badge with '5000 ₽'
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b1W * 0.10f, -b1H * 0.32f),
                    size = Size(b1W * 0.42f, b1H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // 3. BIFOLD LEATHER WALLET FRONT BODY (Covers banknotes as they slide down)
            drawRoundRect(
                color = Color(0xFF1E1B4B),
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // Leather top fold line
            drawRoundRect(
                color = Color(0xFF312E81),
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, 4.dp.toPx())
            )

            // Wallet Front Outline
            drawRoundRect(
                color = Indigo500,
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Stitching lines along top & bottom edges
            val stitchCount = 14
            val stitchStep = walletW / (stitchCount + 1)
            for (i in 1..stitchCount) {
                val sx = walletX + i * stitchStep
                drawRect(
                    color = Color(0xFFA5B4FC).copy(alpha = 0.8f),
                    topLeft = Offset(sx, walletY + 3.dp.toPx()),
                    size = Size(2.dp.toPx(), 1.dp.toPx())
                )
                drawRect(
                    color = Color(0xFFA5B4FC).copy(alpha = 0.8f),
                    topLeft = Offset(sx, walletY + walletH - 4.dp.toPx()),
                    size = Size(2.dp.toPx(), 1.dp.toPx())
                )
            }

            // 4. WALLET LATCH STRAP & CLASP BUTTON (Right side)
            val claspW = walletW * 0.22f
            val claspH = walletH * 0.36f
            val claspX = walletX + walletW * 0.78f
            val claspY = walletY + walletH * 0.32f

            drawRoundRect(
                color = Color(0xFF312E81),
                topLeft = Offset(claspX, claspY),
                size = Size(claspW, claspH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Indigo500,
                topLeft = Offset(claspX, claspY),
                size = Size(claspW, claspH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Clasp Gold Metal Button
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = claspH * 0.24f,
                center = Offset(claspX + claspW * 0.72f, claspY + claspH * 0.50f)
            )
            drawCircle(
                color = Color(0xFFFCD34D),
                radius = claspH * 0.14f,
                center = Offset(claspX + claspW * 0.72f, claspY + claspH * 0.50f)
            )
        }
    }
}

// Wallet loading animation for AI analysis: fan folds bill by bill, then snaps open
@Composable
fun AiWalletLoadingIcon(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_wallet_fan")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Sequence (1600ms):
    // 0.00..0.22: Bill 3 (Right, +22°) folds in to 0°
    // 0.18..0.40: Bill 1 (Left, -22°) folds in to 0°
    // 0.36..0.58: Bill 2 (Center, 0°) folds down into wallet
    // 0.58..0.72: Hold fully folded inside wallet
    // 0.72..0.86: REZKO (suddenly) snap open back into fan (-22°, 0°, +22°)!
    // 0.86..1.00: Brief hold in open fan state, then loop.

    val fold3Progress = ((phase - 0.00f) / 0.22f).coerceIn(0f, 1f)
    val fold1Progress = ((phase - 0.18f) / 0.22f).coerceIn(0f, 1f)
    val fold2Progress = ((phase - 0.36f) / 0.22f).coerceIn(0f, 1f)

    val isSnapping = phase >= 0.72f
    val rawSnap = if (phase < 0.72f) 0f else if (phase > 0.86f) 1f else (phase - 0.72f) / 0.14f
    val snapFactor = FastOutSlowInEasing.transform(rawSnap)

    val b3Fold = if (isSnapping) (1f - snapFactor) else fold3Progress
    val b1Fold = if (isSnapping) (1f - snapFactor) else fold1Progress
    val b2Fold = if (isSnapping) (1f - snapFactor) else fold2Progress

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val walletW = w * 0.70f
        val walletH = h * 0.38f
        val walletX = (w - walletW) / 2f
        val walletY = h * 0.52f

        // Wallet Back Slot
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // --- Banknote 1: Left (-22° -> 0°) ---
        val b1Rot = -22f * (1f - b1Fold)
        val b1W = w * 0.50f
        val b1H = h * 0.24f
        val b1OffsetX = (-w * 0.12f) * (1f - b1Fold)
        val b1Y = walletY - b1H * 0.55f + (b1H * 0.45f * b1Fold)

        withTransform({
            translate(left = w / 2f + b1OffsetX, top = b1Y)
            rotate(degrees = b1Rot, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(-b1W / 2f, -b1H / 2f),
                size = Size(b1W, b1H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFBAE6FD),
                topLeft = Offset(-b1W * 0.30f, -b1H / 2f),
                size = Size(b1W * 0.12f, b1H)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(-b1W * 0.08f, -b1H * 0.30f),
                size = Size(b1W * 0.35f, b1H * 0.42f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // --- Banknote 3: Right (+22° -> 0°) ---
        val b3Rot = 22f * (1f - b3Fold)
        val b3W = w * 0.52f
        val b3H = h * 0.24f
        val b3OffsetX = (w * 0.12f) * (1f - b3Fold)
        val b3Y = walletY - b3H * 0.55f + (b3H * 0.45f * b3Fold)

        withTransform({
            translate(left = w / 2f + b3OffsetX, top = b3Y)
            rotate(degrees = b3Rot, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                color = Rose500,
                topLeft = Offset(-b3W / 2f, -b3H / 2f),
                size = Size(b3W, b3H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFFDE68A),
                topLeft = Offset(-b3W * 0.30f, -b3H / 2f),
                size = Size(b3W * 0.12f, b3H)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(-b3W * 0.08f, -b3H * 0.30f),
                size = Size(b3W * 0.38f, b3H * 0.42f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // --- Banknote 2: Center (0°) ---
        val b2W = w * 0.56f
        val b2H = h * 0.24f
        val b2Y = walletY - b2H * 0.70f + (b2H * 0.45f * b2Fold)

        withTransform({
            translate(left = w / 2f, top = b2Y)
        }) {
            drawRoundRect(
                color = Color(0xFF7C3AED),
                topLeft = Offset(-b2W / 2f, -b2H / 2f),
                size = Size(b2W, b2H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFC7D2FE),
                topLeft = Offset(b2W * 0.20f, -b2H / 2f),
                size = Size(b2W * 0.14f, b2H)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(-b2W * 0.30f, -b2H * 0.30f),
                size = Size(b2W * 0.38f, b2H * 0.42f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Wallet Front Leather Body
        drawRoundRect(
            color = Color(0xFF1E1B4B),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFF312E81),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, 3.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFF818CF8),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val stitchCount = 10
        val stitchStep = walletW / (stitchCount + 1)
        for (i in 1..stitchCount) {
            val sx = walletX + i * stitchStep
            drawRect(
                color = Color(0xFFA5B4FC).copy(alpha = 0.8f),
                topLeft = Offset(sx, walletY + 2.dp.toPx()),
                size = Size(2.dp.toPx(), 1.dp.toPx())
            )
            drawRect(
                color = Color(0xFFA5B4FC).copy(alpha = 0.8f),
                topLeft = Offset(sx, walletY + walletH - 3.dp.toPx()),
                size = Size(2.dp.toPx(), 1.dp.toPx())
            )
        }

        val claspW = walletW * 0.22f
        val claspH = walletH * 0.36f
        val claspX = walletX + walletW * 0.78f
        val claspY = walletY + walletH * 0.32f

        drawRoundRect(
            color = Color(0xFF312E81),
            topLeft = Offset(claspX, claspY),
            size = Size(claspW, claspH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = claspH * 0.24f,
            center = Offset(claspX + claspW * 0.72f, claspY + claspH * 0.50f)
        )
    }
}

// Alias for backwards compatibility with ExpenseSharesScreen or other callers
@Composable
fun GrowthChartIcon(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    AiWalletLoadingIcon(size = size, modifier = modifier)
}

@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean = false,
    onExitFinished: () -> Unit = {}
) {
    val exitAnimProgress = remember { Animatable(0f) }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
            onExitFinished()
        }
    }

    val progress = exitAnimProgress.value
    val currentAlpha = if (isExiting) (1f - progress).coerceIn(0f, 1f) else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = currentAlpha
            }
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        // ONLY the wallet icon centered - text removed completely as requested
        WalletSplashIcon(
            size = 150.dp,
            animated = true
        )
    }
}
