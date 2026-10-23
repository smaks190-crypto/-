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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecurityManager
import com.example.notifications.ReminderManager
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.SettingsScreen
import com.example.ui.components.settingsSharedBounds
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun SettingsHubMainContent(
    securityManager: SecurityManager,
    apiKey: String,
    currentProfileName: String,
    profileId: String = "default",
    onAvatarChanged: () -> Unit = {},
    onRenameProfile: (String) -> Unit,
    onResetAllData: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToVoiceAI: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToApiKey: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onExitBudget: (() -> Unit)? = null
) {
    var showConfirmReset by remember { mutableStateOf(false) }

    if (showConfirmReset) {
        ConfirmDialog(
            title = "Сброс всех данных",
            message = "Вы уверены, что хотите полностью стереть все бюджеты, транзакции, категории и настройки? Это действие абсолютно необратимо!",
            onConfirm = {
                showConfirmReset = false
                onResetAllData()
            },
            onDismiss = {
                showConfirmReset = false
            }
        )
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // --- TOP BAR: TITLE + SWITCH BUDGET + CLOSE ---
        SettingsHubTopBar(
            onDismiss = onDismiss,
            onExitBudget = onExitBudget
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- OBSIDIAN BENTO PROFILE CARD ---
        SettingsProfileBentoCard(
            currentProfileName = currentProfileName,
            profileId = profileId,
            onAvatarChanged = onAvatarChanged,
            onRenameProfile = onRenameProfile
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section 0: Main
            SettingsCategoryHeader(text = "ОСНОВНЫЕ ПАРАМЕТРЫ")

            SettingsItemCard(
                icon = Icons.Default.Tune,
                iconTint = Emerald400,
                title = "Основные настройки",
                subtitle = "Валюта, язык и оформление",
                badgeText = null,
                badgeColor = Emerald400,
                testTag = "settings_general_item",
                onClick = onNavigateToGeneral
            )

            SettingsItemCard(
                icon = Icons.Default.List,
                iconTint = Emerald400,
                title = "Категории операций",
                subtitle = "Управление доходами и расходами",
                badgeText = null,
                badgeColor = Emerald400,
                testTag = "settings_categories_item",
                onClick = onNavigateToCategories
            )

            // Section 1: AI & Notifications
            SettingsCategoryHeader(text = "ИИ ДАВИД И УВЕДОМЛЕНИЯ")

            val activeStrictness = ReminderManager.getAuditorStrictness(context)
            val croakThreshold = ReminderManager.getCroakThresholdAmount(context)
            val isCroakOn = ReminderManager.isCroakAlertEnabled(context)
            SettingsItemCard(
                icon = Icons.Default.Mic,
                iconTint = Indigo400,
                title = "Голос и ИИ-Ассистент",
                subtitle = buildString {
                    append(activeStrictness.title)
                    if (isCroakOn) append(" • Порог ${croakThreshold} ₽")
                },
                badgeText = if (apiKey.isNotBlank()) activeStrictness.badge else "Ключ API",
                badgeColor = if (apiKey.isNotBlank()) Emerald400 else Amber400,
                testTag = "settings_voice_ai_item",
                onClick = onNavigateToVoiceAI
            )

            val isReminderActive = ReminderManager.isReminderEnabled(context)
            val (remHour, remMin) = ReminderManager.getReminderTime(context)
            val remTone = ReminderManager.getReminderTone(context)
            SettingsItemCard(
                icon = Icons.Default.Notifications,
                iconTint = Emerald400,
                title = "Ежедневные напоминания",
                subtitle = if (isReminderActive) {
                    "${remTone.title} • в ${remHour.toString().padStart(2, '0')}:${remMin.toString().padStart(2, '0')}"
                } else {
                    "Умные триггеры и сводка расходов"
                },
                badgeText = if (isReminderActive) "Вкл" else "Выкл",
                badgeColor = if (isReminderActive) Emerald400 else Slate400,
                testTag = "settings_reminder_item",
                onClick = onNavigateToReminders
            )

            // Section 2: Security & Backup
            SettingsCategoryHeader(text = "БЕЗОПАСНОСТЬ И ДАННЫЕ")

            SettingsItemCard(
                icon = Icons.Default.Lock,
                iconTint = if (securityManager.isPinEnabled()) Emerald400 else Slate400,
                title = "Защита приложения",
                subtitle = if (securityManager.isPinEnabled()) {
                    buildString {
                        append("ПИН-код")
                        if (securityManager.isBiometricEnabled()) append(" + Биометрия")
                        if (securityManager.isGuestPinEnabled()) append(" + Скрытый 🐸")
                    }
                } else "ПИН-код отключен",
                badgeText = if (securityManager.isPinEnabled()) "Вкл" else "Выкл",
                badgeColor = if (securityManager.isPinEnabled()) Emerald400 else Slate400,
                testTag = "settings_security_item",
                onClick = onNavigateToSecurity
            )

            SettingsItemCard(
                icon = Icons.Default.Storage,
                iconTint = Indigo400,
                title = "Бэкап и Восстановление",
                subtitle = "Экспорт и импорт JSON / CSV",
                badgeText = null,
                badgeColor = Indigo400,
                testTag = "settings_backup_item",
                onClick = onNavigateToBackup
            )

            SettingsItemCard(
                icon = Icons.Default.Info,
                iconTint = Slate400,
                title = "Политика конфиденциальности",
                subtitle = "Локальное хранение и работа с Gemini API",
                badgeText = null,
                badgeColor = Slate600,
                testTag = "settings_privacy_item",
                onClick = onNavigateToPrivacy
            )

            // Section 3: Danger Zone
            SettingsCategoryHeader(text = "ОПАСНАЯ ЗОНА")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_data_item")
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showConfirmReset = true },
                shape = RoundedCornerShape(18.dp),
                color = Rose500.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Rose500.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rose500.copy(alpha = 0.15f))
                                .border(1.dp, Rose500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Сбросить данные к дефолтным",
                                color = Rose500,
                                fontSize = 14.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Полное удаление всех бюджетов и операций",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                maxLines = 1,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Rose500.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Section 4: App Info Footer
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "David Finance AI",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "v${com.example.BuildConfig.VERSION_NAME} • Obsidian UI",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(text: String) {
    Text(
        text = text,
        color = Slate400,
        fontSize = 10.sp,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsItemCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = Emerald400,
    testTag: String,
    screenKey: SettingsScreen? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .settingsSharedBounds(screenKey)
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.13f))
                        .border(1.dp, iconTint.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        maxLines = 1,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Slate400.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
