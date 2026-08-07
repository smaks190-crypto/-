package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import com.example.data.SecurityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PinLockScreen(
    title: String = "Личный Бюджет AI",
    subtitle: String = "Введите ПИН-код для доступа",
    isBiometricAvailable: Boolean = false,
    onVerifyPin: (String) -> Boolean,
    onBiometricClick: () -> Unit = {},
    onResetSecurity: () -> Unit = {},
    onSuccess: () -> Unit,
    onForgotPinClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableStateOf(securityManager.getRemainingBlockTimeSeconds()) }

    val shakeOffsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            errorMessage = "Слишком много попыток. Заблокировано на $secondsRemaining сек."
            delay(1000L)
            val nextVal = securityManager.getRemainingBlockTimeSeconds()
            if (nextVal <= 0L) {
                errorMessage = null
            }
            secondsRemaining = nextVal
        } else if (securityManager.isBlocked()) {
            secondsRemaining = securityManager.getRemainingBlockTimeSeconds()
        }
    }

    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
        } catch (_: Exception) {}
    }

    fun onKeyPress(digit: String) {
        if (secondsRemaining > 0) return
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == 4) {
                if (onVerifyPin(newPin)) {
                    securityManager.resetFailedAttempts()
                    onSuccess()
                } else {
                    securityManager.handleFailedAttempt()
                    val remaining = securityManager.getRemainingBlockTimeSeconds()
                    if (remaining > 0) {
                        secondsRemaining = remaining
                    }
                    triggerVibration()
                    errorMessage = if (remaining > 0) {
                        "Слишком много попыток. Заблокировано на $remaining сек."
                    } else {
                        "Неверный ПИН-код (${securityManager.getFailedAttempts()}/5)"
                    }
                    coroutineScope.launch {
                        shakeOffsetX.animateTo(20f, spring(stiffness = 1000f))
                        shakeOffsetX.animateTo(-20f, spring(stiffness = 1000f))
                        shakeOffsetX.animateTo(10f, spring(stiffness = 1000f))
                        shakeOffsetX.animateTo(-10f, spring(stiffness = 1000f))
                        shakeOffsetX.animateTo(0f, spring(stiffness = 1000f))
                        enteredPin = ""
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (secondsRemaining > 0) return
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Slate900)
                    .border(2.dp, Emerald400.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Замок",
                    tint = Emerald400,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                color = Slate400,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN Dots Indicator with Shake Animation
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(shakeOffsetX.value.roundToInt(), 0) }
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    val isError = errorMessage != null
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> Rose500
                                    isFilled -> Emerald400
                                    else -> Slate900
                                }
                            )
                            .border(
                                1.5.dp,
                                when {
                                    isError -> Rose500
                                    isFilled -> Emerald400
                                    else -> Slate800
                                },
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error Text
            Column(
                modifier = Modifier.height(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Rose500,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val padRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in padRows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (digit in row) {
                            KeypadButton(
                                text = digit,
                                onClick = { onKeyPress(digit) },
                                modifier = Modifier.testTag("pin_key_$digit")
                            )
                        }
                    }
                }

                // Bottom row: Biometric / 0 / Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBiometricAvailable) {
                        Surface(
                            onClick = onBiometricClick,
                            shape = CircleShape,
                            color = Slate900,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("pin_key_biometric")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Отпечаток пальца",
                                    tint = Indigo500,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(68.dp))
                    }

                    KeypadButton(
                        text = "0",
                        onClick = { onKeyPress("0") },
                        modifier = Modifier.testTag("pin_key_0")
                    )

                    Surface(
                        onClick = { onBackspace() },
                        shape = CircleShape,
                        color = Slate900,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("pin_key_backspace")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Стереть",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.testTag("forgot_pin_button")
            ) {
                Text(
                    text = "Забыли ПИН-код?",
                    color = Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showResetDialog) {
        var resetInputText by remember { mutableStateOf("") }
        com.example.ui.components.SwipeToDismissDialog(
            onDismissRequest = {
                showResetDialog = false
                resetInputText = ""
            }
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Сброс защиты приложения", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "⚠️ Внимание: В целях защиты вашей конфиденциальности при сбросе ПИН-кода все данные операций, транзакций и бюджетов будут безвозвратно удалены.\n\nДля сброса ПИН-кода подтвердите личность с помощью биометрии/пароля устройства или введите слово «СБРОС».",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                        if (onForgotPinClick != null) {
                            OutlinedButton(
                                onClick = {
                                    showResetDialog = false
                                    resetInputText = ""
                                    onForgotPinClick()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = Indigo500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Подтвердить через устройство", color = Indigo500, fontSize = 13.sp)
                            }
                            Text(
                                text = "Или введите «СБРОС» для ручного сброса:",
                                color = Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Для подтверждения ручного сброса введите «СБРОС»:",
                                color = Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedTextField(
                            value = resetInputText,
                            onValueChange = { resetInputText = it },
                            placeholder = { Text("СБРОС", color = Slate600) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Rose500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showResetDialog = false
                            resetInputText = ""
                        }) {
                            Text("Отмена", color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showResetDialog = false
                                resetInputText = ""
                                onResetSecurity()
                            },
                            enabled = resetInputText.trim().equals("СБРОС", ignoreCase = true),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Rose500,
                                disabledContainerColor = Slate800
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Сбросить защиту", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Slate900,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
        modifier = modifier.size(68.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
