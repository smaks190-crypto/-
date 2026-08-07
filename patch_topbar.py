import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace the two IconButtons with three IconButtons
old_buttons = """                                    IconButton(
                                        onClick = {
                                            if (savedAiAudit?.auditText.isNullOrBlank() && aiAuditResult.isNullOrBlank()) {
                                                if (isGeminiConsentGiven) {
                                                    viewModel.requestAiAudit(filteredTransactions)
                                                } else {
                                                    consentDialogMessage = "Для формирования ИИ-отчета бюджета требуется ваше согласие на обработку данных."
                                                    showConsentDialog = true
                                                    return@IconButton
                                                }
                                            }
                                            showReportDialog = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "ИИ-Отчет",
                                            tint = Indigo400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            settingsInitialScreen = com.example.ui.components.SettingsScreen.HUB
                                            showSettingsHubModal = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .testTag("open_settings_button_top")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Настройки",
                                            tint = Emerald400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }"""

new_buttons = """                                    IconButton(
                                        onClick = {
                                            if (savedAiAudit?.auditText.isNullOrBlank() && aiAuditResult.isNullOrBlank()) {
                                                if (isGeminiConsentGiven) {
                                                    viewModel.requestAiAudit(filteredTransactions)
                                                } else {
                                                    consentDialogMessage = "Для формирования ИИ-отчета бюджета требуется ваше согласие на обработку данных."
                                                    showConsentDialog = true
                                                    return@IconButton
                                                }
                                            }
                                            showReportDialog = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "ИИ-Отчет",
                                            tint = Indigo400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            showAddTxModal = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
                                            contentDescription = "Добавить",
                                            tint = Emerald400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            settingsInitialScreen = com.example.ui.components.SettingsScreen.HUB
                                            showSettingsHubModal = true
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Slate800.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Slate700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .testTag("open_settings_button_top")
                                    ) {
                                        Box(contentAlignment = Alignment.TopEnd) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                                                contentDescription = "Настройки",
                                                tint = Slate400,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(Rose500, CircleShape)
                                            )
                                        }
                                    }"""
content = content.replace(old_buttons, new_buttons)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
