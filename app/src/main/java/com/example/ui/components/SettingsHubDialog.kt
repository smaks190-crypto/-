package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import com.example.data.SecurityManager
import com.example.ui.components.settings.BackupRestoreSettingsTab
import com.example.ui.components.settings.GeneralSettingsTab
import com.example.ui.components.settings.SecuritySettingsTab
import com.example.ui.components.settings.SettingsApiKeySubContent
import com.example.ui.components.settings.SettingsHubMainContent
import com.example.ui.components.settings.SettingsPrivacySubContent
import com.example.ui.components.settings.SettingsRemindersSubContent
import com.example.ui.components.settings.VoiceAndAISettingsTab
import com.example.ui.theme.DarkBg
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight

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
    var currentScreen by remember(initialScreen) { mutableStateOf(initialScreen) }

    androidx.activity.compose.BackHandler(enabled = currentScreen != SettingsScreen.HUB) {
        currentScreen = SettingsScreen.HUB
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 360.dp)
            .padding(horizontal = 4.dp),
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
