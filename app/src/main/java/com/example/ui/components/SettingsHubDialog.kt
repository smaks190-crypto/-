package com.example.ui.components

import com.example.ui.components.settings.GeneralSettingsTab
import com.example.ui.components.settings.VoiceAndAISettingsTab
import com.example.ui.components.settings.SecuritySettingsTab
import com.example.ui.components.settings.BackupRestoreSettingsTab
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.example.ui.theme.Slate700
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecurityManager
import com.example.notifications.ReminderManager
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import java.io.File

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.AnimatedVisibilityScope?> { null }

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.settingsSharedBounds(screenKey: SettingsScreen?): Modifier {
    if (screenKey == null) return this
    val sharedScope = LocalSharedTransitionScope.current
    val animScope = LocalAnimatedVisibilityScope.current
    return if (sharedScope != null && animScope != null) {
        with(sharedScope) {
            this@settingsSharedBounds.sharedBounds(
                rememberSharedContentState(key = "card_$screenKey"),
                animatedVisibilityScope = animScope,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    } else {
        this
    }
}

enum class SettingsScreen {
    HUB,
    GENERAL,
    VOICE_AI,
    SECURITY,
    REMINDERS,
    API_KEY,
    PRIVACY,
    BACKUP
}

private const val REQUEST_CODE_POST_NOTIFICATIONS = 101

@OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun SettingsHubDialog(
    securityManager: SecurityManager,
    apiKey: String,
    currentProfileName: String,
    profileId: String = "default",
    onAvatarChanged: () -> Unit = {},
    onRenameProfile: (String) -> Unit,
    onResetAllData: () -> Unit,
    onDismiss: () -> Unit,
    initialScreen: SettingsScreen = SettingsScreen.HUB,
    onSaveApiKey: ((String) -> Unit)? = null,
    onSecurityUpdated: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenReminders: (() -> Unit)? = null,
    onOpenApiKey: (() -> Unit)? = null,
    onOpenCategories: (() -> Unit)? = null,
    onOpenExportImport: (() -> Unit)? = null,
    onExitBudget: (() -> Unit)? = null
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Slate900,
        dragHandle = null
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 340.dp)
                .padding(horizontal = 4.dp)
                .navigationBarsPadding()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .border(1.dp, Slate800, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Slate700)
                )
                androidx.compose.animation.SharedTransitionLayout {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            modifier = Modifier.fillMaxWidth(),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith 
                                fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                            },
                            contentAlignment = Alignment.TopStart,
                            label = "settings_transition"
                        ) { screen ->
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalAnimatedVisibilityScope provides this
                            ) {
                                when (screen) {
                                    SettingsScreen.HUB -> {
                                        SettingsHubMainContent(
                                            securityManager = securityManager,
                                            apiKey = apiKey,
                                            currentProfileName = currentProfileName,
                                            profileId = profileId,
                                            onAvatarChanged = onAvatarChanged,
                                            onRenameProfile = onRenameProfile,
                                            onResetAllData = onResetAllData,
                                            onDismiss = onDismiss,
                                            onNavigateToGeneral = {
                                                currentScreen = SettingsScreen.GENERAL
                                            },
                                            onNavigateToVoiceAI = {
                                                currentScreen = SettingsScreen.VOICE_AI
                                            },
                                            onNavigateToSecurity = {
                                                currentScreen = SettingsScreen.SECURITY
                                            },
                                            onNavigateToReminders = {
                                                currentScreen = SettingsScreen.REMINDERS
                                            },
                                            onNavigateToApiKey = {
                                                currentScreen = SettingsScreen.VOICE_AI
                                            },
                                            onNavigateToPrivacy = {
                                                currentScreen = SettingsScreen.PRIVACY
                                            },
                                            onNavigateToBackup = {
                                                currentScreen = SettingsScreen.BACKUP
                                            },
                                            onNavigateToCategories = {
                                                onOpenCategories?.invoke()
                                            },
                                            onExitBudget = onExitBudget
                                        )
                                    }
                                    SettingsScreen.GENERAL -> {
                                        GeneralSettingsTab(
                                            onOpenCategories = { onOpenCategories?.invoke() },
                                            onResetData = onResetAllData,
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss
                                        )
                                    }
                                    SettingsScreen.VOICE_AI, SettingsScreen.API_KEY -> {
                                        VoiceAndAISettingsTab(
                                            initialKey = apiKey,
                                            onSaveApiKey = { newKey ->
                                                onSaveApiKey?.invoke(newKey)
                                                currentScreen = SettingsScreen.HUB
                                            },
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss
                                        )
                                    }
                                    SettingsScreen.SECURITY -> {
                                        SecuritySettingsTab(
                                            securityManager = securityManager,
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss,
                                            onSecurityUpdated = { onSecurityUpdated?.invoke() }
                                        )
                                    }
                                    SettingsScreen.BACKUP -> {
                                        BackupRestoreSettingsTab(
                                            onExportJson = { onOpenExportImport?.invoke() },
                                            onExportCsv = { onOpenExportImport?.invoke() },
                                            onImportJson = { onOpenExportImport?.invoke() },
                                            onImportCsv = { onOpenExportImport?.invoke() },
                                            onResetData = onResetAllData,
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss
                                        )
                                    }
                                    SettingsScreen.REMINDERS -> {
                                        SettingsRemindersSubContent(
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss
                                        )
                                    }
                                    SettingsScreen.API_KEY -> {
                                        SettingsApiKeySubContent(
                                            initialKey = apiKey,
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss,
                                            onSave = { newKey ->
                                                onSaveApiKey?.invoke(newKey)
                                                currentScreen = SettingsScreen.HUB
                                            }
                                        )
                                    }
                                    SettingsScreen.PRIVACY -> {
                                        SettingsPrivacySubContent(
                                            onBack = { currentScreen = SettingsScreen.HUB },
                                            onClose = onDismiss
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
}

@Composable
private fun SettingsHubMainContent(
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
    var nameInput by remember(currentProfileName) { mutableStateOf(currentProfileName) }
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

    val initials = remember(nameInput) {
        val clean = nameInput.trim().uppercase()
        if (clean.isEmpty()) "Б"
        else {
            val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 2) {
                "${parts[0].first()}${parts[1].first()}"
            } else if (parts.isNotEmpty()) {
                "${parts[0].first()}"
            } else {
                "Б"
            }
        }
    }

    val context = LocalContext.current
    var avatarUpdateTrigger by remember { mutableStateOf(0) }
    var tempBitmapToCrop by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val avatarBitmap = remember(profileId, avatarUpdateTrigger) {
        val file = File(context.filesDir, "avatar_$profileId.jpg")
        if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val sourceBitmap = BitmapFactory.decodeStream(inputStream)
                if (sourceBitmap != null) {
                    tempBitmapToCrop = sourceBitmap
                } else {
                    Toast.makeText(context, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Ошибка при загрузке изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (tempBitmapToCrop != null) {
        AvatarCropDialog(
            bitmap = tempBitmapToCrop!!,
            onDismiss = { tempBitmapToCrop = null },
            onCropped = { croppedBitmap ->
                try {
                    val file = File(context.filesDir, "avatar_$profileId.jpg")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
                    }
                    avatarUpdateTrigger++
                    onAvatarChanged()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Ошибка сохранения аватара", Toast.LENGTH_SHORT).show()
                }
                tempBitmapToCrop = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // --- HEADER WITH AVATAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    // Neon Glow Background
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Indigo500.copy(alpha = 0.6f),
                                        Indigo500.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Emerald400, Indigo500, Rose500)
                                )
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(DarkBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "Аватар",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = initials,
                                    color = Emerald400,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    // Overlay edit badge
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Indigo500)
                            .border(1.dp, Slate900, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Изменить фото",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Привет 👋",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                        if (avatarBitmap != null) {
                            Text(
                                text = "Удалить фото",
                                color = Rose500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    try {
                                        val file = File(context.filesDir, "avatar_$profileId.jpg")
                                        if (file.exists()) {
                                            file.delete()
                                        }
                                        avatarUpdateTrigger++
                                        onAvatarChanged()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val focusManager = LocalFocusManager.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = nameInput,
                            onValueChange = {
                                if (it.length <= 25) {
                                    nameInput = it
                                }
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Emerald400),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onRenameProfile(nameInput)
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier
                                .widthIn(min = 80.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused && nameInput != currentProfileName) {
                                        onRenameProfile(nameInput)
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = Slate600,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (onExitBudget != null) {
                IconButton(
                    onClick = {
                        onDismiss()
                        onExitBudget()
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(Rose500.copy(alpha = 0.12f))
                        .border(1.dp, Rose500.copy(alpha = 0.4f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Выйти в списки бюджетов",
                        tint = Rose500,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section 0: Main
            SettingsCategoryHeader(text = "ОСНОВНЫЕ")

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

            // Section 1: Security
            SettingsCategoryHeader(text = "БЕЗОПАСНОСТЬ И БЭКАП")

            SettingsItemCard(
                icon = Icons.Default.Lock,
                iconTint = if (securityManager.isPinEnabled()) Emerald400 else Slate400,
                title = "Защита приложения",
                subtitle = if (securityManager.isPinEnabled()) {
                    if (securityManager.isBiometricEnabled()) "ПИН-код + Биометрия" else "ПИН-код активен"
                } else "ПИН-код отключен",
                badgeText = if (securityManager.isPinEnabled()) "Вкл" else "Выкл",
                badgeColor = if (securityManager.isPinEnabled()) Emerald400 else Slate600,
                testTag = "settings_security_item",
                onClick = onNavigateToSecurity
            )

            SettingsItemCard(
                icon = Icons.Default.Storage,
                iconTint = Indigo500,
                title = "Бэкап и Восстановление",
                subtitle = "Экспорт/импорт и резервные копии",
                badgeText = null,
                badgeColor = Indigo500,
                testTag = "settings_backup_item",
                onClick = onNavigateToBackup
            )

            // Section 2: Notifications
            SettingsCategoryHeader(text = "УВЕДОМЛЕНИЯ")

            SettingsItemCard(
                icon = Icons.Default.Notifications,
                iconTint = Emerald400,
                title = "Ежедневные напоминания",
                subtitle = "Время и текст напоминаний",
                badgeText = null,
                badgeColor = Emerald400,
                testTag = "settings_reminder_item",
                onClick = onNavigateToReminders
            )

            // Section 3: AI Assistant
            SettingsCategoryHeader(text = "ИНТЕЛЛЕКТУАЛЬНЫЕ ФУНКЦИИ")

            SettingsItemCard(
                icon = Icons.Default.Mic,
                iconTint = Indigo500,
                title = "Голос и ИИ-Ассистент",
                subtitle = if (apiKey.isNotBlank()) "Gemini API • Голосовой ввод и промпты" else "Настройка голоса, ключей и промптов",
                badgeText = if (apiKey.isNotBlank()) "Активен" else "Не задан",
                badgeColor = if (apiKey.isNotBlank()) Indigo500 else Slate600,
                testTag = "settings_voice_ai_item",
                onClick = onNavigateToVoiceAI
            )

            // Section 3.5: Privacy
            SettingsCategoryHeader(text = "КОНФИДЕНЦИАЛЬНОСТЬ")

            SettingsItemCard(
                icon = Icons.Default.Info,
                iconTint = Slate400,
                title = "Политика конфиденциальности",
                subtitle = "Сводные данные о сборе и Gemini API",
                badgeText = null,
                badgeColor = Slate600,
                testTag = "settings_privacy_item",
                onClick = onNavigateToPrivacy
            )

            // Section 3.9: Danger Zone
            SettingsCategoryHeader(text = "ОПАСНАЯ ЗОНА")

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_reset_data_item")
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showConfirmReset = true },
                shape = RoundedCornerShape(16.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.8f))
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Rose500.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Сбросить данные к дефолтным",
                                color = Rose500,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Полное удаление всех бюджетов и данных",
                                color = Slate400,
                                fontSize = 11.sp,
                                maxLines = 1,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Section 4: App Info
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Slate800.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AI Личный Бюджет v${com.example.BuildConfig.VERSION_NAME}",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRemindersSubContent(
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(ReminderManager.isReminderEnabled(context)) }
    val (savedHour, savedMinute) = remember { ReminderManager.getReminderTime(context) }
    var selectedHour by remember { mutableStateOf(savedHour) }
    var selectedMinute by remember { mutableStateOf(savedMinute) }

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
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Emerald400.copy(alpha = 0.15f))
                    .border(1.dp, Emerald400.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Уведомления",
                    tint = Emerald400,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Ежедневные напоминания",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Настройка времени и уведомлений",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Напоминание о необходимости внести расходы и проверить бюджет.",
            color = Slate400,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBg)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .clickable {
                    val nextState = !isEnabled
                    isEnabled = nextState
                    if (nextState && !hasPermission) {
                        checkAndRequestPermission()
                    }
                }
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .settingsSharedBounds(SettingsScreen.REMINDERS)
                    .weight(1f)
            ) {
                Text("Включить уведомления", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isEnabled) {
                        if (hasPermission) "Напоминание активно" else "Требуется разрешение"
                    } else "Напоминания отключены",
                    color = if (isEnabled) (if (hasPermission) Emerald400 else Rose500) else Slate400,
                    fontSize = 11.sp
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Rose500.copy(alpha = 0.15f))
                    .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { checkAndRequestPermission() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Разрешение не предоставлено", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Нажмите, чтобы разрешить отправку уведомлений", color = Rose500, fontSize = 10.sp)
                }
            }
        }

        if (isEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            Text("Время напоминания (24ч):", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelPicker(
                    items = (0..23).map { it.toString().padStart(2, '0') },
                    initialIndex = selectedHour.coerceIn(0, 23),
                    onItemSelected = { hourIdx -> selectedHour = hourIdx },
                    modifier = Modifier.width(90.dp),
                    visibleItemsCount = 3,
                    itemHeight = 42.dp
                )

                Text(
                    text = " : ",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                WheelPicker(
                    items = (0..59).map { it.toString().padStart(2, '0') },
                    initialIndex = selectedMinute.coerceIn(0, 59),
                    onItemSelected = { minIdx -> selectedMinute = minIdx },
                    modifier = Modifier.width(90.dp),
                    visibleItemsCount = 3,
                    itemHeight = 42.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = {
                    if (!hasPermission) {
                        checkAndRequestPermission {
                            ReminderManager.showNotification(context)
                        }
                    } else {
                        ReminderManager.showNotification(context)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Тест уведомления", color = Indigo500, fontSize = 11.sp)
            }

            Button(
                onClick = {
                    if (isEnabled && !hasPermission) {
                        checkAndRequestPermission {
                            ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                            Toast.makeText(context, "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    } else {
                        ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                        Toast.makeText(context, if (isEnabled) "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}" else "Напоминания отключены", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_to_settings_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Назад", color = Slate400, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsApiKeySubContent(
    initialKey: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember { mutableStateOf(initialKey) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Indigo500.copy(alpha = 0.15f))
                    .border(1.dp, Indigo500.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "ИИ-Помощник",
                    tint = Indigo500,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gemini API Ключ",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.2f))
                            .border(1.dp, Emerald400.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Free", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(
                    text = "Интеллектуальный помощник",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBg)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Indigo500, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Как бесплатно получить API ключ:", color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "1. Перейдите на aistudio.google.com/app/apikey\n" +
                            "2. Войдите под своим Google-аккаунтом\n" +
                            "3. Нажмите «Create API key»\n" +
                            "4. Скопируйте ключ и вставьте ниже",
                    color = Slate400,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("Получить API ключ в Google AI Studio ↗", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("ВАШ КЛЮЧ API", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = apiKeyText,
            onValueChange = { apiKeyText = it },
            placeholder = { Text("AIzaSy...", color = Slate400) },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Info else Icons.Default.Lock,
                        contentDescription = "Показать/Скрыть",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = Indigo500,
                unfocusedBorderColor = Slate800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(apiKeyText)
                Toast.makeText(context, if (apiKeyText.isNotBlank()) "API ключ сохранен" else "Ключ очищен", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("Сохранить ключ", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_to_settings_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Назад", color = Slate400, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(text: String) {
    Text(
        text = text,
        color = Slate400,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsItemCard(
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Slate800.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Slate400,
                        fontSize = 11.sp,
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .border(1.dp, badgeColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Slate400.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsPrivacySubContent(
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Indigo500.copy(alpha = 0.15f))
                    .border(1.dp, Indigo500.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Конфиденциальность",
                    tint = Indigo500,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Политика конфиденциальности",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Безопасность и обработка данных",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 350.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBg)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Сбор и хранение данных:\n" +
                            "• Все финансовые записи, транзакции, категории, суммы, цели и настройки бюджета сохраняются исключительно локально в базе данных вашего устройства.\n" +
                            "• Приложение не отправляет финансовую информацию на сервера разработчиков и не имеет общего сетевого хранилища.\n\n" +
                            "Использование Gemini API (Google):\n" +
                            "• Если вы указали API-ключ Gemini, данные сумм, категорий и описаний передаются в Google Gemini API напрямую со смартфона для анализа трат и формирования отчетов.\n" +
                            "• Голосовой ввод передается в Gemini API для распознавания текста и распределения транзакций.\n" +
                            "• Запросы отправляются со смартфона прямо на сервера Google, минуя промежуточные сервера разработчиков.\n\n" +
                            "Контроль:\n" +
                            "• Вы можете удалить свои данные или отозвать согласие в любой момент.",
                    color = Slate400,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_to_settings_button_privacy")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Назад", color = Slate400, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AvatarCropDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onCropped: (android.graphics.Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val containerSizeDp = 300.dp
    val viewportSizeDp = 240.dp
    val containerSizePx = with(density) { containerSizeDp.toPx() }
    val viewportSizePx = with(density) { viewportSizeDp.toPx() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .border(1.dp, Slate800, RoundedCornerShape(16.dp)),
            color = Color(0xFF0B0F19),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ОБРЕЗКА АВАТАРА",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    androidx.compose.material3.IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(containerSizeDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, rotation ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = androidx.compose.ui.geometry.Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Crop
                    )

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val radius = viewportSizePx / 2f
                        val centerX = canvasWidth / 2f
                        val centerY = canvasHeight / 2f

                        drawContext.canvas.nativeCanvas.saveLayer(null, null)
                        
                        drawRect(color = Color.Black.copy(alpha = 0.75f))

                        drawCircle(
                            color = Color.Transparent,
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                        )

                        drawContext.canvas.nativeCanvas.restore()

                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Emerald400, Indigo500, Rose500)
                            ),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Slate400,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { scale = (scale - 0.15f).coerceAtLeast(1f) }
                    )
                    androidx.compose.material3.Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..4f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            activeTrackColor = Indigo500,
                            inactiveTrackColor = Slate800,
                            thumbColor = Emerald400
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Slate400,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { scale = (scale + 0.15f).coerceIn(1f, 4f) }
                    )
                }

                Text(
                    text = "Используйте жест сведения пальцев или слайдер для масштабирования",
                    color = Slate600,
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    ) {
                        Text("ОТМЕНА", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    androidx.compose.material3.Button(
                        onClick = {
                            val cropped = cropAvatar(
                                source = bitmap,
                                scale = scale,
                                offset = offset,
                                containerSizePx = containerSizePx,
                                viewportSizePx = viewportSizePx
                            )
                            if (cropped != null) {
                                onCropped(cropped)
                            } else {
                                onDismiss()
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Indigo500
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = 1.dp,
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Emerald400, Indigo500, Rose500)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text("ПРИМЕНИТЬ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun cropAvatar(
    source: android.graphics.Bitmap,
    scale: Float,
    offset: androidx.compose.ui.geometry.Offset,
    containerSizePx: Float,
    viewportSizePx: Float
): android.graphics.Bitmap? {
    return try {
        val resultSize = 512
        val croppedBitmap = android.graphics.Bitmap.createBitmap(resultSize, resultSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        
        val scaleX = containerSizePx / source.width.toFloat()
        val scaleY = containerSizePx / source.height.toFloat()
        val baseScale = maxOf(scaleX, scaleY)
        
        val initialWidth = source.width * baseScale
        val initialHeight = source.height * baseScale
        val initialX = (containerSizePx - initialWidth) / 2f
        val initialY = (containerSizePx - initialHeight) / 2f
        
        val viewportLeft = (containerSizePx - viewportSizePx) / 2f
        val viewportTop = (containerSizePx - viewportSizePx) / 2f
        
        val matrix = android.graphics.Matrix()
        
        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(initialX, initialY)
        
        val viewportCenterX = containerSizePx / 2f
        val viewportCenterY = containerSizePx / 2f
        matrix.postScale(scale, scale, viewportCenterX, viewportCenterY)
        
        matrix.postTranslate(offset.x, offset.y)
        matrix.postTranslate(-viewportLeft, -viewportTop)
        
        val finalScale = resultSize.toFloat() / viewportSizePx
        matrix.postScale(finalScale, finalScale)
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(source, matrix, paint)
        croppedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
