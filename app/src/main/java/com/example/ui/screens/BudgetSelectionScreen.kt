package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.example.ui.components.capitalizeFirstLetter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Indigo500
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.BudgetProfileEntity
import com.example.ui.components.SwipeToRevealBox
import com.example.ui.components.SwipeDirection
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BudgetSelectionScreen(
    profiles: List<BudgetProfileEntity>,
    onSelectBudget: (String) -> Unit,
    onCreateBudget: (String) -> Unit,
    onRenameBudget: (id: String, newName: String) -> Unit,
    onDeleteBudget: (id: String) -> Unit,
    onExportBudget: (id: String) -> Unit,
    onImportFromBackup: (json: String) -> Unit,
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

    // Delayed deletion handler
    LaunchedEffect(pendingDeleteId) {
        val idToDelete = pendingDeleteId
        if (idToDelete != null) {
            delay(3500L) // 3.5s window to allow user to tap "Вернуть"
            if (pendingDeleteId == idToDelete) {
                animatingOutId = idToDelete
                delay(300L) // smooth collapse & fadeOut
                onDeleteBudget(idToDelete)
                animatingOutId = null
                pendingDeleteId = null
            }
        }
    }

    // Launchers for Importing (Opening JSON file)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!json.isNullOrBlank()) {
                    onImportFromBackup(json)
                } else {
                    Toast.makeText(context, "Выбранный файл пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!json.isNullOrBlank()) {
                    onImportFromBackup(json)
                } else {
                    Toast.makeText(context, "Выбранный файл пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                if (isCreatingNew) {
                    isCreatingNew = false
                    newBudgetNameText = ""
                }
                editingProfileId = null
                confirmDeleteId = null
                com.example.ui.components.SwipeToRevealController.requestCollapseAll()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Title & Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Профили бюджетов",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Выберите профиль или создайте новый",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenSecurityModal != null) {
                        Surface(
                            onClick = { onOpenSecurityModal() },
                            shape = RoundedCornerShape(12.dp),
                            color = Slate900,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("open_security_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Защита",
                                    tint = Emerald400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Защита",
                                    color = Emerald400,
                                    fontSize = 12.sp,
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
                        shape = RoundedCornerShape(12.dp),
                        color = Slate900,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("import_copy_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Из копии",
                                tint = Indigo500,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Из копии",
                                color = Indigo500,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (profiles.isEmpty() && !isCreatingNew) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("У вас пока нет сохраненных профилей", color = Slate400, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Нажмите + внизу справа, чтобы создать профиль", color = Slate400.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isCreatingNew) {
                        item(key = "inline_create_budget") {
                            AnimatedVisibility(
                                visible = isCreatingNew,
                                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                                exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                            ) {
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

                    items(profiles, key = { it.id }) { profile ->
                        val isConfirmingDelete = confirmDeleteId == profile.id
                        val isPendingDelete = pendingDeleteId == profile.id
                        val isEditingName = editingProfileId == profile.id
                        val isAnimatingOut = animatingOutId == profile.id

                        AnimatedVisibility(
                            visible = !isAnimatingOut,
                            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                            exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
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

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // FAB to add budget
        FloatingActionButton(
            onClick = {
                isCreatingNew = true
                newBudgetNameText = ""
            },
            containerColor = Emerald400,
            contentColor = DarkBg,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("create_budget_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать профиль")
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

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("inline_create_budget_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Emerald400)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Emerald400.copy(alpha = 0.2f))
                    .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(22.dp)
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.capitalizeFirstLetter()) },
                placeholder = { Text("Название профиля", color = Slate400, fontSize = 14.sp) },
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
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onCreate() }
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onCreate,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Сохранить",
                        tint = Emerald400
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Отмена",
                        tint = Slate400
                    )
                }
            }
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
                .clip(RoundedCornerShape(20.dp))
                .testTag("budget_rename_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Emerald400)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo500.copy(alpha = 0.2f))
                        .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Indigo500,
                        modifier = Modifier.size(22.dp)
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onSaveRename() }
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onSaveRename,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Сохранить",
                            tint = Emerald400
                        )
                    }
                    IconButton(
                        onClick = onCancelRename,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Slate400
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
            else -> Slate900
        },
        animationSpec = tween(durationMillis = 350),
        label = "containerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isPendingDelete || isConfirmingDelete -> Rose500
            else -> Slate800
        },
        animationSpec = tween(durationMillis = 350),
        label = "borderColor"
    )
    val iconBgColor by animateColorAsState(
        targetValue = when {
            isPendingDelete || isConfirmingDelete -> Color.White.copy(alpha = 0.25f)
            else -> Indigo500.copy(alpha = 0.2f)
        },
        animationSpec = tween(durationMillis = 350),
        label = "iconBgColor"
    )
    val iconBorderColor by animateColorAsState(
        targetValue = when {
            isPendingDelete || isConfirmingDelete -> Color.White.copy(alpha = 0.4f)
            else -> Indigo500.copy(alpha = 0.4f)
        },
        animationSpec = tween(durationMillis = 350),
        label = "iconBorderColor"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            isPendingDelete || isConfirmingDelete -> Color.White
            else -> Indigo500
        },
        animationSpec = tween(durationMillis = 350),
        label = "iconTint"
    )

    SwipeToRevealBox(
        onExport = if (isConfirmingDelete || isPendingDelete) null else onExport,
        onEdit = if (isConfirmingDelete || isPendingDelete) null else onRename,
        onDelete = if (isConfirmingDelete || isPendingDelete) null else onRequestDelete,
        swipeDirection = SwipeDirection.Both,
        resetSwipe = isConfirmingDelete || isPendingDelete,
        shape = RoundedCornerShape(20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .drawBehind {
                    if (isPendingDelete) {
                        val progress = deleteProgress.value.coerceIn(0f, 1f)
                        val redEndFraction = (1f - progress).coerceIn(0f, 1f)
                        if (redEndFraction >= 0.99f) {
                            drawRect(Rose500.copy(alpha = 0.85f))
                        } else if (redEndFraction <= 0.01f) {
                            drawRect(Slate900)
                        } else {
                            val brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Rose500.copy(alpha = 0.85f),
                                    redEndFraction to Rose500.copy(alpha = 0.85f),
                                    (redEndFraction + 0.03f).coerceAtMost(1f) to Slate900,
                                    1f to Slate900
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
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPendingDelete) Color.Transparent else containerColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
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
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconBgColor)
                            .border(1.dp, iconBorderColor, RoundedCornerShape(14.dp))
                            .then(
                                if (isConfirmingDelete) {
                                    Modifier.clickable { onClick() }
                                } else {
                                    Modifier
                                }
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
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                            },
                            label = "iconContent"
                        ) { state ->
                            val icon = when (state) {
                                "pending", "confirming" -> Icons.Default.Delete
                                else -> Icons.Default.Person
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = if (state == "confirming") "Удалить" else null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
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
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "textContent",
                        modifier = Modifier.weight(1f)
                    ) { state ->
                        if (state == "confirming") {
                            Text(
                                text = "⚠️ Нажмите для подтверждения",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column {
                                val titleText = when (state) {
                                    "pending" -> "Удалено"
                                    else -> profile.name
                                }
                                val subtitleText = when (state) {
                                    "pending" -> "Нажмите «Вернуть» для отмены"
                                    else -> "Создан: $dateStr"
                                }

                                Text(
                                    text = titleText,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitleText,
                                    color = if (state != "normal") Color.White.copy(alpha = 0.9f) else Slate400,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isPendingDelete,
                    enter = fadeIn(tween(250)) + expandHorizontally(),
                    exit = fadeOut(tween(250)) + shrinkHorizontally()
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
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isConfirmingDelete && !isPendingDelete,
                    enter = fadeIn(tween(250)) + expandHorizontally(),
                    exit = fadeOut(tween(250)) + shrinkHorizontally()
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

