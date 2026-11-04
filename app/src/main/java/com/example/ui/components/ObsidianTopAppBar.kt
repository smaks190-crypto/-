package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BudgetProfileEntity
import com.example.data.preferences.BudgetPreferencesRepository
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate400
import com.example.ui.viewmodel.BudgetWelcomeNotificationHelper
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun ObsidianTopAppBar(
    currentProfile: BudgetProfileEntity?,
    avatarBitmap: ImageBitmap?,
    isDavidChatOpen: Boolean,
    unreadNotificationsCount: Int,
    splashStage: String,
    showWelcomeBubble: Boolean,
    onDismissWelcomeBubble: () -> Unit,
    onOpenSettingsHub: () -> Unit,
    onOpenDebugConsole: () -> Unit,
    onOpenDavidChat: () -> Unit
) {
    var avatarTapCount by remember { mutableStateOf(0) }
    var lastAvatarTapTime by remember { mutableStateOf(0L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastAvatarTapTime < 500) {
                        avatarTapCount++
                        if (avatarTapCount >= 3) {
                            onOpenDebugConsole()
                            avatarTapCount = 0
                        }
                    } else {
                        avatarTapCount = 1
                    }
                    lastAvatarTapTime = now
                    onOpenSettingsHub()
                }
                .padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ObsidianSurface, CircleShape)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Emerald400.copy(alpha = 0.7f),
                                    GlassBorder
                                )
                            ),
                            shape = CircleShape
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = "Аватар",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = getProfileInitials(currentProfile?.name ?: ""),
                            color = Emerald400,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Column {
                Text(
                    text = currentProfile?.name ?: "Личный бюджет",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "Профиль и настройки ›",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val pName = currentProfile?.name ?: "default"
            val hasOpenedChatBefore = remember(isDavidChatOpen, pName) {
                BudgetPreferencesRepository.hasOpenedDavidChatBefore(context, pName)
            }
            val hasUnread = unreadNotificationsCount > 0 || !hasOpenedChatBefore
            val shouldShowMessage = (splashStage == "done") && (showWelcomeBubble || (hasUnread && unreadNotificationsCount > 0))
            val iconNeonColor = Emerald400

            var isExpandedByNeon by remember { mutableStateOf(false) }
            var isNeonFlickering by remember { mutableStateOf(false) }
            val haptic = LocalHapticFeedback.current
            val neonAlpha = remember { Animatable(1f) }

            LaunchedEffect(shouldShowMessage, unreadNotificationsCount, splashStage) {
                if (shouldShowMessage) {
                    if (isExpandedByNeon) {
                        delay(4000)
                        isExpandedByNeon = false
                        return@LaunchedEffect
                    }

                    delay(500)
                    isExpandedByNeon = false
                    isNeonFlickering = true

                    neonAlpha.snapTo(0.25f)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(70)
                    neonAlpha.snapTo(1f)
                    delay(50)
                    neonAlpha.snapTo(0.35f)
                    delay(80)
                    neonAlpha.snapTo(1f)

                    isNeonFlickering = false
                    isExpandedByNeon = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    delay(4000)
                    isExpandedByNeon = false
                } else {
                    isNeonFlickering = false
                    isExpandedByNeon = false
                    neonAlpha.snapTo(1f)
                }
            }

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .drawBehind {
                        val borderColor = if (isNeonFlickering) {
                            iconNeonColor.copy(alpha = neonAlpha.value.coerceIn(0.15f, 1f))
                        } else if (shouldShowMessage || hasUnread) {
                            iconNeonColor.copy(alpha = 0.6f)
                        } else {
                            GlassBorder
                        }
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )
                    .clickable {
                        onDismissWelcomeBubble()
                        isExpandedByNeon = false
                        onOpenDavidChat()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrandFrogAvatar(size = 20.dp)
                    if (!isExpandedByNeon || !shouldShowMessage) {
                        Text(
                            text = "Давид AI",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        if (unreadNotificationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Emerald400)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isExpandedByNeon && shouldShowMessage,
                        enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec = tween(200)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                        ) {
                            val welcomeGreeting = remember(currentProfile?.id, currentProfile?.name) {
                                val profileKey = currentProfile?.id ?: currentProfile?.name ?: "default"
                                val hasOpened = BudgetPreferencesRepository.hasOpenedProfileBefore(context, profileKey)
                                val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                val timeOfDayGreeting = BudgetWelcomeNotificationHelper.getGreetingForHour(hourNow)
                                if (!hasOpened) {
                                    BudgetPreferencesRepository.markProfileOpened(context, profileKey)
                                    "Добро пожаловать"
                                } else {
                                    timeOfDayGreeting
                                }
                            }
                            if (hasUnread && unreadNotificationsCount > 0) {
                                Text(
                                    text = "Новое сообщение от Давида",
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$welcomeGreeting, ",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${currentProfile?.name ?: "Друг"}!",
                                        color = Emerald400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun getProfileInitials(name: String): String {
    val clean = name.trim().uppercase()
    if (clean.isEmpty()) return "Б"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first()}${parts[1].first()}"
    } else if (parts.isNotEmpty()) {
        "${parts[0].first()}"
    } else {
        "Б"
    }
}
