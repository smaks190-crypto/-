package com.example.davidapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.davidapp.live.DavidSendButtonGestureHelper
import com.example.davidapp.live.computeStreamingTextFadeSplitIndex
import kotlin.math.roundToInt
import com.example.ui.components.BrandFrogAvatar
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.MarkdownFormattedText
import com.example.ui.components.formatRublesCompact
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
import kotlinx.coroutines.withTimeoutOrNull

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

private val UserBubbleGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0C262B),
        Color(0xFF10203B),
        Color(0xFF131B36)
    )
)

private val DavidBubbleGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0E172A),
        Color(0xFF091020)
    )
)

val CyberpunkGradient = Brush.linearGradient(
    colors = listOf(Emerald400, Indigo500)
)

/**
 * Верхняя панель чата Давида Жабова в обновлённой эстетике Obsidian Neo-Bank:
 * стеклянная шапка с тонким изумрудно-сапфировым бликом, живым ореолом вокруг аватара
 * и сбалансированными капсулами действий.
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
        initialValue = 0.22f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val topBarBgBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B1324).copy(alpha = 0.96f),
                DarkBg.copy(alpha = 0.94f)
            )
        )
    }
    val bottomHairlineBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Emerald400.copy(alpha = 0.32f),
                Indigo400.copy(alpha = 0.25f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(topBarBgBrush)
            .drawBehind {
                drawLine(
                    brush = bottomHairlineBrush,
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
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface.copy(alpha = 0.9f))
                        .border(1.dp, GlassHighlight, CircleShape)
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

            // Obsidian Avatar with Emerald-Cyan Halo
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
                                colors = listOf(
                                    Emerald400.copy(alpha = glowAlpha),
                                    Color(0xFF06B6D4).copy(alpha = glowAlpha * 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B1426))
                        .border(
                            width = 1.4.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Emerald400.copy(alpha = 0.9f),
                                    Color(0xFF22D3EE).copy(alpha = 0.65f),
                                    Indigo500.copy(alpha = 0.55f)
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    BrandFrogAvatar(size = 28.dp)
                }

                Box(
                    modifier = Modifier
                        .size(10.5.dp)
                        .clip(CircleShape)
                        .background(if (hasApiKey) Emerald400 else Amber400)
                        .border(1.8.dp, DarkBg, CircleShape)
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
                        fontSize = 15.5.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Emerald400.copy(alpha = 0.18f),
                                        Color(0xFF06B6D4).copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(
                                width = 0.7.dp,
                                color = Emerald400.copy(alpha = 0.48f),
                                shape = RoundedCornerShape(7.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AI Аудитор",
                            color = Emerald400,
                            fontSize = 9.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isTyping -> Emerald400
                                    hasApiKey -> Emerald400.copy(alpha = 0.75f)
                                    else -> Amber400
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when {
                            isTyping -> "анализирует бюджет..."
                            hasApiKey -> "Gemini 3.8 • Готов к разбору трат"
                            else -> "Офлайн-режим • Нажмите для ключа"
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
            }

            if (onOpenAchievements != null) {
                val achievementsList by AchievementManager.achievementsFlow.collectAsState()
                val unlockedCount = achievementsList.count { it.isUnlocked }

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ObsidianSurface.copy(alpha = 0.9f))
                        .border(1.dp, GlassHighlight, RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenAchievements)
                        .padding(horizontal = 11.dp)
                        .testTag("david_chat_achievements_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🏆", fontSize = 13.5.sp)
                        Text(
                            text = unlockedCount.toString(),
                            color = Amber400,
                            fontSize = 12.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(7.dp))
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface.copy(alpha = 0.9f))
                    .border(1.dp, GlassHighlight, CircleShape)
                    .clickable(onClick = onReset)
                    .testTag("david_chat_reset_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Очистить чат",
                    tint = Slate400,
                    modifier = Modifier.size(16.5.dp)
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
    onRetryLiveSummary: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val isLiveVoiceTurn = message.id.startsWith(DavidViewModel.LIVE_CHAT_MSG_PREFIX)

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
                    isUser = message.isUser,
                    isLiveVoiceTurn = isLiveVoiceTurn,
                    messageId = message.id
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
            is ChatMessageType.LiveSessionSummary -> {
                DavidLiveSummaryBubble(
                    summaryState = type,
                    timestamp = message.timestamp,
                    onRetry = { onRetryLiveSummary?.invoke(message.id) }
                )
            }
        }
    }
}

/**
 * Структурированная карточка итогов голосового диалога David Live в ленте чата.
 */
@Composable
fun DavidLiveSummaryBubble(
    summaryState: ChatMessageType.LiveSessionSummary,
    timestamp: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Emerald400.copy(alpha = 0.55f),
                Color(0xFF06B6D4).copy(alpha = 0.35f),
                Indigo500.copy(alpha = 0.38f)
            )
        )
    }

    Surface(
        shape = shape,
        color = Color(0xFF0C1527),
        border = BorderStroke(1.dp, borderBrush),
        modifier = modifier
            .widthIn(min = 250.dp, max = 355.dp)
            .shadow(10.dp, shape)
            .testTag("david_live_summary_card")
    ) {
        Column(
            modifier = Modifier
                .background(DavidBubbleGradient)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Emerald400.copy(alpha = 0.24f),
                                        Emerald400.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "David Live",
                            tint = Emerald400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "Итоги разговора • David Live",
                            color = Emerald400,
                            fontSize = 12.5.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Только проверенные факты из диалога",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                summaryState.isGenerating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("david_live_summary_loading")
                    ) {
                        CircularProgressIndicator(
                            color = Emerald400,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Давид формирует достоверное резюме разговора...",
                            color = TextLight,
                            fontSize = 12.5.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }

                !summaryState.errorMessage.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkBg.copy(alpha = 0.65f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                            .testTag("david_live_summary_error")
                    ) {
                        Text(
                            text = summaryState.errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald400,
                                contentColor = DarkBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("david_live_summary_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Повторить",
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                summaryState.summary != null -> {
                    val summary = summaryState.summary
                    if (summary.topics.isNotEmpty()) {
                        LiveSummarySectionBlock(
                            title = "📌 О чём говорили",
                            items = summary.topics
                        )
                    }
                    if (summary.keyFindings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveSummarySectionBlock(
                            title = "📊 Ключевые выводы по данным",
                            items = summary.keyFindings
                        )
                    }
                    if (summary.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveSummarySectionBlock(
                            title = "💡 Рекомендации Давида",
                            items = summary.recommendations
                        )
                    }
                    if (summary.nextActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LiveSummarySectionBlock(
                            title = "✅ Следующие действия",
                            items = summary.nextActions
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = timestamp,
                color = Slate400.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun LiveSummarySectionBlock(
    title: String,
    items: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF060B16).copy(alpha = 0.72f))
            .border(1.dp, GlassBorder, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = title,
            color = Emerald400,
            fontSize = 11.5.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        items.forEach { item ->
            Text(
                text = "• $item",
                color = TextLight,
                fontSize = 12.5.sp,
                fontFamily = ManropeFontFamily,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 1.5.dp)
            )
        }
    }
}

/**
 * Интерактивная карточка распознанной в чате операции с кнопками «Записать» / «Отмена»
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
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    Surface(
        shape = shape,
        color = Color(0xFF0C1527),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Emerald400.copy(alpha = 0.52f),
                    Indigo500.copy(alpha = 0.32f)
                )
            )
        ),
        modifier = modifier
            .widthIn(min = 240.dp, max = 340.dp)
            .shadow(8.dp, shape)
    ) {
        Column(
            modifier = Modifier
                .background(DavidBubbleGradient)
                .padding(14.dp)
        ) {
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

private val SmoothChatTextEnterEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

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
        color = Color(0xFF0E1D2F),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Emerald400.copy(alpha = 0.36f),
                    Indigo400.copy(alpha = 0.36f)
                )
            )
        ),
        modifier = modifier
            .widthIn(min = 148.dp, max = 288.dp)
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .background(UserBubbleGradient)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (operation.userPhrase.isNotBlank()) {
                Text(
                    text = operation.userPhrase,
                    color = TextLight,
                    fontSize = 13.5.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF050B16).copy(alpha = 0.72f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryIconBadge(
                        categoryName = operation.category,
                        type = operation.type,
                        size = 26.dp,
                        iconSize = 13.dp
                    )
                    Spacer(modifier = Modifier.width(7.dp))
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
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$amountPrefix${operation.amount.toInt()} ₽",
                    color = accentColor,
                    fontSize = 13.5.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestamp,
                    color = Slate400.copy(alpha = 0.78f),
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
 * Текстовое сообщение в обновлённой эстетике Obsidian Neo-Bank.
 * - Компактно адаптирует ширину и высоту облачка под фактический размер текста
 *   (без принудительного растягивания на всю ширину экрана для коротких реплик).
 * - При потоковой голосовой транскрипции (в том числе когда разговор David Live свёрнут в капсулу)
 *   обеспечивает такое же плавное пословное проявление текста и мягкий вход облачка,
 *   как и на полноэкранном визуализаторе David Live.
 */
@Composable
fun DavidTextBubble(
    content: String,
    timestamp: String,
    isUser: Boolean,
    isLiveVoiceTurn: Boolean = false,
    messageId: String = "",
    modifier: Modifier = Modifier
) {
    val useRichMarkdownBlock = !isUser &&
        !isLiveVoiceTurn &&
        (content.contains("#") || content.contains("\n- ") || (content.contains("**") && (content.length > 90 || content.contains('\n'))))

    val isShortSingleLine = content.length <= 48 && !content.contains('\n') && !useRichMarkdownBlock

    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = if (isShortSingleLine) 16.dp else 20.dp,
            topEnd = if (isShortSingleLine) 16.dp else 20.dp,
            bottomStart = if (isShortSingleLine) 16.dp else 20.dp,
            bottomEnd = 6.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (isShortSingleLine) 16.dp else 20.dp,
            topEnd = if (isShortSingleLine) 16.dp else 20.dp,
            bottomStart = 6.dp,
            bottomEnd = if (isShortSingleLine) 16.dp else 20.dp
        )
    }

    val borderBrush = remember(isUser, isLiveVoiceTurn) {
        if (isUser) {
            Brush.linearGradient(
                colors = listOf(
                    Emerald400.copy(alpha = if (isLiveVoiceTurn) 0.52f else 0.36f),
                    Color(0xFF38BDF8).copy(alpha = 0.34f),
                    Indigo400.copy(alpha = 0.42f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Emerald400.copy(alpha = if (isLiveVoiceTurn) 0.46f else 0.30f),
                    GlassHighlight,
                    GlassBorder
                )
            )
        }
    }

    val backgroundBrush = if (isUser) UserBubbleGradient else DavidBubbleGradient

    // Плавный вход нового облачка в ленту чата без рывков по вертикали
    val entryProgress = remember { Animatable(if (isLiveVoiceTurn) 0.35f else 1f) }
    LaunchedEffect(Unit) {
        if (entryProgress.value < 1f) {
            entryProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = SmoothChatTextEnterEasing
                )
            )
        }
    }

    // Пословное мягкое проявление нового текста при потоковой речи (аналогично DavidLiveSheet):
    // уже произнесённый префикс остаётся на 100% непрозрачности, а только что открытое по аудио слово
    // мгновенно занимает своё финальное место в строке и мягко набирает альфу за 180 мс.
    val msgKey = remember(messageId, isLiveVoiceTurn) {
        if (messageId.isNotBlank()) messageId else "turn_${isLiveVoiceTurn}_${isUser}"
    }
    var previousRenderedText by remember(msgKey) {
        mutableStateOf(if (isLiveVoiceTurn) "" else content)
    }
    var fadeStartIndex by remember(msgKey) {
        mutableIntStateOf(if (isLiveVoiceTurn) 0 else content.length)
    }
    val latestTailAlpha = remember(msgKey) {
        Animatable(if (isLiveVoiceTurn) 0.24f else 1f)
    }

    LaunchedEffect(content, msgKey) {
        val prev = previousRenderedText
        if (content != prev) {
            val splitIdx = computeStreamingTextFadeSplitIndex(
                previousText = prev,
                currentText = content
            )
            previousRenderedText = content
            if (isLiveVoiceTurn && splitIdx < content.length) {
                fadeStartIndex = splitIdx
                latestTailAlpha.snapTo(0.24f)
                latestTailAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = SmoothChatTextEnterEasing
                    )
                )
                fadeStartIndex = content.length
            } else {
                fadeStartIndex = content.length
                latestTailAlpha.snapTo(1f)
            }
        } else if (isLiveVoiceTurn && latestTailAlpha.value < 1f) {
            latestTailAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = SmoothChatTextEnterEasing
                )
            )
            fadeStartIndex = content.length
        }
    }

    val styledContent = remember(
        content,
        fadeStartIndex,
        latestTailAlpha.value,
        isLiveVoiceTurn
    ) {
        val safeSplit = fadeStartIndex.coerceIn(0, content.length)
        if (!isLiveVoiceTurn || safeSplit >= content.length || latestTailAlpha.value >= 0.99f) {
            AnnotatedString(content)
        } else {
            buildAnnotatedString {
                append(content.substring(0, safeSplit))
                pushStyle(
                    SpanStyle(
                        color = TextLight.copy(
                            alpha = latestTailAlpha.value.coerceIn(0.24f, 1f)
                        )
                    )
                )
                append(content.substring(safeSplit))
                pop()
            }
        }
    }

    val bubbleAlpha = entryProgress.value
    val bubbleScale = 0.98f + 0.02f * entryProgress.value

    Surface(
        shape = bubbleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderBrush),
        modifier = modifier
            .widthIn(min = 40.dp, max = if (isUser) 292.dp else 332.dp)
            .alpha(bubbleAlpha)
            .scale(bubbleScale)
            .shadow(if (isUser) 3.dp else 5.dp, bubbleShape)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = SmoothChatTextEnterEasing
                )
            )
    ) {
        if (isShortSingleLine) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(backgroundBrush)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (!isUser) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.14f))
                            .border(0.8.dp, Emerald400.copy(alpha = 0.45f), CircleShape)
                    ) {
                        BrandFrogAvatar(size = 11.dp)
                    }
                }

                Text(
                    text = styledContent,
                    color = TextLight,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.width(2.dp))

                if (isLiveVoiceTurn) {
                    LiveVoiceTurnBadge()
                }

                Text(
                    text = timestamp,
                    color = Slate400.copy(alpha = 0.72f),
                    fontSize = 9.5.sp,
                    fontFamily = ManropeFontFamily
                )

                if (isUser) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = Emerald400.copy(alpha = 0.82f),
                        modifier = Modifier.size(11.5.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .background(backgroundBrush)
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Emerald400.copy(alpha = 0.14f))
                                    .border(0.8.dp, Emerald400.copy(alpha = 0.45f), CircleShape)
                            ) {
                                BrandFrogAvatar(size = 13.dp)
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Давид Жабов",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (isLiveVoiceTurn) {
                            LiveVoiceTurnBadge()
                        }
                    }
                }

                if (useRichMarkdownBlock) {
                    MarkdownFormattedText(
                        markdownText = content,
                        fontSize = 13.5.sp
                    )
                } else {
                    Text(
                        text = styledContent,
                        color = TextLight,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (isUser && isLiveVoiceTurn) {
                        LiveVoiceTurnBadge()
                    }
                    Text(
                        text = timestamp,
                        color = Slate400.copy(alpha = 0.72f),
                        fontSize = 9.5.sp,
                        fontFamily = ManropeFontFamily
                    )
                    if (isUser) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = Emerald400.copy(alpha = 0.82f),
                            modifier = Modifier.size(11.5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceTurnBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Emerald400.copy(alpha = 0.14f))
            .border(0.6.dp, Emerald400.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "David Live",
            tint = Emerald400,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "LIVE",
            color = Emerald400,
            fontSize = 8.5.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
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
    val bubbleShape = RoundedCornerShape(20.dp)

    Surface(
        shape = bubbleShape,
        color = Color(0xFF0C1527),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) Indigo400.copy(alpha = 0.45f) else Emerald400.copy(alpha = 0.42f)
        ),
        modifier = modifier
            .widthIn(max = 315.dp)
            .shadow(6.dp, bubbleShape)
    ) {
        Column(
            modifier = Modifier
                .background(if (isUser) UserBubbleGradient else DavidBubbleGradient)
                .padding(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Emerald400.copy(alpha = 0.15f))
                        .border(1.dp, Emerald400.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
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

            Spacer(modifier = Modifier.height(7.dp))

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
                    color = Slate400.copy(alpha = 0.72f),
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
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
    val rubleFormatter = DecimalFormat("#,###", DecimalFormatSymbols(Locale.GERMAN))

    Surface(
        shape = shape,
        color = Color(0xFF0C1527),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Emerald400.copy(alpha = 0.42f),
                    GlassHighlight,
                    Indigo500.copy(alpha = 0.35f)
                )
            )
        ),
        modifier = modifier
            .widthIn(max = 350.dp)
            .shadow(10.dp, shape)
    ) {
        Column(
            modifier = Modifier
                .background(DavidBubbleGradient)
                .padding(15.dp)
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
                            .background(Emerald400.copy(alpha = 0.16f))
                            .border(0.8.dp, Emerald400.copy(alpha = 0.42f), CircleShape)
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
                        .background(deltaColor.copy(alpha = 0.15f))
                        .border(0.6.dp, deltaColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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
                    .height(94.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF050A14))
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
                    .background(Color(0xFF050A14).copy(alpha = 0.72f))
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
                    fontSize = 12.5.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestamp,
                color = Slate400.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * Единая трансформирующаяся лента умных действий и выбора периода разбора (Morphing Smart Action Strip).
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0xFF0B1426).copy(alpha = 0.96f))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Emerald400.copy(alpha = 0.55f),
                                    Color(0xFF06B6D4).copy(alpha = 0.42f)
                                )
                            ),
                            shape = RoundedCornerShape(21.dp)
                        )
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
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Emerald400.copy(alpha = 0.18f),
                                            Color(0xFF06B6D4).copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(1.dp, Emerald400.copy(alpha = 0.42f), RoundedCornerShape(15.dp))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObsidianQuickChip(
                        text = "🔥 Разбор трат",
                        isAccent = true,
                        onClick = { onTogglePeriodSelector(true) },
                        tag = "action_start_report"
                    )

                    if (stage == DavidStage.FOLLOW_UP) {
                        ObsidianQuickChip(
                            text = "📄 Скачать PDF",
                            isAccent = false,
                            borderColor = Indigo500.copy(alpha = 0.55f),
                            textColor = Indigo400,
                            onClick = onExportPdf,
                            tag = "action_download_pdf"
                        )
                    }

                    ObsidianQuickChip(
                        text = "💡 Где утечки бюджета?",
                        onClick = { onSendPrompt("На чём мне сэкономить и какие главные утечки бюджета?") }
                    )

                    ObsidianQuickChip(
                        text = "🎯 Статус целей",
                        onClick = { onActionClick("GOALS") }
                    )

                    ObsidianQuickChip(
                        text = "📊 Баланс и тренды",
                        onClick = { onSendPrompt("Покажи мой баланс и аналитику трендов") }
                    )

                    ObsidianQuickChip(
                        text = if (hasApiKey) "🔑 API ключ" else "🔑 Подключить Gemini",
                        borderColor = if (!hasApiKey) Amber400.copy(alpha = 0.58f) else GlassHighlight,
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
    borderColor: Color = if (isAccent) Emerald400.copy(alpha = 0.62f) else GlassHighlight,
    textColor: Color = if (isAccent) Emerald400 else TextLight,
    tag: String = "obsidian_quick_chip"
) {
    val backgroundBrush = remember(isAccent) {
        if (isAccent) {
            Brush.horizontalGradient(
                colors = listOf(
                    Emerald400.copy(alpha = 0.20f),
                    Color(0xFF06B6D4).copy(alpha = 0.14f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF111C33).copy(alpha = 0.94f),
                    Color(0xFF0A1222).copy(alpha = 0.94f)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundBrush)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.5.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = if (isAccent) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Нижняя капсула ввода сообщения в обновлённой эстетике Obsidian Neo-Bank.
 *
 * Поддерживает распознавание короткого и долгого нажатия (~550 мс) на кнопку отправки:
 * - Короткое нажатие (< 550 мс) сохраняет обычную отправку сообщения.
 * - Удержание (~550 мс) запускает голосовой режим **David Live** с лёгким haptic feedback,
 *   исключает случайную отправку сообщения при отпускании пальца и сохраняет уже набранный текст нетронутым.
 */
@Composable
fun DavidChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onStartDavidLive: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentTextState by rememberUpdatedState(text)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentOnStartLive by rememberUpdatedState(onStartDavidLive)

    val trimmedInput = text.trim()
    val isSecretInput = trimmedInput.startsWith("AIza") ||
        trimmedInput.startsWith("AQ.") ||
        (trimmedInput.length >= 25 && !trimmedInput.contains(" "))

    val isEnabled = text.isNotBlank()
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) Emerald400.copy(alpha = 0.58f) else GlassHighlight,
        animationSpec = tween(220),
        label = "chat_input_border"
    )

    val capsuleBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF101A30).copy(alpha = 0.98f),
                Color(0xFF0A1120).copy(alpha = 0.98f)
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(16.dp, RoundedCornerShape(27.dp))
                .clip(RoundedCornerShape(27.dp))
                .background(capsuleBrush)
                .border(1.dp, borderColor, RoundedCornerShape(27.dp))
                .padding(start = 15.dp, end = 6.dp)
        ) {
            // Subtle glowing emerald prompt dot when idle
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) Emerald400 else Emerald400.copy(alpha = 0.55f)
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))

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
                            Brush.linearGradient(
                                listOf(
                                    Emerald400,
                                    Color(0xFF10B981)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF132238),
                                    Color(0xFF0D1729)
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        brush = if (isEnabled) {
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Emerald400.copy(alpha = 0.48f),
                                    Color(0xFF06B6D4).copy(alpha = 0.35f)
                                )
                            )
                        },
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var longPressTriggered = false

                            val upBeforeTimeout = withTimeoutOrNull(
                                DavidSendButtonGestureHelper.LONG_PRESS_DURATION_MS
                            ) {
                                waitForUpOrCancellation()
                            }

                            if (upBeforeTimeout == null) {
                                // Удержание >= 550 мс: запускаем David Live без отправки и без очистки текста
                                longPressTriggered = true
                                val evaluation = DavidSendButtonGestureHelper.evaluatePress(
                                    pressDurationMs = DavidSendButtonGestureHelper.LONG_PRESS_DURATION_MS,
                                    currentDraftText = currentTextState,
                                    wasCancelled = false
                                )
                                if (evaluation.shouldTriggerHaptic) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                currentOnStartLive?.invoke()
                                // Дожидаемся отпускания пальца и блокируем случайную отправку сообщения
                                waitForUpOrCancellation()
                            } else if (!longPressTriggered) {
                                // Короткое нажатие (< 550 мс): отправляем сообщение, только если жест не отменён и текст не пуст
                                val evaluation = DavidSendButtonGestureHelper.evaluatePress(
                                    pressDurationMs = 120L,
                                    currentDraftText = currentTextState,
                                    wasCancelled = false
                                )
                                if (evaluation.action == com.example.davidapp.live.SendButtonGestureAction.SHORT_TAP_SEND) {
                                    currentOnSend()
                                }
                            }
                        }
                    }
                    .testTag("david_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить (удерживайте для David Live)",
                    tint = if (isEnabled) DarkBg else Emerald400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (onStartDavidLive != null) {
            Text(
                text = "Удерживайте отправку ~0.5 сек для голосового диалога David Live",
                color = Slate400.copy(alpha = 0.72f),
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                modifier = Modifier
                    .padding(top = 3.dp, end = 10.dp)
                    .testTag("david_live_long_press_hint")
            )
        }
    }
}
