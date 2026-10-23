package com.example.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.ReminderManager
import com.example.notifications.ReminderTone
import com.example.ui.components.WheelPicker
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun RemindersToneAndScheduleSection(
    isEnabled: Boolean,
    selectedTone: ReminderTone,
    onSelectTone: (ReminderTone) -> Unit,
    smartSummaryEnabled: Boolean,
    onToggleSmartSummary: (Boolean) -> Unit,
    selectedHour: Int,
    onSelectHour: (Int) -> Unit,
    selectedMinute: Int,
    onSelectMinute: (Int) -> Unit
) {
    // --- DAVID PERSONA TONE SELECTOR ---
    Text(
        text = "ХАРАКТЕР ПУШЕЙ ОТ ДАВИДА 🐸",
        color = Slate400,
        fontSize = 10.sp,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderTone.entries.forEach { tone ->
            val isSelected = selectedTone == tone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectTone(tone) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) Emerald400.copy(alpha = 0.10f) else ObsidianSurface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Emerald400.copy(alpha = 0.5f) else GlassBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tone.title,
                                color = if (isSelected) Emerald400 else Color.White,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSelected) Emerald400.copy(alpha = 0.2f) else Indigo500.copy(alpha = 0.14f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = tone.badge,
                                    color = if (isSelected) Emerald400 else Indigo400,
                                    fontSize = 9.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tone.subtitle,
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Smart Evening Summary Toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onToggleSmartSummary(!smartSummaryEnabled) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Умная вечерняя сводка трат",
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Присылать итог расходов за день, даже если операции уже внесены",
                color = Slate400,
                fontSize = 11.sp,
                fontFamily = ManropeFontFamily
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = smartSummaryEnabled,
            onCheckedChange = { onToggleSmartSummary(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Indigo500,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Live Preview of the Push Notification
    val (previewTitle, previewBody) = remember(selectedTone, smartSummaryEnabled) {
        ReminderManager.buildMessagePreview(
            tone = selectedTone,
            todayExpenseCount = if (smartSummaryEnabled) 3 else 0,
            todayExpenseSum = if (smartSummaryEnabled) 1450.0 else 0.0
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkBg,
        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ПРЕДПРОСМОТР УВЕДОМЛЕНИЯ",
                color = Indigo400,
                fontSize = 9.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = previewTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = previewBody,
                color = Slate400,
                fontSize = 11.sp,
                fontFamily = ManropeFontFamily,
                lineHeight = 15.sp
            )
        }
    }

    if (isEnabled) {
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "ВРЕМЯ НАПОМИНАНИЯ (24Ч)",
            color = Slate400,
            fontSize = 10.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelPicker(
                    items = (0..23).map { it.toString().padStart(2, '0') },
                    initialIndex = selectedHour.coerceIn(0, 23),
                    onItemSelected = { hourIdx -> onSelectHour(hourIdx) },
                    modifier = Modifier.width(90.dp),
                    visibleItemsCount = 3,
                    itemHeight = 40.dp
                )

                Text(
                    text = " : ",
                    color = Emerald400,
                    fontSize = 22.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                WheelPicker(
                    items = (0..59).map { it.toString().padStart(2, '0') },
                    initialIndex = selectedMinute.coerceIn(0, 59),
                    onItemSelected = { minIdx -> onSelectMinute(minIdx) },
                    modifier = Modifier.width(90.dp),
                    visibleItemsCount = 3,
                    itemHeight = 40.dp
                )
            }
        }
    }
}
