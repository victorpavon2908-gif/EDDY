package com.eddy.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EddyColors = lightColorScheme(
    primary = Color(0xFF101010),
    onPrimary = Color.White,
    secondary = Color(0xFF43DDB3),
    onSecondary = Color(0xFF101010),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF7F7F7),
    onBackground = Color(0xFF101010),
    onSurface = Color(0xFF101010),
    outline = Color(0xFFE8E8E8),
)

@Composable
fun EddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EddyColors,
        content = content,
    )
}
