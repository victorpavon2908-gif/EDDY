package com.eddy.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EddyColors = lightColorScheme(
    primary = Color(0xFF0E1714),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDFBF1),
    onPrimaryContainer = Color(0xFF092D22),
    secondary = Color(0xFF19B985),
    onSecondary = Color(0xFF052019),
    secondaryContainer = Color(0xFFC8F8E7),
    onSecondaryContainer = Color(0xFF073D2D),
    tertiary = Color(0xFF4E7FCA),
    onTertiary = Color.White,
    background = Color(0xFFF3F7F5),
    surface = Color(0xFFFCFEFD),
    surfaceVariant = Color(0xFFE9F0ED),
    surfaceTint = Color(0xFF19B985),
    onBackground = Color(0xFF0E1714),
    onSurface = Color(0xFF0E1714),
    onSurfaceVariant = Color(0xFF52615B),
    outline = Color(0xFFD7E2DE),
    outlineVariant = Color(0xFFE7EFEC),
)

private val EddyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val EddyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun EddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EddyColors,
        typography = EddyTypography,
        shapes = EddyShapes,
        content = content,
    )
}
