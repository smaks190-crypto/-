package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed

/** Must stay composed above the PIN screen so the picker result survives locking and recreation. */
@Composable
internal fun rememberBudgetDocumentImport(isLocked: Boolean, onImport: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // A provider may offer only a task-scoped grant; it still works on normal return.
            }
            pendingUri = uri.toString()
        }
    }
    LaunchedEffect(isLocked, pendingUri) {
        val uri = pendingUri
        if (!isLocked && uri != null) {
            owner.lifecycle.withResumed {
                onImport(Uri.parse(uri))
                pendingUri = null
            }
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
}
