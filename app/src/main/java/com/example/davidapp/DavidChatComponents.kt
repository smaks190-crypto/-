package com.example.davidapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// --- Dark Neon & Cyberpunk Minimalist Palette ---
val NeonEmerald = Color(0xFF34D399)
val NeonIndigo = Color(0xFF6366F1)
val NeonRose = Color(0xFFF43F5E)
val DarkBackground = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF0F172A)
val DarkCard = Color(0xFF1E293B)
val SlateBorder = Color(0xFF334155)
val TextLight = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

val CyberpunkGradient = Brush.linearGradient(
    colors = listOf(NeonEmerald, NeonIndigo, NeonRose),
    start = Offset(0f, 0f),
    end = Offset(400f, 400f)
)

/**
 * Верхняя панель чата с неоновым аватаром Давида Жабова
 */
@Composable
fun DavidChatTopBar(
    onBack: (() -> Unit)? = null,
    onReset: () -> Unit,
    isTyping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        color = DarkSurface.copy(alpha = 0.95f),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = SlateBorder.copy(alpha = 0.5f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("david_chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextLight
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Glowing Cyberpunk Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonIndigo.copy(alpha = glowAlpha), Color.Transparent)
                            )
                        )
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .border(1.5.dp, CyberpunkGradient, CircleShape)
                ) {
                    Text(text = "🐸", fontSize = 20.sp)
                }

                // Online indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NeonEmerald)
                        .border(1.5.dp, DarkSurface, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Давид Жабов",
                        color = TextLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonIndigo.copy(alpha = 0.2f))
                            .border(0.5.dp, NeonIndigo.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "AI BOT",
                            color = NeonIndigo,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = if (isTyping) "печатает..." else "в сети",
                    color = if (isTyping) NeonIndigo else NeonEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onReset,
                modifier = Modifier.testTag("david_chat_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Очистить чат",
                    tint = TextMuted
                )
            }
        }
    }
}

/**
 * Контейнер отдельного сообщения с маршрутизацией по типам
 */
@Composable
fun DavidMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        when (val type = message.type) {
            is ChatMessageType.Text -> {
                DavidTextBubble(
                    content = type.content,
                    timestamp = message.timestamp,
                    isUser = message.isUser
                )
            }
            is ChatMessageType.File -> {
                DavidFileBubble(
                    name = type.name,
                    size = type.size,
                    extension = type.extension,
                    timestamp = message.timestamp,
                    isUser = message.isUser
                )
            }
            is ChatMessageType.Chart -> {
                DavidChartBubble(
                    chartData = type,
                    timestamp = message.timestamp
                )
            }
        }
    }
}

/**
 * Текстовое сообщение с неоновым акцентом
 */
@Composable
fun DavidTextBubble(
    content: String,
    timestamp: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val bubbleBackground = if (isUser) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF283548))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF162032))
        )
    }

    val borderStroke = if (isUser) {
        BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
    }

    Surface(
        shape = bubbleShape,
        color = Color.Transparent,
        border = borderStroke,
        modifier = modifier
            .widthIn(min = 80.dp, max = if (isUser) 300.dp else 360.dp)
            .shadow(4.dp, bubbleShape, ambientColor = if (isUser) NeonIndigo.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(bubbleBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(text = "🐸", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Давид Жабов",
                            color = NeonEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Аудитор",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                if (!isUser && (content.contains("#") || content.contains("**") || content.contains("- "))) {
                    com.example.ui.components.MarkdownFormattedText(
                        markdownText = content,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = content,
                        color = TextLight,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timestamp,
                    color = TextMuted.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/**
 * Карточка прикрепленного файла (отчет, выписка)
 */
@Composable
fun DavidFileBubble(
    name: String,
    size: String,
    extension: String,
    timestamp: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(14.dp)

    Surface(
        shape = bubbleShape,
        color = DarkCard,
        border = BorderStroke(1.dp, if (isUser) NeonIndigo.copy(alpha = 0.6f) else NeonEmerald.copy(alpha = 0.5f)),
        modifier = modifier
            .widthIn(max = 300.dp)
            .shadow(6.dp, bubbleShape)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkCard, DarkSurface)
                    )
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonRose.copy(alpha = 0.15f))
                        .border(1.dp, NeonRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF File",
                        tint = NeonRose,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(NeonIndigo.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = extension.uppercase(),
                                color = NeonIndigo,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = size,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUser) Icons.Default.DoneAll else Icons.Default.CheckCircle,
                        contentDescription = "Доставлено",
                        tint = NeonEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isUser) "Отправлено Давиду" else "Готово к аудиту",
                        color = NeonEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = timestamp,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Интерактивная карточка с финансовым графиком и сводкой доходов/расходов
 */
@Composable
fun DavidChartBubble(
    chartData: ChatMessageType.Chart,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val rubleFormatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale.GERMAN))

    Surface(
        shape = shape,
        color = DarkCard,
        border = BorderStroke(1.dp, CyberpunkGradient),
        modifier = modifier
            .widthIn(max = 330.dp)
            .shadow(8.dp, shape, ambientColor = NeonIndigo.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .padding(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NeonEmerald.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = "График",
                            tint = NeonEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chartData.title,
                        color = TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Рост",
                        tint = NeonEmerald,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "+${chartData.deltaPercent}%",
                        color = NeonEmerald,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Neon Bézier Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0F19))
                    .border(0.5.dp, SlateBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val points = chartData.dataPoints
                    if (points.size < 2) return@Canvas

                    val maxVal = points.maxOrNull() ?: 100f
                    val minVal = points.minOrNull() ?: 0f
                    val range = (maxVal - minVal).coerceAtLeast(1f)

                    val stepX = width / (points.size - 1)

                    val path = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, value ->
                        val x = i * stepX
                        val normalized = (value - minVal) / range
                        val y = height - (normalized * (height - 12.dp.toPx()) + 6.dp.toPx())

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevNorm = (points[i - 1] - minVal) / range
                            val prevY = height - (prevNorm * (height - 12.dp.toPx()) + 6.dp.toPx())

                            val cX1 = prevX + (x - prevX) / 2f
                            val cY1 = prevY
                            val cX2 = prevX + (x - prevX) / 2f
                            val cY2 = y

                            path.cubicTo(cX1, cY1, cX2, cY2, x, y)
                            fillPath.cubicTo(cX1, cY1, cX2, cY2, x, y)
                        }

                        if (i == points.size - 1) {
                            fillPath.lineTo(x, height)
                            fillPath.close()
                        }
                    }

                    // Gradient fill under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(NeonEmerald.copy(alpha = 0.3f), NeonIndigo.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )

                    // Glowing Stroke Line
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(NeonEmerald, NeonIndigo, NeonRose)
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Draw glowing end dot
                    val lastX = (points.size - 1) * stepX
                    val lastNorm = (points.last() - minVal) / range
                    val lastY = height - (lastNorm * (height - 12.dp.toPx()) + 6.dp.toPx())

                    drawCircle(
                        color = NeonEmerald.copy(alpha = 0.4f),
                        radius = 6.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = NeonEmerald,
                        radius = 3.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Income / Expense Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ДОХОДЫ",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "+${rubleFormatter.format(chartData.income)} ₽",
                        color = NeonEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(SlateBorder.copy(alpha = 0.5f))
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "РАСХОДЫ",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "-${rubleFormatter.format(chartData.expense)} ₽",
                        color = NeonRose,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (chartData.summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = chartData.summary,
                    color = TextLight,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestamp,
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * Индикатор печати Давида с анимированными неоновыми точками
 */
@Composable
fun DavidTypingBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f)),
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = "🐸", fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Давид думает",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .offset(y = dot1Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NeonEmerald)
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot2Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NeonIndigo)
                )
                Box(
                    modifier = Modifier
                        .offset(y = dot3Offset.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NeonRose)
                )
            }
        }
    }
}

/**
 * Динамические кнопки быстрых действий для разных этапов диалога
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DavidQuickActionsBar(
    stage: DavidStage,
    onActionClick: (String) -> Unit,
    onPeriodSelect: (String) -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stage != DavidStage.PROCESSING,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            when (stage) {
                DavidStage.INITIAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CyberpunkActionChip(
                            text = "🐸 Давид, сделай отчет",
                            gradient = CyberpunkGradient,
                            onClick = { onActionClick("START") },
                            modifier = Modifier.fillMaxWidth(),
                            tag = "action_start_report"
                        )
                    }
                }

                DavidStage.FILE_SELECTION -> {
                    Column {
                        Text(
                            text = "Выберите временной интервал для PDF-выписки:",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val periods = listOf("День", "Неделя", "Месяц", "Год")
                            periods.forEach { period ->
                                CyberpunkPeriodChip(
                                    text = period,
                                    onClick = { onPeriodSelect(period) },
                                    modifier = Modifier.weight(1f),
                                    tag = "period_${period.lowercase()}"
                                )
                            }
                        }
                    }
                }

                DavidStage.FOLLOW_UP -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CyberpunkActionChip(
                            text = "🔄 Новый запрос",
                            gradient = Brush.horizontalGradient(listOf(DarkCard, Color(0xFF283548))),
                            borderColor = SlateBorder,
                            textColor = TextLight,
                            onClick = { onActionClick("START") },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Refresh,
                            tag = "action_new_report"
                        )
                        CyberpunkActionChip(
                            text = "📄 Скачать PDF",
                            gradient = Brush.horizontalGradient(listOf(NeonIndigo, NeonEmerald)),
                            onClick = onExportPdf,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Download,
                            tag = "action_download_pdf"
                        )
                    }
                }

                DavidStage.PROCESSING -> {
                    // Hidden
                }
            }
        }
    }
}

/**
 * Неоновая кнопка-чип для быстрых действий
 */
@Composable
fun CyberpunkActionChip(
    text: String,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    textColor: Color = Color.White,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tag: String = "cyberpunk_chip"
) {
    val shape = RoundedCornerShape(12.dp)

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor ?: NeonIndigo.copy(alpha = 0.5f)),
        modifier = modifier
            .testTag(tag)
            .shadow(4.dp, shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(gradient)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Кнопка выбора периода с иконкой PDF
 */
@Composable
fun CyberpunkPeriodChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "period_chip"
) {
    val shape = RoundedCornerShape(10.dp)

    Surface(
        shape = shape,
        color = DarkCard,
        border = BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.4f)),
        modifier = modifier
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(DarkCard)
                .padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = "PDF",
                tint = NeonRose,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = TextLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Нижняя строка ввода текста с кнопкой отправки и вложениями
 */
@Composable
fun DavidChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = SlateBorder.copy(alpha = 0.5f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onAttach,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("david_attach_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Прикрепить",
                    tint = TextMuted
                )
            }

            // Input Field Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DarkCard)
                    .border(1.dp, SlateBorder.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Спросить Давида...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = TextLight,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(NeonEmerald),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("david_text_input")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Neon Send Button
            val isEnabled = text.isNotBlank()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) CyberpunkGradient else Brush.linearGradient(listOf(DarkCard, DarkCard))
                    )
                    .border(
                        1.dp,
                        if (isEnabled) NeonEmerald.copy(alpha = 0.8f) else SlateBorder.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .clickable(enabled = isEnabled, onClick = onSend)
                    .testTag("david_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = if (isEnabled) Color.White else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
