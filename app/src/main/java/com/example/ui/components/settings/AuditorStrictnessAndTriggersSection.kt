package com.example.ui.components.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.AuditorStrictness
import com.example.notifications.ReminderManager
import com.example.ui.theme.Amber400
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private fun strictnessAccentColor(strictness: AuditorStrictness): Color {
    return when (strictness) {
        AuditorStrictness.SOFT_MENTOR -> Emerald400
        AuditorStrictness.CALM_ANALYST -> Indigo400
        AuditorStrictness.SAVAGE_ROAST -> Amber400
        AuditorStrictness.HARDCORE_TOXIC -> Rose500
    }
}

@Composable
fun AuditorStrictnessAndTriggersSection(
    selectedStrictness: AuditorStrictness,
    onStrictnessChange: (AuditorStrictness) -> Unit,
    croakAlertEnabled: Boolean,
    onCroakAlertEnabledChange: (Boolean) -> Unit,
    croakThresholdAmount: Int,
    onCroakThresholdAmountChange: (Int) -> Unit,
    impulseCategoriesWatch: Boolean,
    onImpulseCategoriesWatchChange: (Boolean) -> Unit,
    nightWatchEnabled: Boolean,
    onNightWatchEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activeAccent by animateColorAsState(
        targetValue = strictnessAccentColor(selectedStrictness),
        animationSpec = tween(250),
        label = "strictness_accent"
    )
    val formattedThreshold = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(croakThresholdAmount)

    // --- SECTION 1: AUDITOR STRICTNESS ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ЖЁСТКОСТЬ АУДИТОРА ДАВИДА 🐸",
            color = Slate400,
            fontSize = 10.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "УРОВЕНЬ ${selectedStrictness.levelIndex}/4",
            color = activeAccent,
            fontSize = 10.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 4-Segment Visual Strictness Meter Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AuditorStrictness.entries.forEach { mode ->
                    val isLit = mode.levelIndex <= selectedStrictness.levelIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLit) strictnessAccentColor(selectedStrictness)
                                else Slate800
                            )
                    )
                }
            }

            // Strictness Mode Selector Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditorStrictness.entries.forEach { mode ->
                    val isSelected = mode == selectedStrictness
                    val modeColor = strictnessAccentColor(mode)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auditor_strictness_${mode.id.lowercase()}")
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onStrictnessChange(mode) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) modeColor.copy(alpha = 0.12f) else DarkBg,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) modeColor.copy(alpha = 0.55f) else GlassBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        color = if (isSelected) modeColor else Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(modeColor.copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = mode.badge,
                                            color = modeColor,
                                            fontSize = 9.sp,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.subtitle,
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    lineHeight = 14.sp
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(modeColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = modeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Quote Preview Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = DarkBg,
                border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ПРИМЕР РЕАКЦИИ В ЧАТЕ И АУДИТЕ",
                        color = activeAccent,
                        fontSize = 9.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedStrictness.sampleQuote,
                        color = Slate200,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    // --- SECTION 2: ALERT TRIGGERS & CROAK THRESHOLD ---
    Text(
        text = "ТРИГГЕРЫ ТРЕВОГИ И «ПОРОГ КВАКАНЬЯ» 🚨",
        color = Slate400,
        fontSize = 10.sp,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Switch: Croak Threshold Alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Amber400.copy(alpha = 0.14f))
                            .border(1.dp, Amber400.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Тревога «Порог кваканья»",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (croakAlertEnabled) {
                                "Сирена Давида при трате свыше $formattedThreshold ₽"
                            } else {
                                "Экстренные оповещения отключены"
                            },
                            color = if (croakAlertEnabled) Amber400 else Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }
                Switch(
                    checked = croakAlertEnabled,
                    onCheckedChange = onCroakAlertEnabledChange,
                    modifier = Modifier.testTag("croak_alert_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBg,
                        checkedTrackColor = Emerald400,
                        uncheckedThumbColor = Slate400,
                        uncheckedTrackColor = Slate800
                    )
                )
            }

            if (croakAlertEnabled) {
                HorizontalDivider(color = GlassBorder, thickness = 1.dp)

                // Slider + Current Amount Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Лимит разовой покупки без тревоги",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Amber400.copy(alpha = 0.15f))
                            .border(1.dp, Amber400.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$formattedThreshold ₽",
                            color = Amber400,
                            fontSize = 13.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Slider(
                    value = croakThresholdAmount.toFloat(),
                    onValueChange = { raw ->
                        val stepped = ((raw / 500f).roundToInt() * 500).coerceIn(500, 50000)
                        onCroakThresholdAmountChange(stepped)
                    },
                    valueRange = 500f..50000f,
                    modifier = Modifier.testTag("croak_threshold_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Amber400,
                        activeTrackColor = Amber400,
                        inactiveTrackColor = Slate800
                    )
                )

                // Quick Preset Chips
                val presets = listOf(1000, 3000, 5000, 10000, 25000)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isPresetSelected = croakThresholdAmount == preset
                        val label = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(preset) + " ₽"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPresetSelected) Emerald400.copy(alpha = 0.18f) else DarkBg)
                                .border(
                                    1.dp,
                                    if (isPresetSelected) Emerald400 else GlassBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onCroakThresholdAmountChange(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isPresetSelected) Emerald400 else Slate300,
                                fontSize = 11.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = GlassBorder, thickness = 1.dp)

            // Toggle 2: Impulse Categories Watch
            TriggerToggleItem(
                title = "Охота на импульсивные категории",
                subtitle = "Особый спрос за Фастфуд, Доставку, Такси, Игры и Маркетплейсы",
                checked = impulseCategoriesWatch,
                accentColor = Rose500,
                testTag = "impulse_categories_switch",
                onCheckedChange = onImpulseCategoriesWatchChange
            )

            // Toggle 3: Night Spending Watch
            TriggerToggleItem(
                title = "Ночной дозор Жабова (23:00 – 05:00)",
                subtitle = "Двойная прожарка за полуночный жор и спонтанный ночной шопинг",
                checked = nightWatchEnabled,
                accentColor = Indigo400,
                testTag = "night_watch_switch",
                onCheckedChange = onNightWatchEnabledChange
            )

            // Test Button
            Button(
                onClick = {
                    val sampleAmount = (croakThresholdAmount + 1500)
                    ReminderManager.triggerCroakAlertNotification(
                        context = context,
                        category = "Доставка еды и Фастфуд",
                        amount = sampleAmount,
                        overrideStrictness = selectedStrictness,
                        overrideThreshold = croakThresholdAmount
                    )
                    val (previewTitle, previewBody) = ReminderManager.buildCroakAlertMessage(
                        strictness = selectedStrictness,
                        category = "Доставка еды",
                        amount = sampleAmount,
                        threshold = croakThresholdAmount
                    )
                    Toast.makeText(context, "$previewTitle\n$previewBody", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("test_croak_alert_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo500.copy(alpha = 0.22f)),
                border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Indigo400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Проверить тревогу Жабова 🐸",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TriggerToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Slate400,
                fontSize = 11.sp,
                fontFamily = ManropeFontFamily,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }
}
