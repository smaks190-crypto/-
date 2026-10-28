package com.example.davidapp.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BrandFrogAvatar

private val ObsidianLiveBg = Color(0xFF090D14)
private val SpeechBubbleBg = Color(0xFF131924)
private val SpeechBubbleBorder = Color(0xFF1E293B)
private val CancelPillBg = Color(0xFF131924)
private val CancelPillBorder = Color(0xFF1F2B3D)
private val CancelCircleBg = Color(0xFF1B2536)

/**
 * Full-screen voice session overlay for **David Live**, matching the approved visual reference:
 * 1. Top header with David Zhabov 3D frog avatar, title **"Давид Жабов"**, subtitle **"AI-помощник по финансам"**,
 *    and settings/key action icon on the right.
 * 2. Dark speech bubble with bottom-left pointer tail displaying live subtitles / voice status
 *    (`"Говорите, я слушаю..."`, `"Слушаю вас..."`, `"Отлично, продолжаю..."`, or live transcription).
 * 3. Central 48-ray circular spectral visualizer [DavidLiveCircularVisualizer] with central microphone mute toggle.
 * 4. Network error / missing API key recovery bar when needed.
 * 5. Bottom `[ ✕  Отменить ]` pill button that cleanly ends the session and triggers post-session summary generation.
 */
@Composable
fun DavidLiveSheet(
    connectionState: DavidLiveConnectionState,
    liveSubtitle: String,
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    errorMessage: String?,
    onToggleMicMute: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianLiveBg,
                        Color(0xFF070A10),
                        ObsidianLiveBg
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("david_live_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Header + Speech Bubble
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                DavidLiveTopHeader(
                    connectionState = connectionState,
                    isMicMuted = isMicMuted,
                    onOpenApiKeySettings = onOpenApiKeySettings
                )

                Spacer(modifier = Modifier.height(22.dp))

                DavidLiveSpeechBubble(
                    subtitleText = liveSubtitle,
                    connectionState = connectionState,
                    errorMessage = errorMessage
                )
            }

            // Center Section: Circular Spectral Visualizer + Recovery controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DavidLiveCircularVisualizer(
                    rayMagnitudes = rayMagnitudes,
                    overallEnergy = overallEnergy,
                    connectionState = connectionState,
                    activeSpeaker = activeSpeaker,
                    isMicMuted = isMicMuted,
                    onToggleMicMute = onToggleMicMute,
                    visualizerSize = 304.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Subtle status caption below visualizer
                Text(
                    text = when {
                        connectionState == DavidLiveConnectionState.NO_API_KEY ->
                            "Укажите ваш Gemini API ключ для голосового диалога"
                        connectionState == DavidLiveConnectionState.ERROR ->
                            "Ошибка соединения с Gemini Live"
                        isMicMuted ->
                            "Микрофон выключен • Нажмите на центр, чтобы включить"
                        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ->
                            "Давид отвечает • Начните говорить, чтобы перебить"
                        connectionState == DavidLiveConnectionState.CONNECTING ||
                            connectionState == DavidLiveConnectionState.RECONNECTING ->
                            "Устанавливаем защищённый аудиоканал..."
                        else ->
                            "Нажмите на микрофон в центре для mute/unmute"
                    },
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )

                AnimatedVisibility(
                    visible = connectionState == DavidLiveConnectionState.ERROR ||
                        connectionState == DavidLiveConnectionState.NO_API_KEY,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (connectionState == DavidLiveConnectionState.ERROR) {
                            Button(
                                onClick = onRetryConnection,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5A8),
                                    contentColor = Color(0xFF061310)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("david_live_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Повторить",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (connectionState == DavidLiveConnectionState.NO_API_KEY) {
                            Button(
                                onClick = onOpenApiKeySettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5A8),
                                    contentColor = Color(0xFF061310)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("david_live_set_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ввести API ключ",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section: [ ✕  Отменить ] pill button matching the reference
            DavidLiveCancelPillButton(
                onClick = onEndSession,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun DavidLiveTopHeader(
    connectionState: DavidLiveConnectionState,
    isMicMuted: Boolean,
    onOpenApiKeySettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111824))
                    .border(
                        width = 1.5.dp,
                        color = when {
                            connectionState == DavidLiveConnectionState.DAVID_SPEAKING -> Color(0xFF00B4FF)
                            isMicMuted -> Color(0xFF475569)
                            else -> Color(0xFF00E5A8)
                        },
                        shape = CircleShape
                    )
            ) {
                BrandFrogAvatar(size = 42.dp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Давид Жабов",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "AI-помощник по финансам",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
        }

        IconButton(
            onClick = onOpenApiKeySettings,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .testTag("david_live_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Настройки API ключа",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DavidLiveSpeechBubble(
    subtitleText: String,
    connectionState: DavidLiveConnectionState,
    errorMessage: String?
) {
    val displayedText = when {
        !errorMessage.isNullOrBlank() -> errorMessage
        subtitleText.isNotBlank() -> subtitleText
        else -> "Говорите, я слушаю..."
    }

    val isError = connectionState == DavidLiveConnectionState.ERROR ||
        connectionState == DavidLiveConnectionState.NO_API_KEY

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = SpeechBubbleBg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isError) Color(0xFFEF4444).copy(alpha = 0.45f) else SpeechBubbleBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("david_live_subtitle_bubble")
        ) {
            Text(
                text = displayedText,
                color = if (isError) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
            )
        }

        // Small pointer triangle under the left side of the speech bubble (matching the reference design)
        Canvas(
            modifier = Modifier
                .padding(start = 26.dp)
                .size(width = 16.dp, height = 9.dp)
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width * 0.35f, size.height)
                close()
            }
            drawPath(path = path, color = SpeechBubbleBg)
        }
    }
}

@Composable
private fun DavidLiveCancelPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CancelPillBg,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .height(56.dp)
            .width(192.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(width = 1.dp, color = CancelPillBorder, shape = RoundedCornerShape(32.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("david_live_cancel_button")
            .semantics {
                contentDescription = "Отменить и завершить David Live"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CancelCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = "Отменить",
                color = Color(0xFFE2E8F0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
