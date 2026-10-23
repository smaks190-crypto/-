package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SecurityManager
import com.example.notifications.ReminderManager
import com.example.ui.components.settings.BackupRestoreSettingsTab
import com.example.ui.components.settings.GeneralSettingsTab
import com.example.ui.components.settings.SecuritySettingsTab
import com.example.ui.components.settings.VoiceAndAISettingsTab
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
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import java.io.File

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<androidx.compose.animation.AnimatedVisibilityScope?> { null }

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
    ExperimentalMaterial3Api::class
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
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = DarkBg,
        dragHandle = null
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 360.dp)
                .padding(horizontal = 4.dp)
                .navigationBarsPadding()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sleek Obsidian Drag Handle
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(GlassHighlight)
                )
                androidx.compose.animation.SharedTransitionLayout {
                    CompositionLocalProvider(
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
                            CompositionLocalProvider(
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
                                            onNavigateToGeneral = { currentScreen = SettingsScreen.GENERAL },
                                            onNavigateToVoiceAI = { currentScreen = SettingsScreen.VOICE_AI },
                                            onNavigateToSecurity = { currentScreen = SettingsScreen.SECURITY },
                                            onNavigateToReminders = { currentScreen = SettingsScreen.REMINDERS },
                                            onNavigateToApiKey = { currentScreen = SettingsScreen.VOICE_AI },
                                            onNavigateToPrivacy = { currentScreen = SettingsScreen.PRIVACY },
                                            onNavigateToBackup = { currentScreen = SettingsScreen.BACKUP },
                                            onNavigateToCategories = { onOpenCategories?.invoke() },
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
                                    SettingsScreen.VOICE_AI -> {
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
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // --- TOP BAR: TITLE + SWITCH BUDGET + CLOSE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ЦЕНТР УПРАВЛЕНИЯ",
                    color = Emerald400,
                    fontSize = 10.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Настройки",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onExitBudget != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                onDismiss()
                                onExitBudget()
                            },
                        shape = RoundedCornerShape(50),
                        color = Indigo500.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Выйти в списки бюджетов",
                                tint = Indigo400,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Бюджеты",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- OBSIDIAN BENTO PROFILE CARD ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.08f),
                                Indigo500.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with Obsidian/Emerald ring
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Emerald400, Indigo500)
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
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = initials,
                                        color = Emerald400,
                                        fontSize = 16.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Overlay edit badge
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Emerald400)
                                .border(1.5.dp, DarkBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Изменить фото",
                                tint = DarkBg,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "АКТИВНЫЙ ПРОФИЛЬ",
                                color = Slate400,
                                fontSize = 10.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            if (avatarBitmap != null) {
                                Text(
                                    text = "Удалить фото",
                                    color = Rose500,
                                    fontSize = 10.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.SemiBold,
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

                        Spacer(modifier = Modifier.height(4.dp))

                        val focusManager = LocalFocusManager.current
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkBg.copy(alpha = 0.65f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
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
                                    fontSize = 15.sp,
                                    fontFamily = ManropeFontFamily,
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
                                    .weight(1f)
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
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

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

            SettingsItemCard(
                icon = Icons.Default.Mic,
                iconTint = Indigo400,
                title = "Голос и ИИ-Ассистент",
                subtitle = if (apiKey.isNotBlank()) "Gemini API • Голосовой ввод и характер" else "Настройка голоса, ключей и промптов",
                badgeText = if (apiKey.isNotBlank()) "Активен" else "Не задан",
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
private fun SettingsRemindersSubContent(
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

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
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
            com.example.notifications.ReminderTone.entries.forEach { tone ->
                val isSelected = selectedTone == tone
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedTone = tone },
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
                .clickable { smartSummaryEnabled = !smartSummaryEnabled }
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
                onCheckedChange = { smartSummaryEnabled = it },
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
                        onItemSelected = { hourIdx -> selectedHour = hourIdx },
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
                        onItemSelected = { minIdx -> selectedMinute = minIdx },
                        modifier = Modifier.width(90.dp),
                        visibleItemsCount = 3,
                        itemHeight = 40.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Text(
                    text = "Сохранить",
                    color = DarkBg,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
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
                Text("Назад", color = Slate400, fontSize = 13.sp, fontFamily = ManropeFontFamily)
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
                        .background(Indigo500.copy(alpha = 0.15f))
                        .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "ИИ-Помощник",
                        tint = Indigo400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Gemini API Ключ",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Emerald400.copy(alpha = 0.16f))
                                .border(1.dp, Emerald400.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Free",
                                color = Emerald400,
                                fontSize = 9.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Text(
                        text = "Интеллектуальный помощник",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Indigo400, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Как бесплатно получить API ключ:",
                        color = Indigo400,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "1. Перейдите на aistudio.google.com/app/apikey\n" +
                        "2. Войдите под своим Google-аккаунтом\n" +
                        "3. Нажмите «Create API key»\n" +
                        "4. Скопируйте ключ и вставьте ниже",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text(
                        text = "Получить API ключ в Google AI Studio ↗",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ВАШ КЛЮЧ API",
            color = Slate400,
            fontSize = 10.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = Indigo500,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(apiKeyText)
                Toast.makeText(
                    context,
                    if (apiKeyText.isNotBlank()) "API ключ сохранен" else "Ключ очищен",
                    Toast.LENGTH_SHORT
                ).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text(
                text = "Сохранить ключ",
                color = DarkBg,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
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
                Text("Назад", color = Slate400, fontSize = 13.sp, fontFamily = ManropeFontFamily)
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
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
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
                        .background(Indigo500.copy(alpha = 0.15f))
                        .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Конфиденциальность",
                        tint = Indigo400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Политика конфиденциальности",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Безопасность и обработка данных",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 350.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
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
                    fontFamily = ManropeFontFamily,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                Text("Назад", color = Slate400, fontSize = 13.sp, fontFamily = ManropeFontFamily)
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
    var offset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val containerSizeDp = 300.dp
    val viewportSizeDp = 240.dp
    val containerSizePx = with(density) { containerSizeDp.toPx() }
    val viewportSizePx = with(density) { viewportSizeDp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
            color = ObsidianElevated,
            shape = RoundedCornerShape(24.dp)
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGroteskFontFamily,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(containerSizeDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBg)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = Offset(
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

                    Canvas(modifier = Modifier.fillMaxSize()) {
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
                            center = Offset(centerX, centerY),
                            blendMode = BlendMode.Clear
                        )

                        drawContext.canvas.nativeCanvas.restore()

                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(Emerald400, Indigo500)
                            ),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Slate400,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { scale = (scale - 0.15f).coerceAtLeast(1f) }
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 1f..4f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Emerald400,
                            inactiveTrackColor = Slate800,
                            thumbColor = Emerald400
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
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
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "Отмена",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
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
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = "Сохранить",
                            color = DarkBg,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

private fun cropAvatar(
    source: android.graphics.Bitmap,
    scale: Float,
    offset: Offset,
    containerSizePx: Float,
    viewportSizePx: Float
): android.graphics.Bitmap? {
    return try {
        val resultSize = 512
        val croppedBitmap = android.graphics.Bitmap.createBitmap(
            resultSize,
            resultSize,
            android.graphics.Bitmap.Config.ARGB_8888
        )
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
