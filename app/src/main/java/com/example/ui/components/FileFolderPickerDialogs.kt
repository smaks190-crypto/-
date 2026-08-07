package com.example.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.rotate
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportFolderPickerDialog(
    defaultFileName: String,
    jsonContent: String,
    onDismiss: () -> Unit,
    onSaveSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    var fileNameInput by remember { mutableStateOf(defaultFileName) }

    val downloadsDir = remember {
        try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } catch (e: Exception) { null }
            ?: context.getExternalFilesDir(null) ?: context.filesDir
    }
    val documentsDir = remember {
        try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) } catch (e: Exception) { null }
            ?: context.getExternalFilesDir(null) ?: context.filesDir
    }
    val appFilesDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }

    var currentTargetDir by remember { mutableStateOf(downloadsDir) }
    var showFolderSelector by remember { mutableStateOf(false) }

    val displayPath = remember(currentTargetDir) {
        val path = currentTargetDir.absolutePath
        when {
            path.contains("Download", ignoreCase = true) -> "Загрузки (${currentTargetDir.path})"
            path.contains("Document", ignoreCase = true) -> "Документы (${currentTargetDir.path})"
            else -> currentTargetDir.path
        }
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Slate900,
            border = BorderStroke(1.dp, Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title & Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Indigo500.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Сохранение бюджета",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // File Name Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Имя файла",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Indigo500,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Target Folder Path Display
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Путь сохранения (нажмите, чтобы изменить)",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        onClick = { showFolderSelector = !showFolderSelector },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkBg,
                        border = BorderStroke(1.dp, if (showFolderSelector) Indigo500 else Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Indigo500)
                            Text(
                                text = displayPath,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.rotate(if (showFolderSelector) 180f else 0f)
                            )
                        }
                    }

                    // Expandable Folder Selection list
                    if (showFolderSelector) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val options = listOf(
                                "Загрузки" to downloadsDir,
                                "Документы" to documentsDir,
                                "Папка приложения" to appFilesDir
                            )

                            options.forEach { (label, dir) ->
                                val isSelected = currentTargetDir == dir
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Indigo500.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            currentTargetDir = dir
                                            showFolderSelector = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = null,
                                        tint = if (isSelected) Indigo500 else Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(dir.path, color = Slate400, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo500, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Single Action Button: Скачать / Сохранить
                Button(
                    onClick = {
                        val name = if (fileNameInput.endsWith(".json")) fileNameInput else "$fileNameInput.json"
                        var savedPath = ""
                        var success = false

                        try {
                            if (currentTargetDir == downloadsDir && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val resolver = context.contentResolver
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    resolver.openOutputStream(uri)?.use { os ->
                                        os.write(jsonContent.toByteArray(Charsets.UTF_8))
                                    }
                                    savedPath = "Загрузки / $name"
                                    success = true
                                }
                            }

                            if (!success) {
                                if (currentTargetDir.exists() || currentTargetDir.mkdirs()) {
                                    val outFile = File(currentTargetDir, name)
                                    outFile.writeText(jsonContent, Charsets.UTF_8)
                                    savedPath = outFile.absolutePath
                                    success = true
                                }
                            }

                            if (success) {
                                Toast.makeText(context, "Файл сохранен: $savedPath", Toast.LENGTH_LONG).show()
                                onSaveSuccess(savedPath)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Сохранить",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ImportFilePickerDialog(
    onFileSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val downloadsDir = remember {
        try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) } catch (e: Exception) { null }
            ?: context.getExternalFilesDir(null) ?: context.filesDir
    }
    val documentsDir = remember {
        try { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) } catch (e: Exception) { null }
            ?: context.getExternalFilesDir(null) ?: context.filesDir
    }
    val appFilesDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }

    var currentDir by remember { mutableStateOf(downloadsDir) }
    var selectedFileItem by remember { mutableStateOf<File?>(null) }
    var showFolderSelector by remember { mutableStateOf(false) }

    // List available JSON files in current directory
    val jsonFiles = remember(currentDir) {
        val list = mutableListOf<File>()
        try {
            currentDir.listFiles()?.forEach { file ->
                if (!file.isDirectory && (file.name.endsWith(".json", ignoreCase = true) || file.name.endsWith(".txt", ignoreCase = true))) {
                    list.add(file)
                }
            }
            list.sortByDescending { it.lastModified() }
        } catch (e: Exception) {
            // Ignore unreadable
        }
        list
    }

    val displayPath = remember(currentDir) {
        val path = currentDir.absolutePath
        when {
            path.contains("Download", ignoreCase = true) -> "Загрузки (${currentDir.path})"
            path.contains("Document", ignoreCase = true) -> "Документы (${currentDir.path})"
            else -> currentDir.path
        }
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Slate900,
            border = BorderStroke(1.dp, Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Indigo500.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Импорт бюджета",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Current Folder Path Display
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Путь к папке (нажмите, чтобы изменить)",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        onClick = { showFolderSelector = !showFolderSelector },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkBg,
                        border = BorderStroke(1.dp, if (showFolderSelector) Indigo500 else Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Indigo500)
                            Text(
                                text = displayPath,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.rotate(if (showFolderSelector) 180f else 0f)
                            )
                        }
                    }

                    // Expandable Folder Selection list
                    if (showFolderSelector) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val options = listOf(
                                "Загрузки" to downloadsDir,
                                "Документы" to documentsDir,
                                "Папка приложения" to appFilesDir
                            )

                            options.forEach { (label, dir) ->
                                val isSelected = currentDir == dir
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Indigo500.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            currentDir = dir
                                            selectedFileItem = null
                                            showFolderSelector = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = null,
                                        tint = if (isSelected) Indigo500 else Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(dir.path, color = Slate400, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Indigo500, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Files List Section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Доступные файлы (.json)",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (jsonFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(DarkBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📭 В этой папке нет .json файлов", color = Slate400, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Попробуйте сменить папку выше", color = Slate500, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(DarkBg, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(jsonFiles) { file ->
                                val isSelected = selectedFileItem == file
                                val sizeKb = file.length() / 1024
                                val dateStr = try {
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                } catch (e: Exception) { "" }

                                Surface(
                                    onClick = { selectedFileItem = file },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Indigo500.copy(alpha = 0.2f) else Slate900,
                                    border = BorderStroke(1.dp, if (isSelected) Indigo500 else Slate800),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$sizeKb КБ  •  $dateStr",
                                                color = Slate400,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Single Action Button: Импортировать
                Button(
                    onClick = {
                        val file = selectedFileItem
                        if (file != null) {
                            try {
                                val json = file.readText(Charsets.UTF_8)
                                if (json.isNotBlank()) {
                                    onFileSelected(json)
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Файл пуст", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ошибка чтения: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Выберите файл из списка", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = selectedFileItem != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Indigo500,
                        disabledContainerColor = Slate800
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Импортировать",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
