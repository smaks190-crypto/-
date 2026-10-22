package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.davidapp.AchievementManager
import org.json.JSONObject
import java.io.File

object ExportImportHelper {

    /**
     * Внедряет открытые достижения в JSON-структуру бэкапа перед экспортом
     */
    fun injectAchievements(context: Context, jsonContent: String): String {
        return try {
            val root = JSONObject(jsonContent)
            val unlockedMap = AchievementManager.exportUnlockedIds(context)
            val achievementsObj = JSONObject()
            unlockedMap.forEach { (id, time) ->
                achievementsObj.put(id, time)
            }
            root.put("achievements", achievementsObj)
            root.toString(2)
        } catch (_: Exception) {
            jsonContent
        }
    }

    /**
     * Извлекает и импортирует достижения из JSON-структуры бэкапа
     * Обеспечивает полную обратную совместимость (если поля achievements нет, ничего не ломается)
     */
    fun extractAndImportAchievements(context: Context, jsonContent: String) {
        try {
            val root = JSONObject(jsonContent)
            if (root.has("achievements")) {
                val achievementsObj = root.optJSONObject("achievements")
                if (achievementsObj != null) {
                    val map = mutableMapOf<String, Long>()
                    val keys = achievementsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val timestamp = achievementsObj.optLong(key, 0L)
                        map[key] = timestamp
                    }
                    if (map.isNotEmpty()) {
                        AchievementManager.importUnlockedIds(context, map)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun saveOrShareExportFile(context: Context, fileName: String, jsonContent: String) {
        val appContext = context.applicationContext
        val finalJson = injectAchievements(appContext, jsonContent)
        var savedSuccessfully = false

        // 1. Try MediaStore Downloads on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = appContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(finalJson.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(appContext, "Сохранено в Загрузки: $fileName", Toast.LENGTH_LONG).show()
                    savedSuccessfully = true
                    AchievementManager.unlock(appContext, "security_backup")
                }
            } catch (_: Exception) {
                savedSuccessfully = false
            }
        } else {
            // Android 9 and lower
            try {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val file = File(downloadsDir, fileName)
                    file.writeText(finalJson, Charsets.UTF_8)
                    Toast.makeText(appContext, "Сохранено в Загрузки: ${file.name}", Toast.LENGTH_LONG).show()
                    savedSuccessfully = true
                    AchievementManager.unlock(appContext, "security_backup")
                }
            } catch (_: Exception) {
                savedSuccessfully = false
            }
        }

        // 2. If direct save to Downloads didn't happen or as additional share option, launch Share
        if (!savedSuccessfully) {
            shareJsonFile(context, fileName, finalJson)
        }
    }

    fun shareJsonFile(context: Context, fileName: String, jsonContent: String) {
        val finalJson = injectAchievements(context, jsonContent)
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(exportDir, fileName)
            cacheFile.writeText(finalJson, Charsets.UTF_8)
            AchievementManager.unlock(context, "security_backup")

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(shareIntent, "Сохранить или отправить резервную копию").apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось экспортировать файл: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
