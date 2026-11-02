package com.example.davidapp.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Визуализатор David Live, обновлённый до 3D Mesh-сферы Obsidian Neo-Bank (Issue #56).
 *
 * Сохраняет полную обратную совместимость со всеми существующими вызовами и UI-тестами
 * (включая семантические теги `david_live_circular_visualizer` и `david_live_mic_button`),
 * делегируя отрисовку новому компоненту [DavidLiveMeshSphereVisualizer].
 */
@Composable
fun DavidLiveCircularVisualizer(
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    onToggleMicMute: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 308.dp,
    visualState: DavidLiveVisualState? = null,
    failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE,
    activeToolName: String? = null,
    onSphereClick: (() -> Unit)? = null,
    onRetryConnection: (() -> Unit)? = null,
    onOpenApiKeySettings: (() -> Unit)? = null
) {
    val resolvedVisualState = visualState ?: DavidLiveVisualStateResolver.resolve(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        overallEnergy = overallEnergy,
        isMicMuted = isMicMuted,
        activeToolName = activeToolName,
        failureKind = failureKind
    )

    val visualMeta = DavidLiveVisualStateResolver.resolveMetadata(
        visualState = resolvedVisualState,
        isMicMuted = isMicMuted,
        activeToolName = activeToolName,
        failureKind = failureKind
    )

    val sphereClickHandler: () -> Unit = onSphereClick ?: {
        when {
            visualMeta.sphereTapTriggersReconnect && onRetryConnection != null -> onRetryConnection()
            visualMeta.sphereTapOpensApiSettings && onOpenApiKeySettings != null -> onOpenApiKeySettings()
            else -> onToggleMicMute()
        }
    }

    DavidLiveMeshSphereVisualizer(
        visualState = resolvedVisualState,
        overallEnergy = overallEnergy,
        rayMagnitudes = rayMagnitudes,
        isMicMuted = isMicMuted,
        onSphereClick = sphereClickHandler,
        modifier = modifier,
        visualizerSize = visualizerSize,
        activeToolName = activeToolName
    )
}
