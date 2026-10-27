package com.example.ui.components

import androidx.compose.runtime.Composable

/**
 * No-Op заглушки UI дебаг-консоли для Release APK (Release Source Set).
 * Гарантируют полное отсутствие отладочного интерфейса и оверлеев в релизной сборке.
 */
@Composable
fun GlobalConsoleOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    // No-op in release build
}

@Composable
fun DebugConsoleFloatingButton(
    onClick: () -> Unit
) {
    // No-op in release build
}
