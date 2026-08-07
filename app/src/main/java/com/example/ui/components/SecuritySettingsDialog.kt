package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SecurityManager
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg

import androidx.compose.material.icons.automirrored.filled.ArrowBack

enum class SecurityDialogState {
    OVERVIEW,
    CREATE_PIN,
    CONFIRM_PIN,
    CHANGE_PIN_OLD,
    CHANGE_PIN_NEW,
    CHANGE_PIN_CONFIRM
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
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Защита",
                    tint = Indigo500,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Защита приложения",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ПИН-код и отпечаток пальца",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }

                Spacer(modifier = Modifier.height(20.dp))

                when (dialogState) {
                    SecurityDialogState.OVERVIEW -> {
                        // Toggle PIN
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
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
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "ПИН-код",
                                        tint = if (isPinEnabled) Emerald400 else Slate400,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Защита ПИН-кодом",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (isPinEnabled) "ПИН-код установлен" else "Требовать ПИН при входе",
                                            color = Slate400,
                                            fontSize = 12.sp
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
                                            onSecurityUpdated()
                                            Toast.makeText(context, "Защита отключена", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Emerald400,
                                        uncheckedThumbColor = Slate400,
                                        uncheckedTrackColor = Slate800
                                    ),
                                    modifier = Modifier.testTag("toggle_pin_switch")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggle Biometrics
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
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
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = "Биометрия",
                                        tint = if (isBiometricEnabled) Indigo500 else Slate400,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Отпечаток / Face ID",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (!isBiometricHardwareAvailable) "Биометрия недоступна на устройстве"
                                            else if (!isPinEnabled) "Сначала включите ПИН-код"
                                            else "Быстрый вход по биометрии",
                                            color = Slate400,
                                            fontSize = 12.sp
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

                        if (isPinEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))

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
                                    .testTag("change_pin_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Indigo500, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Сменить ПИН-код", color = Indigo500)
                            }
                        }

                        if (onBack != null) {
                            Spacer(modifier = Modifier.height(20.dp))
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

                    SecurityDialogState.CREATE_PIN, SecurityDialogState.CONFIRM_PIN -> {
                        androidx.compose.runtime.key(dialogState) {
                            PinInputStepView(
                                title = if (dialogState == SecurityDialogState.CREATE_PIN) "Придумайте ПИН-код" else "Повторите ПИН-код",
                                subtitle = if (dialogState == SecurityDialogState.CREATE_PIN) "Введите 4 цифры" else "Для подтверждения",
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

                    SecurityDialogState.CHANGE_PIN_OLD -> {
                        androidx.compose.runtime.key(dialogState) {
                            PinInputStepView(
                                title = "Введите текущий ПИН-код",
                                subtitle = "Для подтверждения личности",
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
                        androidx.compose.runtime.key(dialogState) {
                            PinInputStepView(
                                title = if (dialogState == SecurityDialogState.CHANGE_PIN_NEW) "Введите новый ПИН-код" else "Повторите новый ПИН-код",
                                subtitle = "Введите 4 цифры",
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
    errorMessage: String?,
    onPinEntered: (String) -> Unit,
    onCancel: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            color = Slate400,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) Emerald400 else DarkBg)
                        .border(1.5.dp, if (isFilled) Emerald400 else Slate800, CircleShape)
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage,
                color = Rose500,
                fontSize = 12.sp,
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
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                for (digit in row) {
                    SmallKeypadButton(text = digit) {
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
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Spacer(modifier = Modifier.size(52.dp))
            SmallKeypadButton(text = "0") {
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
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                },
                shape = CircleShape,
                color = DarkBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", color = Slate400, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancel) {
            Text("Отмена", color = Slate400)
        }
    }
}

@Composable
private fun SmallKeypadButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DarkBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
        modifier = Modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
