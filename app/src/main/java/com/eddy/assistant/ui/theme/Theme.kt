package com.eddy.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EddyColors = darkColorScheme(
    primary = Color(0xFF35E0C1),
    onPrimary = Color(0xFF00201A),
    secondary = Color(0xFF72D7FF),
    background = Color(0xFF071018),
    surface = Color(0xFF0D1B25),
    onBackground = Color(0xFFEAFBF7),
    onSurface = Color(0xFFEAFBF7),
)

@Composable
fun EddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EddyColors,
        content = content,
    )
}
