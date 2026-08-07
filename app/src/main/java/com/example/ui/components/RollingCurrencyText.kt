package com.example.ui.components

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay

@Composable
fun RollingCurrencyText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily? = null,
    animateFromZeroOnStart: Boolean = false,
    animateKey: Any? = null,
    modifier: Modifier = Modifier
) {
    var currentText by remember(animateKey) {
        mutableStateOf(
            if (animateFromZeroOnStart) {
                text.map { if (it.isDigit()) '0' else it }.joinToString("")
            } else text
        )
    }

    LaunchedEffect(text, animateKey) {
        if (animateFromZeroOnStart) {
            val zeroText = text.map { if (it.isDigit()) '0' else it }.joinToString("")
            currentText = zeroText
            delay(40)
            currentText = text
        } else {
            currentText = text
        }
    }

    Row(
        modifier = modifier.clipToBounds(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        currentText.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val isIncreasing = targetState >= initialState
                        if (isIncreasing) {
                            // Wheel turns upward: new digit enters from bottom, old leaves through top
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { fullHeight -> fullHeight } togetherWith slideOutVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { fullHeight -> -fullHeight }
                        } else {
                            // Wheel turns downward: new digit enters from top, old leaves through bottom
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { fullHeight -> -fullHeight } togetherWith slideOutVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { fullHeight -> fullHeight }
                        }
                    },
                    label = "odometer_digit_$index",
                    modifier = Modifier.clipToBounds()
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        color = color,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

fun formatAmountInput(input: String): String {
    if (input.isBlank()) return ""

    val hasComma = input.contains(',')
    val hasDot = input.contains('.')
    val decimalChar = if (hasComma) ',' else if (hasDot) '.' else null

    val parts = if (decimalChar != null) {
        val split = input.split(decimalChar)
        val intDigits = split[0].filter { it.isDigit() }
        val decDigits = split.getOrNull(1)?.filter { it.isDigit() } ?: ""
        Pair(intDigits, decDigits)
    } else {
        Pair(input.filter { it.isDigit() }, null)
    }

    val intPartRaw = parts.first
    if (intPartRaw.isEmpty() && parts.second == null) return ""

    val formattedInt = if (intPartRaw.isNotEmpty()) {
        val num = intPartRaw.toLongOrNull()
        if (num != null) {
            String.format(java.util.Locale("ru", "RU"), "%,d", num).replace('\u00A0', ' ').replace(',', ' ')
        } else {
            intPartRaw
        }
    } else {
        "0"
    }

    return if (parts.second != null) {
        "$formattedInt$decimalChar${parts.second}"
    } else {
        formattedInt
    }
}

fun parseAmountInput(input: String): Double {
    val clean = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    return clean.toDoubleOrNull() ?: 0.0
}

fun formatAmountTextFieldValue(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    if (oldText == newText) {
        return newValue
    }

    val selectionStart = newValue.selection.start
    var nonSpaceCountBeforeCursor = 0
    for (i in 0 until selectionStart.coerceAtMost(newText.length)) {
        if (newText[i] != ' ') {
            nonSpaceCountBeforeCursor++
        }
    }

    val formattedText = formatAmountInput(newText)

    var newCursorPos = 0
    var nonSpaceCount = 0
    while (newCursorPos < formattedText.length && nonSpaceCount < nonSpaceCountBeforeCursor) {
        if (formattedText[newCursorPos] != ' ') {
            nonSpaceCount++
        }
        newCursorPos++
    }

    newCursorPos = newCursorPos.coerceIn(0, formattedText.length)

    return TextFieldValue(
        text = formattedText,
        selection = TextRange(newCursorPos)
    )
}

fun String.capitalizeFirstLetter(): String =
    if (isNotEmpty() && this[0].isLowerCase()) {
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        this
    }

fun TextFieldValue.capitalizeFirstLetter(): TextFieldValue {
    if (text.isNotEmpty() && text[0].isLowerCase()) {
        val newText = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return copy(text = newText)
    }
    return this
}

