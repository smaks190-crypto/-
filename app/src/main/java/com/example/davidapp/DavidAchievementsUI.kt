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
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Фирменные неоновые цвета аудитора Давида Жабова
private val DarkOverlay = Color(0xF20F172A)
private val CardBackground = Color(0xFF1E293B)
private val LockedCardBackground = Color(0xFF131A29)
private val NeonGreen = Color(0xFF00E676)
private val NeonGold = Color(0xFFFFD700)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonViolet = Color(0xFF818CF8)
private val TextMain = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val BorderDim = Color(0xFF334155)

/**
 * Всплывающее сверху неоновое уведомление о получении новой ачивки.
 * Автоматически скрывается через 4 секунды или сразу по клику пользователя.
 */
@Composable
fun AchievementUnlockedBanner(
    modifier: Modifier = Modifier
) {
    val currentBanner by AchievementManager.unlockedBannerQueue.collectAsState()

    AnimatedVisibility(
        visible = currentBanner != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        val achievement = currentBanner ?: return@AnimatedVisibility

        val infiniteTransition = rememberInfiniteTransition(label = "bannerGlow")
        val glowPhase by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )

        val neonBorderGradient = Brush.horizontalGradient(
            listOf(
                NeonGreen.copy(alpha = glowPhase),
                NeonGold.copy(alpha = glowPhase),
                NeonCyan.copy(alpha = glowPhase)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), spotColor = NeonGreen)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkOverlay)
                .border(2.dp, neonBorderGradient, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { AchievementManager.dismissBanner() }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("achievement_unlocked_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Иконка ачивки в неоновом круге
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, Brush.radialGradient(listOf(NeonGold, NeonGreen)), CircleShape)
                ) {
                    Text(text = achievement.iconEmoji, fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🏆 АЧИВКА РАЗБЛОКИРОВАНА!",
                            color = NeonGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonGreen.copy(alpha = 0.15f))
                                .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${achievement.xpReward} XP",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = achievement.title,
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Давид: «${achievement.davidQuote}»",
                        color = NeonCyan.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Диалог просмотра списка всех достижений от ИИ-аудитора Давида Жабова
 */
@Composable
fun AchievementsDialog(
    onDismiss: () -> Unit
) {
    val achievements by AchievementManager.achievementsFlow.collectAsState()
    val rankInfo by AchievementManager.playerRankFlow.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("achievements_dialog")
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkOverlay),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(listOf(NeonGreen.copy(alpha = 0.8f), NeonViolet.copy(alpha = 0.5f)))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
                    .align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 1. Шапка модального окна
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🏆", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Зал Славы Давида",
                                color = TextMain,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .testTag("close_achievements_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Карточка профиля игрока и ранга от Давида
                    PlayerRankCard(rankInfo = rankInfo)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "СПИСОК ДОСТИЖЕНИЙ (${rankInfo.unlockedCount}/${rankInfo.totalCount})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    // 3. Список всех 10 ачивок
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(items = achievements, key = { it.id }) { item ->
                            AchievementItemCard(achievement = item)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка игрового профиля с текущим званием, шкалой опыта и прогрессом
 */
@Composable
private fun PlayerRankCard(rankInfo: PlayerRankInfo) {
    val rankBadgeEmoji = when (rankInfo.level) {
        6 -> "💎"
        5 -> "👑"
        4 -> "🧐"
        3 -> "🐸"
        2 -> "💰"
        else -> "🫧"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A),
                        Color(0xFF14532D).copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(NeonViolet, NeonGreen)),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.5.dp, NeonGold, CircleShape)
                    ) {
                        Text(text = rankBadgeEmoji, fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = rankInfo.title,
                            color = NeonGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Уровень ${rankInfo.level} • ${rankInfo.currentXp} XP",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${rankInfo.unlockedCount}/${rankInfo.totalCount} Открыто",
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Шкала прогресса XP до следующего звания
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Прогресс ранга",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = if (rankInfo.level >= 6) "МАКС. РАНГ" else "${rankInfo.currentXp} / ${rankInfo.nextLevelXp} XP",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { rankInfo.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NeonGreen,
                trackColor = Color(0xFF1E293B)
            )
        }
    }
}

/**
 * Карточка отдельного достижения
 */
@Composable
private fun AchievementItemCard(achievement: Achievement) {
    val isSecretLocked = achievement.isSecret && !achievement.isUnlocked

    val cardBg = if (achievement.isUnlocked) CardBackground else LockedCardBackground
    val borderBrush = if (achievement.isUnlocked) {
        Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.8f), NeonGold.copy(alpha = 0.6f)))
    } else {
        Brush.horizontalGradient(listOf(BorderDim, BorderDim))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(
                width = if (achievement.isUnlocked) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Иконка ачивки
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (achievement.isUnlocked) Color(0xFF0F172A) else Color(0xFF0B1120))
                    .border(
                        1.dp,
                        if (achievement.isUnlocked) NeonGreen else BorderDim,
                        CircleShape
                    )
            ) {
                if (isSecretLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Секретно",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = achievement.iconEmoji,
                        fontSize = 22.sp,
                        color = if (achievement.isUnlocked) Color.Unspecified else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSecretLocked) "🔒 Секретная ачивка от Жабы" else achievement.title,
                        color = if (achievement.isUnlocked) TextMain else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (achievement.isUnlocked) NeonGreen.copy(alpha = 0.15f)
                                else Color(0xFF1E293B)
                            )
                            .border(
                                0.5.dp,
                                if (achievement.isUnlocked) NeonGreen.copy(alpha = 0.6f) else BorderDim,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+${achievement.xpReward} XP",
                            color = if (achievement.isUnlocked) NeonGreen else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isSecretLocked) {
                        "Давид держит условия в строжайшей тайне. Совершай финансовые подвиги, чтобы узнать!"
                    } else {
                        achievement.description
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                if (achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Блок цитаты Давида Жабова
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.5.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🐸 Давид: «${achievement.davidQuote}»",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    if (achievement.unlockedAt != null && achievement.unlockedAt > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedDate = remember(achievement.unlockedAt) {
                            val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru", "RU"))
                            sdf.format(Date(achievement.unlockedAt))
                        }
                        Text(
                            text = "Получено: $formattedDate",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
