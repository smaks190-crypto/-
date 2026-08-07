package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun MarkdownFormattedText(
    markdownText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    MarkdownText(
        markdown = markdownText,
        color = Color.White,
        fontSize = fontSize,
        modifier = modifier.fillMaxWidth()
    )
}



