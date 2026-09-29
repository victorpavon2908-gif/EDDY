package com.niko.assistant.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Deep-space dark palette ────────────────────────────────────────────────────
// Designed to match the existing dark canvas (0xFF03050A) in NikoReferenceScreen
// while providing proper Material 3 surface hierarchy and contrast ratios.

private val md_primary          = Color(0xFF00E5A0)   // vivid emerald — LEO brand
private val md_onPrimary        = Color(0xFF00201A)
private val md_primaryContainer = Color(0xFF003D2E)
private val md_onPrimaryContainer = Color(0xFFAAFFDE)

private val md_secondary        = Color(0xFF5BE8BD)
private val md_onSecondary      = Color(0xFF00392B)
private val md_secondaryContainer = Color(0xFF00513E)
private val md_onSecondaryContainer = Color(0xFF7DFDD9)

private val md_tertiary         = Color(0xFF5CB8FF)
private val md_onTertiary       = Color(0xFF003452)
private val md_tertiaryContainer = Color(0xFF004B75)
private val md_onTertiaryContainer = Color(0xFFCDE6FF)

private val md_error            = Color(0xFFFF8096)
private val md_onError          = Color(0xFF5F0021)
private val md_errorContainer   = Color(0xFF890036)
private val md_onErrorContainer = Color(0xFFFFD9DF)

private val md_background       = Color(0xFF03050A)   // matches NikoReferenceScreen
private val md_onBackground     = Color(0xFFE0E8E5)

private val md_surface          = Color(0xFF07110E)
private val md_onSurface        = Color(0xFFDCE9E5)
private val md_surfaceVariant   = Color(0xFF0D1E19)
private val md_onSurfaceVariant = Color(0xFF8FADA7)

private val md_outline          = Color(0xFF3A5550)
private val md_outlineVariant   = Color(0xFF1C3430)
private val md_surfaceTint      = md_primary

private val NikoColors = darkColorScheme(
    primary              = md_primary,
    onPrimary            = md_onPrimary,
    primaryContainer     = md_primaryContainer,
    onPrimaryContainer   = md_onPrimaryContainer,
    secondary            = md_secondary,
    onSecondary          = md_onSecondary,
    secondaryContainer   = md_secondaryContainer,
    onSecondaryContainer = md_onSecondaryContainer,
    tertiary             = md_tertiary,
    onTertiary           = md_onTertiary,
    tertiaryContainer    = md_tertiaryContainer,
    onTertiaryContainer  = md_onTertiaryContainer,
    error                = md_error,
    onError              = md_onError,
    errorContainer       = md_errorContainer,
    onErrorContainer     = md_onErrorContainer,
    background           = md_background,
    onBackground         = md_onBackground,
    surface              = md_surface,
    onSurface            = md_onSurface,
    surfaceVariant       = md_surfaceVariant,
    onSurfaceVariant     = md_onSurfaceVariant,
    outline              = md_outline,
    outlineVariant       = md_outlineVariant,
    surfaceTint          = md_surfaceTint,
    inverseSurface       = Color(0xFFDCE9E5),
    inverseOnSurface     = Color(0xFF0A1F1A),
    inversePrimary       = Color(0xFF006B50),
    scrim                = Color(0xFF000000),
)

private val NikoTypography = Typography(
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

private val NikoShapes = Shapes(
    extraSmall = CutCornerShape(topStart = 2.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 2.dp),
    small = CutCornerShape(topStart = 3.dp, topEnd = 9.dp, bottomStart = 9.dp, bottomEnd = 3.dp),
    medium = CutCornerShape(topStart = 4.dp, topEnd = 13.dp, bottomStart = 13.dp, bottomEnd = 4.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = CutCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
)

@Composable
fun NikoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NikoColors,
        typography = NikoTypography,
        shapes = NikoShapes,
        content = content,
    )
}
