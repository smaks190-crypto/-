package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BudgetDarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = DarkBg,
    primaryContainer = Indigo500,
    onPrimaryContainer = Color.White,
    secondary = Indigo500,
    onSecondary = Color.White,
    tertiary = Sky400,
    background = DarkBg,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    outline = Slate700,
    error = Rose500,
    onError = Color.White
)

@Composable
fun BudgetTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BudgetDarkColorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
