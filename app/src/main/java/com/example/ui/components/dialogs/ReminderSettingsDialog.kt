package com.example.ui.components

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001

@Composable
fun ReminderSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(com.example.notifications.ReminderManager.isReminderEnabled(context)) }
    val (savedHour, savedMinute) = remember { com.example.notifications.ReminderManager.getReminderTime(context) }
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

    LaunchedEffect(Unit) {
        if (isEnabled && !hasPermission) {
            checkAndRequestPermission()
        }
    }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Slate700)
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💡 Напоминания о бюджете", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ежедневное напоминание о необходимости внести расходы и проверить бюджет.",
                    color = Slate400,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Включить уведомления", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isEnabled) {
                                if (hasPermission) "Напоминание активно" else "Требуется разрешение на уведомления"
                            } else "Напоминания отключены",
                            color = if (isEnabled) (if (hasPermission) Emerald400 else Rose500) else Slate400,
                            fontSize = 11.sp
                        )
                    }

                    androidx.compose.material3.Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            if (checked && !hasPermission) {
                                checkAndRequestPermission()
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = DarkBg,
                            checkedTrackColor = Emerald400,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        )
                    )
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose500.copy(alpha = 0.15f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { checkAndRequestPermission() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Разрешение не предоставлено", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Нажмите, чтобы разрешить приложения отправку уведомлений", color = Rose500, fontSize = 11.sp)
                        }
                    }
                }

                if (isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Время напоминания (24ч):", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(
                            items = (0..23).map { it.toString().padStart(2, '0') },
                            initialIndex = selectedHour.coerceIn(0, 23),
                            onItemSelected = { hourIdx -> selectedHour = hourIdx },
                            modifier = Modifier.width(96.dp),
                            visibleItemsCount = 3,
                            itemHeight = 46.dp
                        )

                        Text(
                            text = " : ",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        WheelPicker(
                            items = (0..59).map { it.toString().padStart(2, '0') },
                            initialIndex = selectedMinute.coerceIn(0, 59),
                            onItemSelected = { minIdx -> selectedMinute = minIdx },
                            modifier = Modifier.width(96.dp),
                            visibleItemsCount = 3,
                            itemHeight = 46.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (!hasPermission) {
                                checkAndRequestPermission {
                                    com.example.notifications.ReminderManager.showNotification(context)
                                }
                            } else {
                                com.example.notifications.ReminderManager.showNotification(context)
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
                                    com.example.notifications.ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                                    Toast.makeText(context, "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                com.example.notifications.ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                                Toast.makeText(context, if (isEnabled) "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}" else "Напоминания отключены", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
