package com.eddy.assistant.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
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
    primary = Color(0xFF0A1512),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8FFF1),
    onPrimaryContainer = Color(0xFF06281F),
    secondary = Color(0xFF0CBF86),
    onSecondary = Color(0xFF031D16),
    secondaryContainer = Color(0xFFC8F8E7),
    onSecondaryContainer = Color(0xFF06392B),
    tertiary = Color(0xFF3D78C9),
    onTertiary = Color.White,
    background = Color(0xFFF5F8F7),
    surface = Color(0xFFFBFDFC),
    surfaceVariant = Color(0xFFEAF0EE),
    surfaceTint = Color(0xFF0CBF86),
    onBackground = Color(0xFF0A1512),
    onSurface = Color(0xFF0A1512),
    onSurfaceVariant = Color(0xFF53605C),
    outline = Color(0xFFC9D4D0),
    outlineVariant = Color(0xFFE0E8E5),
)

private val EddyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.35).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.7.sp,
    ),
)

private val EddyShapes = Shapes(
    extraSmall = CutCornerShape(topStart = 2.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 2.dp),
    small = CutCornerShape(topStart = 3.dp, topEnd = 9.dp, bottomStart = 9.dp, bottomEnd = 3.dp),
    medium = CutCornerShape(topStart = 4.dp, topEnd = 13.dp, bottomStart = 13.dp, bottomEnd = 4.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = CutCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
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
