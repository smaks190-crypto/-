package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NotificationEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatUnreadSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Indigo500.copy(alpha = 0.35f),
                thickness = 1.dp
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate800,
                border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.6f)),
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Rose500)
                    )
                    Text(
                        text = "Новые сообщения",
                        color = Indigo400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Indigo500.copy(alpha = 0.35f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
fun ChatNotificationUser(notification: NotificationEntity, profileName: String) {
    val (ops, userPhrase, _) = remember(notification) { extractOpsAndComment(notification) }
    val timeStr = remember(notification.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color(0xFF2B5278), // Telegram user bubble color
            border = BorderStroke(1.dp, Color(0xFF386797)),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (userPhrase.isNotBlank()) {
                    Text(
                        text = userPhrase,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (op in ops) {
                            Text(
                                text = "• ${op.category} (${op.subcategory})",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (notification.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                        contentDescription = if (notification.isRead) "Прочитано" else "Отправлено",
                        tint = if (notification.isRead) Emerald400 else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatNotificationDavid(notification: NotificationEntity) {
    val (ops, _, comment) = remember(notification) { extractOpsAndComment(notification) }
    val timeStr = remember(notification.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp))
    }

    if (ops.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = Slate800.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Slate700),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Жабов Давид",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val davidText = if (comment.isNotBlank() && !comment.startsWith("||")) {
                        comment
                    } else {
                        "Принял ${ops.size} операций. Отличный учет! 🐸"
                    }
                    Text(
                        text = davidText,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = timeStr,
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    } else if (ops.size == 1) {
        val op = ops[0]
        val isIncome = op.type == "income"
        val commentText = if (comment.isNotBlank() && !comment.startsWith("||")) comment else ""
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = Slate800.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Slate700),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Жабов Давид",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val davidText = if (commentText.isNotBlank()) {
                        commentText
                    } else {
                        "Зафиксировал ${if (isIncome) "доход" else "расход"}. Отличный учет! 🐸"
                    }
                    Text(
                        text = davidText,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = timeStr,
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = Slate800.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Slate700),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = notification.title,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notification.description,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = timeStr,
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatConnectingIndicator(isRestored: Boolean) {
    val dotColor by animateColorAsState(
        targetValue = if (isRestored) Emerald400 else Rose500,
        animationSpec = tween(durationMillis = 500),
        label = "dot_color"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "ping_pong")
    val rawPingPong by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ping_pong_pos"
    )
    val dotPositionFraction by animateFloatAsState(
        targetValue = if (isRestored) 0f else rawPingPong,
        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "dot_pos"
    )
    val lineScaleX by animateFloatAsState(
        targetValue = if (isRestored) 0f else 1f,
        animationSpec = tween(durationMillis = 600, delayMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "line_scale"
    )
    val alphaScale by animateFloatAsState(
        targetValue = if (isRestored) 0f else 1f,
        animationSpec = tween(durationMillis = 400, delayMillis = 700),
        label = "alpha_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = alphaScale }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(24.dp)
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val maxOffsetPx = (totalWidthPx / 2f) - 12f

            Box(
                modifier = Modifier
                    .height(2.dp)
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = lineScaleX }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                dotColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    translationX = maxOffsetPx * dotPositionFraction
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    dotColor.copy(alpha = 0.7f),
                                    dotColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, CircleShape)
                )
            }
        }
    }
}
