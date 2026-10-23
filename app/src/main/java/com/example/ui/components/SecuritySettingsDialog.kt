package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SecurityManager
import com.example.ui.theme.Amber400
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

enum class SecurityDialogState {
    OVERVIEW,
    CREATE_PIN,
    CONFIRM_PIN,
    CHANGE_PIN_OLD,
    CHANGE_PIN_NEW,
    CHANGE_PIN_CONFIRM,
    CREATE_GUEST_PIN,
    CONFIRM_GUEST_PIN
}

@Composable
fun SecuritySettingsDialog(
    securityManager: SecurityManager,
    onDismiss: () -> Unit,
    onSecurityUpdated: () -> Unit
) {
    SwipeToDismissDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(GlassHighlight)
                )
                SecuritySettingsContent(
                    securityManager = securityManager,
                    onBack = null,
                    onClose = onDismiss,
                    onSecurityUpdated = onSecurityUpdated
                )
            }
        }
    }
}

@Composable
fun SecuritySettingsContent(
    securityManager: SecurityManager,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit,
    onSecurityUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    var dialogState by remember { mutableStateOf(SecurityDialogState.OVERVIEW) }

    var isPinEnabled by remember { mutableStateOf(securityManager.isPinEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(securityManager.isBiometricEnabled()) }
    var isGuestPinEnabled by remember { mutableStateOf(securityManager.isGuestPinEnabled()) }
    val isBiometricHardwareAvailable = remember { securityManager.isBiometricHardwareAvailable(context) }

    var pinInput1 by remember { mutableStateOf("") }
    var pinInput2 by remember { mutableStateOf("") }
    var oldPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
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
                if (onBack != null) {
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
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Emerald400.copy(alpha = 0.15f))
                        .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Защита",
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "БЕЗОПАСНОСТЬ И ДОСТУП",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Защита приложения",
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
        HorizontalDivider(color = GlassBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        when (dialogState) {
            SecurityDialogState.OVERVIEW -> {
                // 1. Toggle Main PIN
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPinEnabled) Emerald400.copy(alpha = 0.14f) else Slate800.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        if (isPinEnabled) Emerald400.copy(alpha = 0.3f) else GlassBorder,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "ПИН-код",
                                    tint = if (isPinEnabled) Emerald400 else Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Защита ПИН-кодом",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isPinEnabled) "ПИН-код установлен • PBKDF2" else "Требовать ПИН при входе",
                                    color = if (isPinEnabled) Emerald400 else Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                        }

                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    pinInput1 = ""
                                    pinInput2 = ""
                                    errorMessage = null
                                    dialogState = SecurityDialogState.CREATE_PIN
                                } else {
                                    securityManager.removePin()
                                    isPinEnabled = false
                                    isBiometricEnabled = false
                                    isGuestPinEnabled = false
                                    onSecurityUpdated()
                                    Toast.makeText(context, "Защита отключена", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = Emerald400,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate800
                            ),
                            modifier = Modifier.testTag("toggle_pin_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Toggle Biometrics
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBiometricEnabled) Indigo500.copy(alpha = 0.14f) else Slate800.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        if (isBiometricEnabled) Indigo500.copy(alpha = 0.3f) else GlassBorder,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Биометрия",
                                    tint = if (isBiometricEnabled) Indigo400 else Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Отпечаток / Face ID",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!isBiometricHardwareAvailable) "Биометрия недоступна на устройстве"
                                    else if (!isPinEnabled) "Сначала включите ПИН-код"
                                    else "Быстрый вход по биометрии",
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                        }

                        Switch(
                            checked = isBiometricEnabled,
                            enabled = isPinEnabled && isBiometricHardwareAvailable,
                            onCheckedChange = { checked ->
                                securityManager.setBiometricEnabled(checked)
                                isBiometricEnabled = checked
                                onSecurityUpdated()
                                Toast.makeText(
                                    context,
                                    if (checked) "Вход по отпечатку включен" else "Вход по отпечатку отключен",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Indigo500,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate800
                            ),
                            modifier = Modifier.testTag("toggle_biometric_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Guest / Decoy PIN (Анти-подглядывание от Давида Жабова)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isGuestPinEnabled) Amber400.copy(alpha = 0.4f) else GlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isGuestPinEnabled) Amber400.copy(alpha = 0.14f) else Slate800.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        if (isGuestPinEnabled) Amber400.copy(alpha = 0.35f) else GlassBorder,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Гостевой ПИН",
                                    tint = if (isGuestPinEnabled) Amber400 else Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Скрытый гостевой ПИН",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🐸 PRO",
                                        color = Amber400,
                                        fontSize = 9.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = if (!isPinEnabled) "Доступно после включения основного ПИН"
                                    else if (isGuestPinEnabled) "Защита от посторонних взглядов активна"
                                    else "Второй ПИН для входа при посторонних",
                                    color = if (isGuestPinEnabled) Amber400 else Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                        }

                        Switch(
                            checked = isGuestPinEnabled,
                            enabled = isPinEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    pinInput1 = ""
                                    pinInput2 = ""
                                    errorMessage = null
                                    dialogState = SecurityDialogState.CREATE_GUEST_PIN
                                } else {
                                    securityManager.removeGuestPin()
                                    isGuestPinEnabled = false
                                    onSecurityUpdated()
                                    Toast.makeText(context, "Гостевой ПИН-код отключен", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = Amber400,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate800
                            ),
                            modifier = Modifier.testTag("toggle_guest_pin_switch")
                        )
                    }
                }

                if (isPinEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            oldPinInput = ""
                            pinInput1 = ""
                            pinInput2 = ""
                            errorMessage = null
                            dialogState = SecurityDialogState.CHANGE_PIN_OLD
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("change_pin_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Indigo400,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сменить основной ПИН-код",
                            color = Indigo400,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onBack != null) {
                    Spacer(modifier = Modifier.height(14.dp))
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
                            Text(
                                text = "Назад",
                                color = Slate400,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily
                            )
                        }
                    }
                }
            }

            SecurityDialogState.CREATE_PIN, SecurityDialogState.CONFIRM_PIN -> {
                key(dialogState) {
                    PinInputStepView(
                        title = if (dialogState == SecurityDialogState.CREATE_PIN) "Придумайте ПИН-код" else "Повторите ПИН-код",
                        subtitle = if (dialogState == SecurityDialogState.CREATE_PIN) "Введите 4 цифры для защиты бюджета" else "Для подтверждения основного ПИН-кода",
                        accentColor = Emerald400,
                        errorMessage = errorMessage,
                        onPinEntered = { pin ->
                            if (dialogState == SecurityDialogState.CREATE_PIN) {
                                pinInput1 = pin
                                errorMessage = null
                                dialogState = SecurityDialogState.CONFIRM_PIN
                            } else {
                                pinInput2 = pin
                                if (pinInput1 == pinInput2) {
                                    securityManager.setPin(pinInput1)
                                    isPinEnabled = true
                                    onSecurityUpdated()
                                    dialogState = SecurityDialogState.OVERVIEW
                                    Toast.makeText(context, "ПИН-код успешно сохранен!", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = "ПИН-коды не совпадают. Попробуйте снова."
                                    pinInput2 = ""
                                }
                            }
                        },
                        onCancel = {
                            dialogState = SecurityDialogState.OVERVIEW
                        }
                    )
                }
            }

            SecurityDialogState.CREATE_GUEST_PIN, SecurityDialogState.CONFIRM_GUEST_PIN -> {
                key(dialogState) {
                    PinInputStepView(
                        title = if (dialogState == SecurityDialogState.CREATE_GUEST_PIN) "Придумайте гостевой ПИН" else "Повторите гостевой ПИН",
                        subtitle = if (dialogState == SecurityDialogState.CREATE_GUEST_PIN) "Должен отличаться от основного ПИН-кода" else "Подтвердите скрытый ПИН-код",
                        accentColor = Amber400,
                        errorMessage = errorMessage,
                        onPinEntered = { pin ->
                            if (dialogState == SecurityDialogState.CREATE_GUEST_PIN) {
                                if (securityManager.verifyPin(pin) && !securityManager.isGuestSessionActive()) {
                                    errorMessage = "Гостевой ПИН не должен совпадать с основным!"
                                } else {
                                    pinInput1 = pin
                                    errorMessage = null
                                    dialogState = SecurityDialogState.CONFIRM_GUEST_PIN
                                }
                            } else {
                                pinInput2 = pin
                                if (pinInput1 == pinInput2) {
                                    securityManager.setGuestPin(pinInput1)
                                    isGuestPinEnabled = true
                                    onSecurityUpdated()
                                    dialogState = SecurityDialogState.OVERVIEW
                                    Toast.makeText(context, "Скрытый гостевой ПИН активирован 🐸", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = "ПИН-коды не совпадают. Попробуйте снова."
                                    pinInput2 = ""
                                }
                            }
                        },
                        onCancel = {
                            dialogState = SecurityDialogState.OVERVIEW
                        }
                    )
                }
            }

            SecurityDialogState.CHANGE_PIN_OLD -> {
                key(dialogState) {
                    PinInputStepView(
                        title = "Введите текущий ПИН-код",
                        subtitle = "Для подтверждения личности",
                        accentColor = Indigo400,
                        errorMessage = errorMessage,
                        onPinEntered = { oldPin ->
                            if (securityManager.verifyPin(oldPin)) {
                                oldPinInput = oldPin
                                errorMessage = null
                                dialogState = SecurityDialogState.CHANGE_PIN_NEW
                            } else {
                                errorMessage = "Неверный текущий ПИН-код"
                            }
                        },
                        onCancel = {
                            dialogState = SecurityDialogState.OVERVIEW
                        }
                    )
                }
            }

            SecurityDialogState.CHANGE_PIN_NEW, SecurityDialogState.CHANGE_PIN_CONFIRM -> {
                key(dialogState) {
                    PinInputStepView(
                        title = if (dialogState == SecurityDialogState.CHANGE_PIN_NEW) "Введите новый ПИН-код" else "Повторите новый ПИН-код",
                        subtitle = "Введите 4 цифры",
                        accentColor = Emerald400,
                        errorMessage = errorMessage,
                        onPinEntered = { pin ->
                            if (dialogState == SecurityDialogState.CHANGE_PIN_NEW) {
                                pinInput1 = pin
                                errorMessage = null
                                dialogState = SecurityDialogState.CHANGE_PIN_CONFIRM
                            } else {
                                pinInput2 = pin
                                if (pinInput1 == pinInput2) {
                                    securityManager.setPin(pinInput1)
                                    onSecurityUpdated()
                                    dialogState = SecurityDialogState.OVERVIEW
                                    Toast.makeText(context, "ПИН-код успешно изменен!", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = "ПИН-коды не совпадают"
                                    pinInput2 = ""
                                }
                            }
                        },
                        onCancel = {
                            dialogState = SecurityDialogState.OVERVIEW
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinInputStepView(
    title: String,
    subtitle: String,
    accentColor: Color = Emerald400,
    errorMessage: String?,
    onPinEntered: (String) -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = Slate400,
            fontSize = 12.sp,
            fontFamily = ManropeFontFamily,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Obsidian Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) accentColor else ObsidianSurface)
                        .border(1.5.dp, if (isFilled) accentColor else GlassBorder, CircleShape)
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage,
                color = Rose500,
                fontSize = 12.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Keyboard grid inside dialog
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 5.dp)
            ) {
                for (digit in row) {
                    SmallKeypadButton(text = digit) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (enteredPin.length < 4) {
                            val newP = enteredPin + digit
                            enteredPin = newP
                            if (newP.length == 4) {
                                onPinEntered(newP)
                            }
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 5.dp)
        ) {
            Spacer(modifier = Modifier.size(56.dp))
            SmallKeypadButton(text = "0") {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (enteredPin.length < 4) {
                    val newP = enteredPin + "0"
                    enteredPin = newP
                    if (newP.length == 4) {
                        onPinEntered(newP)
                    }
                }
            }
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                },
                shape = CircleShape,
                color = ObsidianSurface,
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Стереть",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onCancel) {
            Text(
                text = "Отмена",
                color = Slate400,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmallKeypadButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ObsidianSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 19.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
