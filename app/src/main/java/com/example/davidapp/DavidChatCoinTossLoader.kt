package com.example.davidapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.BrandFrogPaths
import com.example.ui.components.drawBrandFrogVector
import com.example.ui.theme.Emerald400
import com.example.ui.theme.ObsidianSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Анимационный индикатор ожидания ответа Давида Жабова без какого-либо текста:
 * фирменная жаба циклично подбрасывает двустороннюю объемную 3D золотую монетку
 * (Аверс: знак рубля ₽, Реверс: чеканный золотой барельеф жабы Давида) с вертикальным кувырком и ловит её обратно.
 */
@Composable
fun DavidCoinTossLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "david_coin_toss_loader")
    val tossPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coin_toss_phase"
    )

    val frogPaths = remember { BrandFrogPaths() }
    val rubleShadowPath = remember { Path() }
    val rubleBowlPath = remember { Path() }
    val rubleHighlightPath = remember { Path() }
    val embossedFrogSmilePath = remember { Path() }
    val shinePath = remember { Path() }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp),
        color = ObsidianSurface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Emerald400.copy(alpha = 0.48f),
                    Emerald400.copy(alpha = 0.16f)
                )
            )
        ),
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("david_coin_toss_loader")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val p = tossPhase
                val w = size.width
                val h = size.height

                // Парабола полета монетки (0f = во рту жабы, 1f = пик взлета над головой)
                val flightHeight = sin(p * PI.toFloat()).coerceIn(0f, 1f)

                // Упругая деформация жабы (Squash & Stretch) при броске и ловле монетки
                val frogScaleX: Float
                val frogScaleY: Float
                when {
                    p < 0.16f -> {
                        val k = p / 0.16f
                        val s = sin(k * PI.toFloat())
                        frogScaleX = 1f - 0.08f * s
                        frogScaleY = 1f + 0.10f * s
                    }
                    p > 0.82f -> {
                        val k = (p - 0.82f) / 0.18f
                        val s = sin(k * PI.toFloat())
                        frogScaleX = 1f + 0.10f * s
                        frogScaleY = 1f - 0.09f * s
                    }
                    else -> {
                        frogScaleX = 1f
                        frogScaleY = 1f
                    }
                }

                // Открытие рта в момент вылета и ловли монетки
                val mouthOpen = when {
                    p < 0.22f -> sin((p / 0.22f) * PI.toFloat())
                    p > 0.78f -> sin(((p - 0.78f) / 0.22f) * PI.toFloat())
                    else -> 0.15f
                }.coerceIn(0f, 1f)

                // Зрачки жабы следят за высотой монетки
                val pupilOffset = -flightHeight * 0.95f

                // Отрисовка фирменной жабы в нижней части мини-холста
                withTransform({
                    translate(left = 0f, top = h * 0.18f)
                    scale(
                        scaleX = frogScaleX * 0.76f,
                        scaleY = frogScaleY * 0.76f,
                        pivot = Offset(w / 2f, h * 0.56f)
                    )
                }) {
                    drawBrandFrogVector(
                        paths = frogPaths,
                        mouthOpenProgress = mouthOpen,
                        pupilOffsetYRatio = pupilOffset
                    )
                }

                // --- ВЕРТИКАЛЬНОЕ 3D-ВРАЩЕНИЕ ДВУСТОРОННЕЙ МОНЕТКИ (вокруг горизонтальной оси X) ---
                val coinStartY = h * 0.67f
                val coinPeakY = h * 0.15f
                val coinY = coinStartY - (coinStartY - coinPeakY) * flightHeight
                val coinX = w / 2f
                val coinRadius = w * 0.165f

                // 2 полных оборота по вертикали за один цикл броска: ширина всегда = 1f, сжимается только высота scaleY
                val spinAngle = p * 4.0f * PI.toFloat()
                val flipCos = cos(spinAngle)
                val flipSin = sin(spinAngle)
                val faceScaleY = abs(flipCos).coerceAtLeast(0.04f)

                // Толщина 3D-гурта монеты и его вертикальный сдвиг
                val rimThickness = coinRadius * 0.36f
                val rimOffsetY = flipSin * rimThickness
                val ovalHeight = coinRadius * 2f * faceScaleY

                // Золотое свечение вокруг подлетающей монетки
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFBBF24).copy(alpha = 0.26f + 0.26f * flightHeight),
                            Color.Transparent
                        ),
                        center = Offset(coinX, coinY),
                        radius = coinRadius * 1.95f
                    ),
                    radius = coinRadius * 1.95f,
                    center = Offset(coinX, coinY)
                )

                // --- СЛОЙ 1: Объемный 3D-гурт (цилиндрическое ребро монеты из 7 промежуточных овалов + 7 насечек) ---
                val rimGradientBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF78350F),
                        Color(0xFFB45309),
                        Color(0xFFF59E0B),
                        Color(0xFFB45309),
                        Color(0xFF78350F)
                    ),
                    startX = coinX - coinRadius,
                    endX = coinX + coinRadius
                )

                for (step in 0..6) {
                    val fraction = step / 6f
                    val layerCenterY = coinY + rimOffsetY * (1f - fraction)
                    drawOval(
                        brush = rimGradientBrush,
                        topLeft = Offset(coinX - coinRadius, layerCenterY - ovalHeight / 2f),
                        size = Size(coinRadius * 2f, ovalHeight)
                    )
                }

                // 7 вертикальных насечек гурта вдоль боковой стенки монеты
                val notchStrokeW = (coinRadius * 0.095f).coerceAtLeast(1f)
                for (notchIdx in 0..6) {
                    val normX = -0.78f + (1.56f * notchIdx / 6f)
                    val nx = coinX + normX * coinRadius
                    val curveFactor = kotlin.math.sqrt((1f - normX * normX).coerceAtLeast(0f))
                    val arcOffsetY = (ovalHeight * 0.5f) * curveFactor * (if (rimOffsetY >= 0f) 1f else -1f)
                    drawLine(
                        color = Color(0xFFFDE047).copy(alpha = 0.55f),
                        start = Offset(nx, coinY - rimOffsetY * 0.25f + arcOffsetY),
                        end = Offset(nx, coinY + rimOffsetY + arcOffsetY),
                        strokeWidth = notchStrokeW,
                        cap = StrokeCap.Round
                    )
                }

                // --- СЛОЙ 2: Лицевая / оборотная грань монеты ---
                val faceCenterY = coinY - rimOffsetY * 0.25f
                withTransform({
                    translate(left = coinX, top = faceCenterY)
                    scale(scaleX = 1f, scaleY = faceScaleY, pivot = Offset.Zero)
                }) {
                    // Внешний золотой обод (#FDE047 -> #F59E0B -> #D97706)
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFDE047),
                                Color(0xFFF59E0B),
                                Color(0xFFD97706)
                            ),
                            start = Offset(-coinRadius, -coinRadius),
                            end = Offset(coinRadius, coinRadius)
                        ),
                        radius = coinRadius,
                        center = Offset.Zero
                    )

                    // Утопленное внутреннее кольцо (#B45309) со светлой фаской (#FEF08A)
                    val innerRingRadius = coinRadius * 0.82f
                    drawCircle(
                        color = Color(0xFFB45309),
                        radius = innerRingRadius,
                        center = Offset.Zero
                    )
                    drawCircle(
                        color = Color(0xFFFEF08A).copy(alpha = 0.70f),
                        radius = innerRingRadius,
                        center = Offset(0f, -coinRadius * 0.03f),
                        style = Stroke(width = (coinRadius * 0.06f).coerceAtLeast(0.8f))
                    )

                    // Центральное золотое поле
                    val coreFieldRadius = coinRadius * 0.75f
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFEF08A),
                                Color(0xFFFBBF24),
                                Color(0xFFF59E0B)
                            ),
                            start = Offset(-coreFieldRadius * 0.5f, -coreFieldRadius),
                            end = Offset(coreFieldRadius * 0.5f, coreFieldRadius)
                        ),
                        radius = coreFieldRadius,
                        center = Offset.Zero
                    )

                    // Диагональный пробегающий блик света при каждом кувырке
                    val glintShift = flipSin * coinRadius * 0.52f
                    shinePath.reset()
                    shinePath.moveTo(-coinRadius * 0.50f + glintShift, -coinRadius * 0.58f)
                    shinePath.lineTo(-coinRadius * 0.16f + glintShift, -coinRadius * 0.70f)
                    shinePath.lineTo(coinRadius * 0.46f + glintShift, coinRadius * 0.54f)
                    shinePath.lineTo(coinRadius * 0.14f + glintShift, coinRadius * 0.68f)
                    shinePath.close()

                    drawPath(
                        path = shinePath,
                        color = Color(0xFFFEF9C3).copy(alpha = 0.44f)
                    )

                    // Двусторонняя чеканка монеты:
                    // Аверс (flipCos >= 0f) — рельефный символ рубля ₽
                    // Реверс (flipCos < 0f) — чеканный золотой барельеф жабы Давида
                    if (abs(flipCos) > 0.14f) {
                        if (flipCos >= 0f) {
                            val strokeW = (coinRadius * 0.18f).coerceAtLeast(1.3f)
                            val shadowShift = 1.2.dp.toPx().coerceAtMost(coinRadius * 0.13f)
                            val highlightShift = 0.55.dp.toPx().coerceAtMost(coinRadius * 0.065f)

                            val stemX = -coinRadius * 0.14f
                            val topY = -coinRadius * 0.38f
                            val botY = coinRadius * 0.40f
                            val midY = coinRadius * 0.04f
                            val barY = coinRadius * 0.20f

                            // 1. Темная тень рельефа (#78350F) со сдвигом +1.2.dp
                            val reliefShadowColor = Color(0xFF78350F).copy(alpha = 0.78f)
                            drawLine(
                                color = reliefShadowColor,
                                start = Offset(stemX + shadowShift, topY + shadowShift),
                                end = Offset(stemX + shadowShift, botY + shadowShift),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            rubleShadowPath.reset()
                            rubleShadowPath.moveTo(stemX + shadowShift, topY + shadowShift)
                            rubleShadowPath.lineTo(coinRadius * 0.08f + shadowShift, topY + shadowShift)
                            rubleShadowPath.arcTo(
                                rect = Rect(
                                    left = -coinRadius * 0.08f + shadowShift,
                                    top = topY + shadowShift,
                                    right = coinRadius * 0.30f + shadowShift,
                                    bottom = midY + shadowShift
                                ),
                                startAngleDegrees = -90f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false
                            )
                            rubleShadowPath.lineTo(stemX + shadowShift, midY + shadowShift)
                            drawPath(
                                path = rubleShadowPath,
                                color = reliefShadowColor,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            drawLine(
                                color = reliefShadowColor,
                                start = Offset(-coinRadius * 0.28f + shadowShift, barY + shadowShift),
                                end = Offset(coinRadius * 0.16f + shadowShift, barY + shadowShift),
                                strokeWidth = strokeW * 0.85f,
                                cap = StrokeCap.Round
                            )

                            // 2. Золотисто-бронзовый символ ₽ (#92400E)
                            val mainSymbolColor = Color(0xFF92400E)
                            drawLine(
                                color = mainSymbolColor,
                                start = Offset(stemX, topY),
                                end = Offset(stemX, botY),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                            rubleBowlPath.reset()
                            rubleBowlPath.moveTo(stemX, topY)
                            rubleBowlPath.lineTo(coinRadius * 0.08f, topY)
                            rubleBowlPath.arcTo(
                                rect = Rect(
                                    left = -coinRadius * 0.08f,
                                    top = topY,
                                    right = coinRadius * 0.30f,
                                    bottom = midY
                                ),
                                startAngleDegrees = -90f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false
                            )
                            rubleBowlPath.lineTo(stemX, midY)
                            drawPath(
                                path = rubleBowlPath,
                                color = mainSymbolColor,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            drawLine(
                                color = mainSymbolColor,
                                start = Offset(-coinRadius * 0.28f, barY),
                                end = Offset(coinRadius * 0.16f, barY),
                                strokeWidth = strokeW * 0.85f,
                                cap = StrokeCap.Round
                            )

                            // 3. Яркий верхний блик чеканки (#FEF9C3)
                            val highlightColor = Color(0xFFFEF9C3).copy(alpha = 0.82f)
                            val highlightW = (strokeW * 0.38f).coerceAtLeast(0.75f)
                            drawLine(
                                color = highlightColor,
                                start = Offset(stemX - highlightShift, topY - highlightShift),
                                end = Offset(stemX - highlightShift, botY - highlightShift),
                                strokeWidth = highlightW,
                                cap = StrokeCap.Round
                            )
                            rubleHighlightPath.reset()
                            rubleHighlightPath.moveTo(stemX - highlightShift, topY - highlightShift)
                            rubleHighlightPath.lineTo(coinRadius * 0.08f - highlightShift, topY - highlightShift)
                            rubleHighlightPath.arcTo(
                                rect = Rect(
                                    left = -coinRadius * 0.08f - highlightShift,
                                    top = topY - highlightShift,
                                    right = coinRadius * 0.30f - highlightShift,
                                    bottom = midY - highlightShift
                                ),
                                startAngleDegrees = -90f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false
                            )
                            rubleHighlightPath.lineTo(stemX - highlightShift, midY - highlightShift)
                            drawPath(
                                path = rubleHighlightPath,
                                color = highlightColor,
                                style = Stroke(width = highlightW, cap = StrokeCap.Round)
                            )
                        } else {
                            // Реверс (flipCos < 0f): чеканный золотой барельеф головы жабы Давида
                            val r = coinRadius
                            val highlightColor = Color(0xFFFEF08A).copy(alpha = 0.7f)
                            val shadowStampColor = Color(0xFF78350F)
                            val mainStampColor = Color(0xFF92400E)
                            val innerStampColor = Color(0xFFB45309)

                            val highlightOffset = Offset(-r * 0.02f, -r * 0.02f)
                            val leftEyeCenter = Offset(-r * 0.24f, -r * 0.22f)
                            val rightEyeCenter = Offset(r * 0.24f, -r * 0.22f)
                            val eyeBumpRadius = r * 0.20f
                            val headTopLeft = Offset(-r * 0.48f, -r * 0.20f)
                            val headSize = Size(r * 0.96f, r * 0.62f)

                            // 1) Светло-золотой блик барельефа со сдвигом (-r * 0.02f, -r * 0.02f)
                            drawCircle(
                                color = highlightColor,
                                radius = eyeBumpRadius,
                                center = leftEyeCenter + highlightOffset
                            )
                            drawCircle(
                                color = highlightColor,
                                radius = eyeBumpRadius,
                                center = rightEyeCenter + highlightOffset
                            )
                            drawOval(
                                color = highlightColor,
                                topLeft = headTopLeft + highlightOffset,
                                size = headSize
                            )

                            // 2) Основной штамп бугорков глаз и овала головы (#92400E / #B45309)
                            drawCircle(
                                color = mainStampColor,
                                radius = eyeBumpRadius,
                                center = leftEyeCenter
                            )
                            drawCircle(
                                color = mainStampColor,
                                radius = eyeBumpRadius,
                                center = rightEyeCenter
                            )
                            drawOval(
                                color = mainStampColor,
                                topLeft = headTopLeft,
                                size = headSize
                            )
                            drawOval(
                                color = innerStampColor,
                                topLeft = Offset(-r * 0.43f, -r * 0.16f),
                                size = Size(r * 0.86f, r * 0.54f)
                            )

                            // 3) Внутри глазниц — светло-золотые радужки (#FDE047, r * 0.12f) и темные чеканные зрачки (#78350F, r * 0.07f)
                            drawCircle(
                                color = Color(0xFFFDE047),
                                radius = r * 0.12f,
                                center = leftEyeCenter
                            )
                            drawCircle(
                                color = Color(0xFFFDE047),
                                radius = r * 0.12f,
                                center = rightEyeCenter
                            )
                            drawCircle(
                                color = shadowStampColor,
                                radius = r * 0.07f,
                                center = leftEyeCenter
                            )
                            drawCircle(
                                color = shadowStampColor,
                                radius = r * 0.07f,
                                center = rightEyeCenter
                            )

                            // 4) Две ноздри по центру и широкая довольная улыбка Давида
                            val nostrilRadius = (r * 0.035f).coerceAtLeast(0.6f)
                            drawCircle(
                                color = shadowStampColor,
                                radius = nostrilRadius,
                                center = Offset(-r * 0.08f, -r * 0.04f)
                            )
                            drawCircle(
                                color = shadowStampColor,
                                radius = nostrilRadius,
                                center = Offset(r * 0.08f, -r * 0.04f)
                            )

                            embossedFrogSmilePath.reset()
                            embossedFrogSmilePath.moveTo(-r * 0.30f, r * 0.12f)
                            embossedFrogSmilePath.quadraticTo(0f, r * 0.30f, r * 0.30f, r * 0.12f)
                            drawPath(
                                path = embossedFrogSmilePath,
                                color = shadowStampColor,
                                style = Stroke(width = (r * 0.065f).coerceAtLeast(1f), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DavidTypingBubble(modifier: Modifier = Modifier) {
    DavidCoinTossLoader(modifier = modifier)
}
