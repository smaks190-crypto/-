package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal class BrandFrogPaths {
    val mouthPath = Path()
    val smileLinePath = Path()
}

/**
 * Единый векторный аватар фирменной жабы Давида Жабова (с иконки приложения `ic_launcher_foreground.xml`),
 * но БЕЗ монетки во рту — с дружелюбной улыбкой, пухлыми щечками, изумрудным градиентом и живым взглядом.
 */
@Composable
fun BrandFrogAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val paths = remember { BrandFrogPaths() }

    Canvas(modifier = modifier.size(size)) {
        drawBrandFrogVector(
            paths = paths,
            mouthOpenProgress = 0f,
            pupilOffsetYRatio = 0f
        )
    }
}

/**
 * Общая функция отрисовки фирменной жабы внутри любого [DrawScope].
 * Масштабируется пропорционально `size.minDimension`, поэтому идеально выглядит
 * в любом размере (16.dp в пузыре сообщения, 20.dp в шапке, 28.dp в чате, 38.dp в пустом стейте).
 *
 * @param mouthOpenProgress 0f — закрытая широкая улыбка; 1f — приоткрытый в улыбке рот (для анимации подбрасывания монетки).
 * @param pupilOffsetYRatio смещение зрачков по вертикали относительно радиуса зрачка (-1f — взгляд вверх за монеткой, 0f — прямо).
 */
internal fun DrawScope.drawBrandFrogVector(
    paths: BrandFrogPaths,
    mouthOpenProgress: Float = 0f,
    pupilOffsetYRatio: Float = 0f
) {
    val minDim = size.minDimension
    if (minDim <= 1f) return

    val cx = size.width / 2f
    val cy = size.height * 0.56f
    val r = minDim * 0.42f

    // 1. Внешнее неоново-изумрудное свечение вокруг головы жабы
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF10B981).copy(alpha = 0.28f),
                Color.Transparent
            ),
            center = Offset(cx, cy - r * 0.08f),
            radius = r * 1.30f
        ),
        radius = r * 1.30f,
        center = Offset(cx, cy - r * 0.08f)
    )

    // 2. Мягкая тень под подбородком
    drawOval(
        color = Color(0xFF020617).copy(alpha = 0.45f),
        topLeft = Offset(cx - r * 0.88f, cy + r * 0.56f),
        size = Size(r * 1.76f, r * 0.36f)
    )

    // 3. Бугорки глаз сверху головы (левый и правый)
    val eyeCenterOffsetX = r * 0.52f
    val eyeCenterY = cy - r * 0.54f
    val eyeSocketRadius = r * 0.42f

    val eyeBumpBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF6EE7B7),
            Color(0xFF10B981),
            Color(0xFF047857)
        ),
        start = Offset(cx, eyeCenterY - eyeSocketRadius),
        end = Offset(cx, eyeCenterY + eyeSocketRadius)
    )

    drawCircle(
        brush = eyeBumpBrush,
        radius = eyeSocketRadius,
        center = Offset(cx - eyeCenterOffsetX, eyeCenterY)
    )
    drawCircle(
        brush = eyeBumpBrush,
        radius = eyeSocketRadius,
        center = Offset(cx + eyeCenterOffsetX, eyeCenterY)
    )

    // 4. Широкая голова и пухлые щечки с объемным 3D-градиентом
    val headWidth = r * 2.18f
    val headHeight = r * 1.48f
    val headTopLeft = Offset(cx - headWidth / 2f, cy - headHeight * 0.48f)

    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF6EE7B7),
                Color(0xFF10B981),
                Color(0xFF059669),
                Color(0xFF064E3B)
            ),
            start = Offset(cx, headTopLeft.y),
            end = Offset(cx, headTopLeft.y + headHeight)
        ),
        topLeft = headTopLeft,
        size = Size(headWidth, headHeight)
    )

    // Внутренний глянцевый блик на лбу и макушке
    val browWidth = headWidth * 0.72f
    val browHeight = headHeight * 0.36f
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFA7F3D0).copy(alpha = 0.42f),
                Color.Transparent
            ),
            startY = headTopLeft.y + r * 0.05f,
            endY = headTopLeft.y + r * 0.05f + browHeight
        ),
        topLeft = Offset(cx - browWidth / 2f, headTopLeft.y + r * 0.06f),
        size = Size(browWidth, browHeight)
    )

    // 5. Светлое мятно-золотистое брюшко / нижняя челюсть
    val bellyWidth = headWidth * 0.74f
    val bellyHeight = headHeight * 0.48f
    val bellyTop = cy + r * 0.04f
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFD1FAE5).copy(alpha = 0.86f),
                Color(0xFF6EE7B7).copy(alpha = 0.52f)
            ),
            startY = bellyTop,
            endY = bellyTop + bellyHeight
        ),
        topLeft = Offset(cx - bellyWidth / 2f, bellyTop),
        size = Size(bellyWidth, bellyHeight)
    )

    // 6. Выразительные глаза (белки, золотисто-изумрудная радужка, живые зрачки и двойные блики)
    val scleraRadius = eyeSocketRadius * 0.74f
    val irisRadius = scleraRadius * 0.68f
    val pupilRadius = scleraRadius * 0.48f
    val pupilShiftY = pupilOffsetYRatio.coerceIn(-1.2f, 1.2f) * (scleraRadius * 0.22f)

    val eyeSides = intArrayOf(-1, 1)
    for (side in eyeSides) {
        val ex = cx + side * eyeCenterOffsetX
        val ey = eyeCenterY

        // Белок глаза
        drawCircle(
            color = Color(0xFFF8FAFC),
            radius = scleraRadius,
            center = Offset(ex, ey)
        )

        // Изумрудно-золотистая радужка
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFDE047),
                    Color(0xFF10B981)
                ),
                center = Offset(ex, ey + pupilShiftY * 0.6f),
                radius = irisRadius
            ),
            radius = irisRadius,
            center = Offset(ex, ey + pupilShiftY * 0.6f)
        )

        // Глубокий темный зрачок
        val px = ex - side * scleraRadius * 0.06f
        val py = ey + pupilShiftY
        drawCircle(
            color = Color(0xFF020617),
            radius = pupilRadius,
            center = Offset(px, py)
        )

        // Крупный солнечный блик в глазу
        drawCircle(
            color = Color.White,
            radius = pupilRadius * 0.38f,
            center = Offset(px - pupilRadius * 0.34f, py - pupilRadius * 0.34f)
        )
        // Малый вторичный блик
        drawCircle(
            color = Color.White.copy(alpha = 0.82f),
            radius = pupilRadius * 0.17f,
            center = Offset(px + pupilRadius * 0.32f, py + pupilRadius * 0.28f)
        )
    }

    // 7. Румяные щечки по бокам улыбки
    val cheekWidth = r * 0.34f
    val cheekHeight = r * 0.20f
    val cheekY = cy + r * 0.05f
    drawOval(
        color = Color(0xFF34D399).copy(alpha = 0.58f),
        topLeft = Offset(cx - r * 0.88f, cheekY),
        size = Size(cheekWidth, cheekHeight)
    )
    drawOval(
        color = Color(0xFF34D399).copy(alpha = 0.58f),
        topLeft = Offset(cx + r * 0.88f - cheekWidth, cheekY),
        size = Size(cheekWidth, cheekHeight)
    )

    // 8. Аккуратные ноздри
    val nostrilRadius = (r * 0.048f).coerceAtLeast(0.8f)
    val nostrilY = cy - r * 0.09f
    drawCircle(
        color = Color(0xFF064E3B),
        radius = nostrilRadius,
        center = Offset(cx - r * 0.14f, nostrilY)
    )
    drawCircle(
        color = Color(0xFF064E3B),
        radius = nostrilRadius,
        center = Offset(cx + r * 0.14f, nostrilY)
    )

    // 9. Дружелюбная фирменная улыбка (без монетки во рту)
    val mouthY = cy + r * 0.20f
    val mouthHalfW = r * 0.56f
    val openFactor = mouthOpenProgress.coerceIn(0f, 1f)

    if (openFactor > 0.04f) {
        val mouthOpenH = r * (0.12f + 0.34f * openFactor)
        paths.mouthPath.reset()
        paths.mouthPath.moveTo(cx - mouthHalfW, mouthY)
        paths.mouthPath.quadraticTo(
            cx,
            mouthY + mouthOpenH * 0.25f,
            cx + mouthHalfW,
            mouthY
        )
        paths.mouthPath.quadraticTo(
            cx,
            mouthY + mouthOpenH * 1.45f,
            cx - mouthHalfW,
            mouthY
        )
        paths.mouthPath.close()

        drawPath(
            path = paths.mouthPath,
            color = Color(0xFF042F2E)
        )

        // Розовый язычок внутри приоткрытого рта
        val tongueW = mouthHalfW * 0.88f
        val tongueH = mouthOpenH * 0.58f
        drawOval(
            color = Color(0xFFFB7185).copy(alpha = 0.88f),
            topLeft = Offset(cx - tongueW / 2f, mouthY + mouthOpenH * 0.42f),
            size = Size(tongueW, tongueH)
        )
    }

    // Четкая векторная линия улыбки с уголками губ
    paths.smileLinePath.reset()
    paths.smileLinePath.moveTo(cx - mouthHalfW, mouthY - r * 0.02f)
    paths.smileLinePath.quadraticTo(
        cx,
        mouthY + r * (0.30f + 0.12f * openFactor),
        cx + mouthHalfW,
        mouthY - r * 0.02f
    )
    drawPath(
        path = paths.smileLinePath,
        color = Color(0xFF064E3B),
        style = Stroke(
            width = (r * 0.095f).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
        )
    )
}
