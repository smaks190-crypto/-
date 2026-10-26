package com.example.davidapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BrandFrogAvatar
import com.example.ui.components.BrandFrogPaths
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.MarkdownFormattedText
import com.example.ui.components.drawBrandFrogVector
import com.example.ui.components.formatRublesCompact
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.example.ui.theme.Amber400
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// --- Obsidian Neo-Bank Design Tokens ---
val NeonEmerald = Emerald400
val NeonIndigo = Indigo500
val NeonRose = Rose500
val NeonAmber = Amber400
val DarkBackground = DarkBg
val DarkSurface = ObsidianSurface
val DarkCard = ObsidianElevated
val SlateBorder = Slate700
val TextLight = Color(0xFFF8FAFC)
val TextMuted = Slate400

val CyberpunkGradient = Brush.linearGradient(
    colors = listOf(Emerald400, Indigo500)
)

/**
 * Верхняя панель чата Давида Жабова в стиле Obsidian Neo-Bank
 */
@Composable
fun DavidChatTopBar(
    onBack: (() -> Unit)? = null,
    onReset: () -> Unit,
    onOpenAchievements: (() -> Unit)? = null,
    hasApiKey: Boolean = true,
    onOpenApiKeyDialog: (() -> Unit)? = null,
    isTyping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Surface(
        color = DarkBg.copy(alpha = 0.96f),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = GlassBorder,
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("david_chat_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Obsidian Avatar with Emerald Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Emerald400.copy(alpha = glowAlpha), Color.Transparent)
                            )
                        )
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(Emerald400.copy(alpha = 0.85f), Indigo500.copy(alpha = 0.5f))
                            ),
                            shape = CircleShape
                        )
                ) {
                    BrandFrogAvatar(size = 28.dp)
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (hasApiKey) Emerald400 else Amber400)
                        .border(1.5.dp, DarkBg, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (onOpenApiKeyDialog != null) {
                            Modifier.clickable { onOpenApiKeyDialog() }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Давид Жабов",
                        color = TextLight,
                        fontSize = 15.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Emerald400.copy(alpha = 0.14f))
                            .border(0.5.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AI Аудитор",
                            color = Emerald400,
                            fontSize = 9.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = when {
                        isTyping -> "анализирует бюджет..."
                        hasApiKey -> "Gemini 3.8 • Готов к разбору трат"
                        else -> "Офлайн-режим • Нажмите для ввода ключа"
                    },
                    color = when {
                        isTyping -> Emerald400
                        hasApiKey -> Slate400
                        else -> Amber400
                    },
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onOpenAchievements != null) {
                val achievementsList by AchievementManager.achievementsFlow.collectAsState()
                val unlockedCount = achievementsList.count { it.isUnlocked }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenAchievements)
                        .padding(horizontal = 10.dp)
                        .testTag("david_chat_achievements_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🏆", fontSize = 14.sp)
                        Text(
                            text = unlockedCount.toString(),
                            color = Amber400,
                            fontSize = 12.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable(onClick = onReset)
                    .testTag("david_chat_reset_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Очистить чат",
                    tint = Slate400,
                    modifier = Modifier.size(17.dp)
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
    onConfirmProposal: ((String) -> Unit)? = null,
    onDismissProposal: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = alignment,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
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
            is ChatMessageType.Operation -> {
                DavidOperationBubble(
                    operation = type,
                    timestamp = message.timestamp
                )
            }
            is ChatMessageType.ParsedTransactionProposal -> {
                DavidParsedTransactionProposalBubble(
                    proposal = type,
                    onConfirm = { onConfirmProposal?.invoke(message.id) },
                    onDismiss = { onDismissProposal?.invoke(message.id) }
                )
            }
        }
    }
}

/**
 * Интерактивная карточка распознанной в чате операции с кнопками «Добавить» / «Отмена»
 */
@Composable
fun DavidParsedTransactionProposalBubble(
    proposal: ChatMessageType.ParsedTransactionProposal,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = proposal.operation
    val isExpense = tx.type == "expense"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.45f)),
        modifier = modifier
            .widthIn(min = 240.dp, max = 340.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBadge(
                    categoryName = tx.category,
                    type = tx.type,
                    size = 38.dp,
                    iconSize = 19.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (proposal.isAdded) "Операция сохранена" else "Быстрое добавление",
                        color = if (proposal.isAdded) Emerald400 else Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${tx.subcategory.ifBlank { tx.title.ifBlank { tx.category } }} • ${tx.category}",
                        color = TextLight,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (isExpense) "-" else "+"}${formatRublesCompact(tx.amount)} ₽",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isExpense) Rose500 else Emerald400
                )
            }
            if (!proposal.isAdded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Отмена",
                            color = Slate400,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald400,
                            contentColor = DarkBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Записать",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Добавлено в бюджет",
                    color = Emerald400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Карточка добавленной операции от пользователя в стиле Obsidian Neo-Bank
 */
@Composable
fun DavidOperationBubble(
    operation: ChatMessageType.Operation,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val bubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    val isExpense = operation.type == "expense"
    val accentColor = if (isExpense) Rose500 else Emerald400
    val amountPrefix = if (isExpense) "-" else "+"

    Surface(
        shape = bubbleShape,
        color = ObsidianElevated,
        border = BorderStroke(1.dp, GlassHighlight),
        modifier = modifier
            .widthIn(min = 140.dp, max = 320.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (operation.userPhrase.isNotBlank()) {
                Text(
                    text = operation.userPhrase,
                    color = TextLight,
                    fontSize = 13.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg.copy(alpha = 0.65f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CategoryIconBadge(
                        categoryName = operation.category,
                        type = operation.type,
                        size = 28.dp,
                        iconSize = 14.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = operation.category,
                            color = TextLight,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        if (operation.subcategory.isNotBlank() && operation.subcategory != operation.category) {
                            Text(
                                text = operation.subcategory,
                                color = Slate400,
                                fontSize = 10.sp,
                                fontFamily = ManropeFontFamily
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$amountPrefix${operation.amount.toInt()} ₽",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestamp,
                    color = Slate400.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontFamily = ManropeFontFamily
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (operation.isRead) Icons.Default.DoneAll else Icons.Default.CheckCircle,
                    contentDescription = "Отправлено",
                    tint = if (operation.isRead) Emerald400 else Slate400,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Текстовое сообщение в эстетике Obsidian Neo-Bank
 */
@Composable
fun DavidTextBubble(
    content: String,
    timestamp: String,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    val containerColor = if (isUser) {
        Indigo500.copy(alpha = 0.18f)
    } else {
        ObsidianSurface
    }

    val borderStroke = if (isUser) {
        BorderStroke(1.dp, Indigo500.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, GlassBorder)
    }

    Surface(
        shape = bubbleShape,
        color = containerColor,
        border = borderStroke,
        modifier = modifier
            .widthIn(min = 80.dp, max = if (isUser) 305.dp else 355.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (!isUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 5.dp)
                ) {
                    BrandFrogAvatar(size = 16.dp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Давид Жабов",
                        color = Emerald400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (!isUser && (content.contains("#") || content.contains("**") || content.contains("- "))) {
                MarkdownFormattedText(
                    markdownText = content,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = content,
                    color = TextLight,
                    fontSize = 13.5.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = timestamp,
                color = Slate400.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * Карточка выписки / отчета
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
    val bubbleShape = RoundedCornerShape(18.dp)

    Surface(
        shape = bubbleShape,
        color = ObsidianSurface,
        border = BorderStroke(1.dp, if (isUser) Indigo500.copy(alpha = 0.45f) else Emerald400.copy(alpha = 0.4f)),
        modifier = modifier.widthIn(max = 310.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Emerald400.copy(alpha = 0.14f))
                        .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = TextLight,
                        fontSize = 12.5.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = extension.uppercase(),
                            color = Emerald400,
                            fontSize = 10.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $size",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isUser) "Данные переданы на разбор" else "Отчет сформирован",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = timestamp,
                    color = Slate400.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = ManropeFontFamily
                )
            }
        }
    }
}

/**
 * Интерактивная карточка с финансовым графиком и сводкой доходов/расходов в стиле Obsidian Neo-Bank
 */
@Composable
fun DavidChartBubble(
    chartData: ChatMessageType.Chart,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    val rubleFormatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale.GERMAN))

    Surface(
        shape = shape,
        color = ObsidianSurface,
        border = BorderStroke(1.dp, GlassHighlight),
        modifier = modifier
            .widthIn(max = 345.dp)
            .shadow(10.dp, shape)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = "График",
                            tint = Emerald400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = chartData.title,
                        color = TextLight,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isPositiveDelta = chartData.deltaPercent >= 0
                val deltaColor = if (isPositiveDelta) Emerald400 else Rose500
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(deltaColor.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${if (isPositiveDelta) "+" else ""}${chartData.deltaPercent}%",
                        color = deltaColor,
                        fontSize = 10.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Smooth Obsidian Bézier Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp)
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

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.28f),
                                Indigo500.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Emerald400, Indigo400)
                        ),
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    val lastX = (points.size - 1) * stepX
                    val lastNorm = (points.last() - minVal) / range
                    val lastY = height - (lastNorm * (height - 12.dp.toPx()) + 6.dp.toPx())

                    drawCircle(
                        color = Emerald400.copy(alpha = 0.35f),
                        radius = 6.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                    drawCircle(
                        color = Emerald400,
                        radius = 3.5.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg.copy(alpha = 0.65f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ДОХОДЫ",
                        color = Slate400,
                        fontSize = 9.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "+${rubleFormatter.format(chartData.income)} ₽",
                        color = Emerald400,
                        fontSize = 13.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(GlassBorder)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "РАСХОДЫ",
                        color = Slate400,
                        fontSize = 9.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "-${rubleFormatter.format(chartData.expense)} ₽",
                        color = Rose500,
                        fontSize = 13.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (chartData.summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = chartData.summary,
                    color = TextLight,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestamp,
                color = Slate400.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * Анимационный индикатор ожидания ответа Давида Жабова без какого-либо текста:
 * фирменная жаба циклично подбрасывает объемную 3D золотую монетку с вертикальным кувырком и ловит её обратно.
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
    val shinePath = remember { Path() }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.45f)),
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

                // --- ВЕРТИКАЛЬНОЕ 3D-ВРАЩЕНИЕ МОНЕТКИ (вокруг горизонтальной оси X) ---
                val coinStartY = h * 0.67f
                val coinPeakY = h * 0.15f
                val coinY = coinStartY - (coinStartY - coinPeakY) * flightHeight
                val coinX = w / 2f
                val coinRadius = w * 0.165f

                // 2 полных оборота по вертикали за один цикл броска: ширина всегда = 1f, сжимается только высота scaleY
                val spinAngle = p * 2f * PI.toFloat() * 2f
                val flipCos = cos(spinAngle)
                val flipSin = sin(spinAngle)
                val faceScaleY = abs(flipCos).coerceAtLeast(0.10f)

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

                    // Объемный рельефный символ ₽ (темная тень #78350F со сдвигом +1.2.dp, бронзовый ₽ #92400E и верхний блик #FEF9C3)
                    if (abs(flipCos) > 0.20f) {
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

/**
 * Единая трансформирующаяся лента умных действий и выбора периода разбора (Morphing Smart Action Strip).
 * Заменяет устаревшие дублирующиеся панели DavidQuickActionsBar + DavidSuggestionChipsRow:
 * - В обычном режиме показывает единую горизонтальную ленту чипов в стиле Obsidian Neo-Bank.
 * - При нажатии на «🔥 Разбор трат» эта же полоса плавно трансформируется в компактный селектор периода:
 *   [✕] Период: [День] [Неделя] [Месяц] [Год].
 */
@Composable
fun DavidSmartActionStrip(
    stage: DavidStage,
    hasApiKey: Boolean,
    isSelectingPeriod: Boolean,
    onTogglePeriodSelector: (Boolean) -> Unit,
    onPeriodSelect: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onActionClick: (String) -> Unit,
    onExportPdf: () -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stage != DavidStage.PROCESSING,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = isSelectingPeriod || stage == DavidStage.FILE_SELECTION,
            transitionSpec = {
                (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                    .using(
                        SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            }
                        )
                    )
            },
            label = "david_smart_strip_morph"
        ) { selectingPeriod ->
            if (selectingPeriod) {
                // Встроенная капсула выбора периода без лишних этажей и дублирования
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                            .clickable { onTogglePeriodSelector(false) }
                            .testTag("close_period_selector_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Slate400,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = "Период:",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    val periods = listOf("День", "Неделя", "Месяц", "Год")
                    periods.forEach { period ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Emerald400.copy(alpha = 0.14f))
                                .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable {
                                    onTogglePeriodSelector(false)
                                    onPeriodSelect(period)
                                }
                                .testTag("period_${period.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                // Единая горизонтальная лента быстрых чипов
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Главный акцентный чип запуска разбора с выбором периода
                    ObsidianQuickChip(
                        text = "🔥 Разбор трат",
                        isAccent = true,
                        onClick = { onTogglePeriodSelector(true) },
                        tag = "action_start_report"
                    )

                    // 2. Если аудит уже проведен — компактный чип экспорта в PDF прямо в этом же ряду
                    if (stage == DavidStage.FOLLOW_UP) {
                        ObsidianQuickChip(
                            text = "📄 Скачать PDF",
                            isAccent = false,
                            borderColor = Indigo500.copy(alpha = 0.5f),
                            textColor = Indigo400,
                            onClick = onExportPdf,
                            tag = "action_download_pdf"
                        )
                    }

                    // 3. Поиск утечек бюджета
                    ObsidianQuickChip(
                        text = "💡 Где утечки бюджета?",
                        onClick = { onSendPrompt("На чём мне сэкономить и какие главные утечки бюджета?") }
                    )

                    // 4. Финансовые цели
                    ObsidianQuickChip(
                        text = "🎯 Статус целей",
                        onClick = { onActionClick("GOALS") }
                    )

                    // 5. Сводка баланса и трендов
                    ObsidianQuickChip(
                        text = "📊 Баланс и тренды",
                        onClick = { onSendPrompt("Покажи мой баланс и аналитику трендов") }
                    )

                    // 6. Настройка API ключа
                    ObsidianQuickChip(
                        text = if (hasApiKey) "🔑 API ключ" else "🔑 Подключить Gemini",
                        borderColor = if (!hasApiKey) Amber400.copy(alpha = 0.55f) else GlassBorder,
                        textColor = if (!hasApiKey) Amber400 else Slate400,
                        onClick = onOpenApiKeyDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun ObsidianQuickChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    borderColor: Color = if (isAccent) Emerald400.copy(alpha = 0.55f) else GlassBorder,
    textColor: Color = if (isAccent) Emerald400 else TextLight,
    tag: String = "obsidian_quick_chip"
) {
    val containerColor = if (isAccent) {
        Emerald400.copy(alpha = 0.14f)
    } else {
        ObsidianSurface
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Нижняя капсула ввода сообщения в едином стиле с MorphingBottomBar (Obsidian Neo-Bank)
 */
@Composable
fun DavidChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val trimmedInput = text.trim()
    val isSecretInput = trimmedInput.startsWith("AIza") ||
        trimmedInput.startsWith("AQ.") ||
        (trimmedInput.length >= 25 && !trimmedInput.contains(" "))

    val isEnabled = text.isNotBlank()
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) Emerald400.copy(alpha = 0.5f) else GlassHighlight,
        animationSpec = tween(200),
        label = "chat_input_border"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(16.dp, RoundedCornerShape(27.dp))
            .clip(RoundedCornerShape(27.dp))
            .background(ObsidianSurface.copy(alpha = 0.98f))
            .border(1.dp, borderColor, RoundedCornerShape(27.dp))
            .padding(start = 16.dp, end = 6.dp)
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Спросите Давида или введите: Кофе 300...",
                    color = Slate400,
                    fontSize = 13.sp,
                    fontFamily = ManropeFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    color = TextLight,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                visualTransformation = if (isSecretInput) {
                    PasswordVisualTransformation('*')
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(Emerald400),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("david_text_input")
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) {
                        Brush.linearGradient(listOf(Emerald400, NeonEmerald))
                    } else {
                        Brush.linearGradient(listOf(Slate800, Slate800))
                    }
                )
                .clickable(enabled = isEnabled, onClick = onSend)
                .testTag("david_send_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Отправить",
                tint = if (isEnabled) DarkBg else Slate400,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
