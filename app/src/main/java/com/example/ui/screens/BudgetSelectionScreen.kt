package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BudgetProfileEntity
import com.example.ui.components.SwipeDirection
import com.example.ui.components.SwipeToRevealBox
import com.example.ui.components.SwipeToRevealController
import com.example.ui.components.capitalizeFirstLetter
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SpaceGroteskFontFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BudgetSelectionScreen(
    profiles: List<BudgetProfileEntity>,
    onSelectBudget: (String) -> Unit,
    onCreateBudget: (String) -> Unit,
    onRenameBudget: (id: String, newName: String) -> Unit,
    onDeleteBudget: (id: String) -> Unit,
    onExportBudget: (id: String) -> Unit,
    onImportFromBackup: (json: String, nameHint: String?) -> Unit,
    onOpenApiKeyModal: (() -> Unit)? = null,
    onOpenCategoriesModal: (() -> Unit)? = null,
    onOpenReminderModal: (() -> Unit)? = null,
    onOpenSecurityModal: (() -> Unit)? = null,
    onOpenSettingsModal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCreatingNew by remember { mutableStateOf(false) }
    var newBudgetNameText by remember { mutableStateOf("") }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var editingNameText by remember { mutableStateOf("") }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var animatingOutId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isCreatingNew || editingProfileId != null) {
        if (isCreatingNew) {
            isCreatingNew = false
            newBudgetNameText = ""
        }
        if (editingProfileId != null) {
            editingProfileId = null
        }
    }

    // Delayed deletion handler (3.5s undo window)
    LaunchedEffect(pendingDeleteId) {
        val idToDelete = pendingDeleteId
        if (idToDelete != null) {
            delay(3500L)
            if (pendingDeleteId == idToDelete) {
                animatingOutId = idToDelete
                delay(300L)
                onDeleteBudget(idToDelete)
                animatingOutId = null
                pendingDeleteId = null
            }
        }
    }

    fun extractNameHint(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            fileName = cursor.getString(idx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        if (fileName != null) {
            val baseName = fileName!!.substringBeforeLast(".json").substringBeforeLast(".txt")
            val cleanName = baseName
                .replace(Regex("_\\d{8}_\\d{4}$"), "")
                .replace(Regex("_\\d{8}$"), "")
                .replace(Regex("_\\d{4}-\\d{2}-\\d{2}.*$"), "")
                .replace("_", " ")
                .trim()
            if (cleanName.isNotBlank() && cleanName.lowercase() != "budget" && cleanName.lowercase() != "backup") {
                return cleanName
            }
        }
        return null
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!json.isNullOrBlank()) {
                    val nameHint = extractNameHint(uri)
                    onImportFromBackup(json, nameHint)
                } else {
                    Toast.makeText(context, "Выбранный файл пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .drawBehind {
                // Деликатное неоновое свечение в верхней части экрана в стиле Obsidian Neo-Bank
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Emerald400.copy(alpha = 0.08f),
                            Indigo500.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, 120f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.2f, 120f),
                    radius = size.width * 0.85f
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                if (isCreatingNew) {
                    isCreatingNew = false
                    newBudgetNameText = ""
                }
                editingProfileId = null
                confirmDeleteId = null
                SwipeToRevealController.requestCollapseAll()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // --- OBSIDIAN NEO-BANK TOP HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianSurface)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(Emerald400.copy(alpha = 0.7f), Indigo500.copy(alpha = 0.4f))
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐸", fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "David Finance",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Emerald400.copy(alpha = 0.15f))
                                    .border(0.5.dp, Emerald400.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "AI",
                                    color = Emerald400,
                                    fontSize = 10.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Выберите пространство бюджета",
                            color = Slate400,
                            fontSize = 11.5.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Кнопки быстрых действий в шапке (Защита и Импорт из копии)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenSecurityModal != null) {
                        Surface(
                            onClick = { onOpenSecurityModal() },
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("open_security_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Защита",
                                    tint = Emerald400,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Защита",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            try {
                                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Не удалось открыть проводник: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("import_copy_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Из копии",
                                tint = Indigo400,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Импорт",
                                color = Indigo400,
                                fontSize = 11.5.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Информационная плашка-подсказка со счетчиком профилей
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ВАШИ БЮДЖЕТЫ (${profiles.size})",
                    color = Slate400,
                    fontSize = 10.5.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold
                )
                if (profiles.isNotEmpty()) {
                    Text(
                        text = "← свайп для бэкапа и правки →",
                        color = Slate400.copy(alpha = 0.65f),
                        fontSize = 10.5.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty() && !isCreatingNew) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                            .padding(horizontal = 24.dp, vertical = 36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Emerald400.copy(alpha = 0.12f))
                                .border(1.dp, Emerald400.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🐸", fontSize = 30.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Нет активных бюджетов",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Создайте личный, семейный или проектный бюджет внизу экрана либо восстановите данные из резервной копии.",
                            color = Slate400,
                            fontSize = 12.5.sp,
                            fontFamily = ManropeFontFamily,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isConfirmingDelete = confirmDeleteId == profile.id
                        val isPendingDelete = pendingDeleteId == profile.id
                        val isEditingName = editingProfileId == profile.id
                        val isAnimatingOut = animatingOutId == profile.id

                        AnimatedVisibility(
                            visible = !isAnimatingOut,
                            enter = fadeIn(tween(260)) + expandVertically(tween(260)),
                            exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                        ) {
                            BudgetProfileCard(
                                profile = profile,
                                isConfirmingDelete = isConfirmingDelete,
                                isPendingDelete = isPendingDelete,
                                isEditingName = isEditingName,
                                editingNameValue = if (isEditingName) editingNameText else "",
                                onEditingNameChange = { editingNameText = it },
                                onSaveRename = {
                                    if (editingNameText.isNotBlank()) {
                                        onRenameBudget(profile.id, editingNameText.trim())
                                    }
                                    editingProfileId = null
                                },
                                onCancelRename = {
                                    editingProfileId = null
                                },
                                onClick = {
                                    if (isEditingName) return@BudgetProfileCard
                                    if (isPendingDelete) {
                                        val idToDelete = profile.id
                                        animatingOutId = idToDelete
                                        coroutineScope.launch {
                                            delay(250)
                                            onDeleteBudget(idToDelete)
                                            animatingOutId = null
                                            pendingDeleteId = null
                                        }
                                    } else if (isConfirmingDelete) {
                                        confirmDeleteId = null
                                        pendingDeleteId = profile.id
                                    } else {
                                        confirmDeleteId = null
                                        onSelectBudget(profile.id)
                                    }
                                },
                                onRequestDelete = {
                                    editingProfileId = null
                                    pendingDeleteId = profile.id
                                    confirmDeleteId = null
                                },
                                onCancelDelete = {
                                    confirmDeleteId = null
                                },
                                onUndoDelete = {
                                    pendingDeleteId = null
                                },
                                onRename = {
                                    confirmDeleteId = null
                                    pendingDeleteId = null
                                    editingProfileId = profile.id
                                    editingNameText = profile.name
                                },
                                onExport = {
                                    confirmDeleteId = null
                                    pendingDeleteId = null
                                    editingProfileId = null
                                    onExportBudget(profile.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- НИЖНЯЯ МОРФИНГ-КАПСУЛА СОЗДАНИЯ НОВОГО БЮДЖЕТА ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(if (isCreatingNew) 26.dp else 30.dp),
                        ambientColor = Emerald400,
                        spotColor = Emerald400
                    )
                    .clip(RoundedCornerShape(if (isCreatingNew) 26.dp else 30.dp))
                    .background(
                        if (isCreatingNew) Slate900.copy(alpha = 0.98f)
                        else ObsidianSurface.copy(alpha = 0.96f)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            if (isCreatingNew) {
                                listOf(Emerald400.copy(alpha = 0.65f), Indigo500.copy(alpha = 0.45f))
                            } else {
                                listOf(Emerald400.copy(alpha = 0.45f), GlassHighlight)
                            }
                        ),
                        shape = RoundedCornerShape(if (isCreatingNew) 26.dp else 30.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                AnimatedContent(
                    targetState = isCreatingNew,
                    transitionSpec = {
                        (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ ->
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    }
                                )
                            )
                    },
                    label = "create_budget_morph"
                ) { creating ->
                    if (!creating) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clickable {
                                    isCreatingNew = true
                                    newBudgetNameText = ""
                                }
                                .padding(horizontal = 16.dp)
                                .testTag("create_budget_fab"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Emerald400.copy(alpha = 0.16f))
                                        .border(1.dp, Emerald400.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Создать новый бюджет",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Личный, семейный или целевой профиль",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .width(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(NeonEmerald, Emerald400))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Создать профиль",
                                    tint = DarkBg,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        InlineCreateBudgetCard(
                            value = newBudgetNameText,
                            onValueChange = { newBudgetNameText = it },
                            onCreate = {
                                if (newBudgetNameText.isNotBlank()) {
                                    onCreateBudget(newBudgetNameText.trim())
                                    isCreatingNew = false
                                    newBudgetNameText = ""
                                }
                            },
                            onCancel = {
                                isCreatingNew = false
                                newBudgetNameText = ""
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InlineCreateBudgetCard(
    value: String,
    onValueChange: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val presets = listOf("Личный бюджет", "Семейный", "Бизнес / ИП", "Накопления", "Отпуск")

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("inline_create_budget_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Новый профиль бюджета",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Slate800)
                    .border(1.dp, Slate700, CircleShape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Отмена",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Быстрые шаблоны названий профилей в 1 тап
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = value.equals(preset, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) Emerald400.copy(alpha = 0.2f)
                            else DarkBg
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Emerald400 else Slate800,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onValueChange(preset) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset,
                        color = if (isSelected) Emerald400 else Slate400,
                        fontSize = 11.5.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.capitalizeFirstLetter()) },
            placeholder = {
                Text(
                    text = "Например: Личный бюджет",
                    color = Slate400,
                    fontSize = 13.5.sp,
                    fontFamily = ManropeFontFamily
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = Emerald400,
                unfocusedBorderColor = Slate800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onCreate() }
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onCreate,
            enabled = value.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald400,
                contentColor = DarkBg,
                disabledContainerColor = Slate800,
                disabledContentColor = Slate400
            ),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Сохранить",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Создать и войти",
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun BudgetProfileCard(
    profile: BudgetProfileEntity,
    isConfirmingDelete: Boolean,
    isPendingDelete: Boolean,
    isEditingName: Boolean = false,
    editingNameValue: String = "",
    onEditingNameChange: (String) -> Unit = {},
    onSaveRename: () -> Unit = {},
    onCancelRename: () -> Unit = {},
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current

    val avatarBitmap = remember(profile.id) {
        val file = File(context.filesDir, "avatar_${profile.id}.jpg")
        if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val initials = remember(profile.name) {
        val clean = profile.name.trim().uppercase()
        if (clean.isEmpty()) "Б"
        else {
            val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}"
            else "${parts[0].first()}"
        }
    }

    if (isEditingName) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        var textFieldValue by remember {
            mutableStateOf(
                TextFieldValue(
                    text = editingNameValue,
                    selection = TextRange(editingNameValue.length)
                )
            )
        }

        LaunchedEffect(Unit) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .testTag("budget_rename_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.5.dp, Emerald400)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Emerald400.copy(alpha = 0.16f))
                        .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Emerald400,
                        fontSize = 15.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val capValue = newValue.capitalizeFirstLetter()
                        textFieldValue = capValue
                        onEditingNameChange(capValue.text)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSaveRename() }
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onSaveRename,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Сохранить",
                            tint = Emerald400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancelRename,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        return
    }

    val dateStr = remember(profile.createdAt) {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(profile.createdAt))
    }

    val deleteProgress = remember { Animatable(0f) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            deleteProgress.snapTo(0f)
            deleteProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3500, easing = LinearEasing)
            )
        } else {
            deleteProgress.snapTo(0f)
        }
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            isPendingDelete -> Rose500.copy(alpha = 0.85f)
            isConfirmingDelete -> Rose500
            else -> ObsidianSurface
        },
        animationSpec = tween(durationMillis = 300),
        label = "containerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isPendingDelete || isConfirmingDelete -> Rose500
            else -> GlassHighlight
        },
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )

    SwipeToRevealBox(
        onExport = if (isConfirmingDelete || isPendingDelete) null else onExport,
        onEdit = if (isConfirmingDelete || isPendingDelete) null else onRename,
        onDelete = if (isConfirmingDelete || isPendingDelete) null else onRequestDelete,
        swipeDirection = SwipeDirection.Both,
        resetSwipe = isConfirmingDelete || isPendingDelete,
        shape = RoundedCornerShape(24.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .drawBehind {
                    if (isPendingDelete) {
                        val progress = deleteProgress.value.coerceIn(0f, 1f)
                        val redEndFraction = (1f - progress).coerceIn(0f, 1f)
                        if (redEndFraction >= 0.99f) {
                            drawRect(Rose500.copy(alpha = 0.85f))
                        } else if (redEndFraction <= 0.01f) {
                            drawRect(ObsidianSurface)
                        } else {
                            val brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Rose500.copy(alpha = 0.85f),
                                    redEndFraction to Rose500.copy(alpha = 0.85f),
                                    (redEndFraction + 0.03f).coerceAtMost(1f) to ObsidianSurface,
                                    1f to ObsidianSurface
                                )
                            )
                            drawRect(brush = brush)
                        }
                    }
                }
                .clickable(onClick = onClick)
                .testTag(
                    when {
                        isPendingDelete -> "budget_delete_pending_card"
                        isConfirmingDelete -> "budget_delete_confirm_card"
                        else -> "budget_profile_card"
                    }
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPendingDelete) Color.Transparent else containerColor
            ),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Аватар профиля (кастомное фото или стильная неоновая монограмма)
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isPendingDelete || isConfirmingDelete) {
                                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.15f)))
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Emerald400.copy(alpha = 0.18f),
                                            Indigo500.copy(alpha = 0.18f)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                brush = if (isPendingDelete || isConfirmingDelete) {
                                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.4f)))
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Emerald400.copy(alpha = 0.55f),
                                            Indigo500.copy(alpha = 0.4f)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = when {
                                isPendingDelete -> "pending"
                                isConfirmingDelete -> "confirming"
                                else -> "normal"
                            },
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "iconContent"
                        ) { state ->
                            if (state == "pending" || state == "confirming") {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = profile.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = initials,
                                    color = Emerald400,
                                    fontSize = 16.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    AnimatedContent(
                        targetState = when {
                            isPendingDelete -> "pending"
                            isConfirmingDelete -> "confirming"
                            else -> "normal"
                        },
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "textContent",
                        modifier = Modifier.weight(1f)
                    ) { state ->
                        if (state == "confirming") {
                            Text(
                                text = "⚠️ Нажмите для подтверждения",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column {
                                val titleText = when (state) {
                                    "pending" -> "Профиль удаляется..."
                                    else -> profile.name
                                }
                                val subtitleText = when (state) {
                                    "pending" -> "Нажмите «Вернуть» для отмены"
                                    else -> "Создан $dateStr"
                                }

                                Text(
                                    text = titleText,
                                    color = Color.White,
                                    fontSize = 16.5.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state == "normal") {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Emerald400)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = subtitleText,
                                        color = if (state != "normal") Color.White.copy(alpha = 0.9f) else Slate400,
                                        fontSize = 11.5.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Правая часть карточки: стрелка входа или кнопки отмены удаления
                if (!isPendingDelete && !isConfirmingDelete) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ObsidianElevated)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Войти в бюджет",
                            tint = Emerald400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isPendingDelete,
                    enter = fadeIn(tween(220)) + expandHorizontally(),
                    exit = fadeOut(tween(220)) + shrinkHorizontally()
                ) {
                    Button(
                        onClick = onUndoDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Вернуть",
                                tint = Rose500,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Вернуть",
                                color = Rose500,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isConfirmingDelete && !isPendingDelete,
                    enter = fadeIn(tween(220)) + expandHorizontally(),
                    exit = fadeOut(tween(220)) + shrinkHorizontally()
                ) {
                    IconButton(
                        onClick = onCancelDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
