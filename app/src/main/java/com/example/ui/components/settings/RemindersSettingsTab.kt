package com.example.ui.components.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.ReminderManager
import com.example.ui.components.SettingsScreen
import com.example.ui.components.settingsSharedBounds
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

private const val REQUEST_CODE_POST_NOTIFICATIONS = 101

@Composable
fun SettingsRemindersSubContent(
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(ReminderManager.isReminderEnabled(context)) }
    val (savedHour, savedMinute) = remember { ReminderManager.getReminderTime(context) }
    var selectedHour by remember { mutableStateOf(savedHour) }
    var selectedMinute by remember { mutableStateOf(savedMinute) }
    var selectedTone by remember { mutableStateOf(ReminderManager.getReminderTone(context)) }
    var smartSummaryEnabled by remember { mutableStateOf(ReminderManager.isSmartSummaryEnabled(context)) }

    var hasPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    fun checkAndRequestPermission(onGranted: () -> Unit = {}) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                var curr = context
                var activity: android.app.Activity? = null
                while (curr is android.content.ContextWrapper) {
                    if (curr is android.app.Activity) {
                        activity = curr
                        break
                    }
                    curr = curr.baseContext
                }
                if (activity != null) {
                    try {
                        androidx.core.app.ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_POST_NOTIFICATIONS
                        )
                    } catch (_: Throwable) {}
                }
            } else {
                hasPermission = true
                onGranted()
            }
        } else {
            hasPermission = true
            onGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Obsidian Sub-screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .testTag("back_to_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Emerald400.copy(alpha = 0.15f))
                        .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Уведомления",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "УМНЫЕ ТРИГГЕРЫ ДАВИДА",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Ежедневные напоминания",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .clickable {
                    val nextState = !isEnabled
                    isEnabled = nextState
                    if (nextState && !hasPermission) {
                        checkAndRequestPermission()
                    }
                }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .settingsSharedBounds(SettingsScreen.REMINDERS)
                    .weight(1f)
            ) {
                Text(
                    text = "Включить уведомления",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isEnabled) {
                        if (hasPermission) "Умный вечерний чек-ин активен" else "Требуется разрешение"
                    } else "Напоминания отключены",
                    color = if (isEnabled) (if (hasPermission) Emerald400 else Rose500) else Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    isEnabled = checked
                    if (checked && !hasPermission) {
                        checkAndRequestPermission()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DarkBg,
                    checkedTrackColor = Emerald400,
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = Slate800
                )
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !hasPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Rose500.copy(alpha = 0.12f))
                    .border(1.dp, Rose500.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { checkAndRequestPermission() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Разрешение не предоставлено",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Нажмите, чтобы разрешить отправку уведомлений",
                        color = Rose500,
                        fontSize = 10.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        RemindersToneAndScheduleSection(
            isEnabled = isEnabled,
            selectedTone = selectedTone,
            onSelectTone = { selectedTone = it },
            smartSummaryEnabled = smartSummaryEnabled,
            onToggleSmartSummary = { smartSummaryEnabled = it },
            selectedHour = selectedHour,
            onSelectHour = { selectedHour = it },
            selectedMinute = selectedMinute,
            onSelectMinute = { selectedMinute = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Единый нижний ряд действий: Тест пуша + Сохранить + квадратная кнопка ✕ справа
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val sampleCount = if (smartSummaryEnabled) 3 else 0
                    val sampleSum = if (smartSummaryEnabled) 1450.0 else 0.0
                    if (!hasPermission) {
                        checkAndRequestPermission {
                            ReminderManager.showNotification(
                                context = context,
                                todayExpenseCount = sampleCount,
                                todayExpenseSum = sampleSum,
                                overrideTone = selectedTone
                            )
                        }
                    } else {
                        ReminderManager.showNotification(
                            context = context,
                            todayExpenseCount = sampleCount,
                            todayExpenseSum = sampleSum,
                            overrideTone = selectedTone
                        )
                    }
                },
                modifier = Modifier
                    .weight(0.85f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = "Тест пуша",
                    color = Indigo400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    if (isEnabled && !hasPermission) {
                        checkAndRequestPermission {
                            ReminderManager.setReminderEnabled(
                                context = context,
                                enabled = isEnabled,
                                hour = selectedHour,
                                minute = selectedMinute,
                                tone = selectedTone,
                                smartSummary = smartSummaryEnabled
                            )
                            Toast.makeText(
                                context,
                                "Напоминание сохранено (${selectedTone.title}) на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}",
                                Toast.LENGTH_SHORT
                            ).show()
                            onBack()
                        }
                    } else {
                        ReminderManager.setReminderEnabled(
                            context = context,
                            enabled = isEnabled,
                            hour = selectedHour,
                            minute = selectedMinute,
                            tone = selectedTone,
                            smartSummary = smartSummaryEnabled
                        )
                        Toast.makeText(
                            context,
                            if (isEnabled) "Напоминание сохранено (${selectedTone.title}) на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}" else "Напоминания отключены",
                            Toast.LENGTH_SHORT
                        ).show()
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(52.dp)
            ) {
                Text(
                    text = "Сохранить",
                    color = DarkBg,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
