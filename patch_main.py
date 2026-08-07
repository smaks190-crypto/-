import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_popup = """            if (isVoiceActive || isAnalyzingVoice) {
                androidx.compose.ui.window.Popup(
                    alignment = androidx.compose.ui.Alignment.BottomEnd,
                    properties = androidx.compose.ui.window.PopupProperties(
                        focusable = false,
                        clippingEnabled = false
                    )
                ) {
                    Box(modifier = Modifier.padding(bottom = 80.dp, end = 16.dp)) {
                        VoiceRecordingOverlay(
                            viewModel = viewModel,
                            selectedDate = selectedDateDay
                        )
                    }
                }
            }"""

new_dialog = """            if (isVoiceActive || isAnalyzingVoice) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = {},
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    val view = androidx.compose.ui.platform.LocalView.current
                    androidx.compose.runtime.LaunchedEffect(view) {
                        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                        window?.setFlags(
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        )
                        window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .androidx.compose.foundation.layout.navigationBarsPadding()
                            .androidx.compose.foundation.layout.imePadding()
                            .padding(bottom = 80.dp, end = 16.dp),
                        contentAlignment = androidx.compose.ui.Alignment.BottomEnd
                    ) {
                        VoiceRecordingOverlay(
                            viewModel = viewModel,
                            selectedDate = selectedDateDay
                        )
                    }
                }
            }"""

content = content.replace(old_popup, new_dialog)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
