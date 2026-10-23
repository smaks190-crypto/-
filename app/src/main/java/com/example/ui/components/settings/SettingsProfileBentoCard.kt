package com.example.ui.components.settings

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarCropDialog
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.SpaceGroteskFontFamily
import java.io.File

@Composable
fun SettingsHubTopBar(
    onDismiss: () -> Unit,
    onExitBudget: (() -> Unit)? = null
) {
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
}

@Composable
fun SettingsProfileBentoCard(
    currentProfileName: String,
    profileId: String,
    onAvatarChanged: () -> Unit,
    onRenameProfile: (String) -> Unit
) {
    var nameInput by remember(currentProfileName) { mutableStateOf(currentProfileName) }

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
}
