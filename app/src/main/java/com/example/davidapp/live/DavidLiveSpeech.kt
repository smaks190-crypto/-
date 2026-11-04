package com.example.davidapp.live

/** Connection and configuration messages never impersonate the assistant's speech. */
internal fun resolveDavidLiveSpeech(state: DavidLiveVisualState, subtitle: String?): String? = when (state) {
    DavidLiveVisualState.OFFLINE, DavidLiveVisualState.API_MISSING, DavidLiveVisualState.ERROR,
    DavidLiveVisualState.CONNECTING, DavidLiveVisualState.RECONNECTING -> null
    else -> subtitle?.trim()?.takeIf { it.isNotEmpty() }
}
