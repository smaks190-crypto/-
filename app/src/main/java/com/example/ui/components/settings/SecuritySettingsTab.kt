package com.example.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.SecurityManager
import com.example.ui.components.SecuritySettingsContent

@Composable
fun SecuritySettingsTab(
    securityManager: SecurityManager,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit = {},
    onSecurityUpdated: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SecuritySettingsContent(
            securityManager = securityManager,
            onBack = onBack,
            onClose = onClose,
            onSecurityUpdated = onSecurityUpdated
        )
    }
}
