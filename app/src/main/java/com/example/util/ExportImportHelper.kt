package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ExportImportHelper {

    fun saveOrShareExportFile(context: Context, fileName: String, jsonContent: String) {
        var savedSuccessfully = false

        // 1. Try MediaStore Downloads on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Сохранено в Загрузки: $fileName", Toast.LENGTH_LONG).show()
                    savedSuccessfully = true
                }
            } catch (e: Exception) {
                savedSuccessfully = false
            }
        } else {
            // Android 9 and lower
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val file = File(downloadsDir, fileName)
                    file.writeText(jsonContent, Charsets.UTF_8)
                    Toast.makeText(context, "Сохранено в Загрузки: ${file.name}", Toast.LENGTH_LONG).show()
                    savedSuccessfully = true
                }
            } catch (e: Exception) {
                savedSuccessfully = false
            }
        }

        // 2. If direct save to Downloads didn't happen or as additional share option, launch Share
        if (!savedSuccessfully) {
            shareJsonFile(context, fileName, jsonContent)
        }
    }

    fun shareJsonFile(context: Context, fileName: String, jsonContent: String) {
        try {
            val cacheFile = File(context.cacheDir, fileName)
            cacheFile.writeText(jsonContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Сохранить или отправить резервную копию"))
        } catch (e: Exception) {
            // Ultimate fallback: copy to clipboard
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("BudgetBackup", jsonContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Резервная копия скопирована в буфер обмена", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
