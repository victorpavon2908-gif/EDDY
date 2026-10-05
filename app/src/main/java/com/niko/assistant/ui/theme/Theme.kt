package com.niko.assistant.ui.theme

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

// Pearl surfaces, deep forest accents and muted brass. High-contrast text on light surfaces.

private val md_primary          = Color(0xFF176B57)   // vivid emerald — LEO brand
private val md_onPrimary        = Color(0xFFFFFFFF)
private val md_primaryContainer = Color(0xFFD6ECE2)
private val md_onPrimaryContainer = Color(0xFF173E31)

private val md_secondary        = Color(0xFF756238)
private val md_onSecondary      = Color(0xFFFFFFFF)
private val md_secondaryContainer = Color(0xFFEFE5CE)
private val md_onSecondaryContainer = Color(0xFF493C20)

private val md_tertiary         = Color(0xFF436477)
private val md_onTertiary       = Color(0xFFFFFFFF)
private val md_tertiaryContainer = Color(0xFFDBE9F0)
private val md_onTertiaryContainer = Color(0xFF233F4F)

private val md_error            = Color(0xFFAB3047)
private val md_onError          = Color(0xFFFFFFFF)
private val md_errorContainer   = Color(0xFFFFDADF)
private val md_onErrorContainer = Color(0xFF42131E)

private val md_background       = Color(0xFFF7F6F2)
private val md_onBackground     = Color(0xFF18221F)

private val md_surface          = Color(0xFFFFFFFF)
private val md_onSurface        = Color(0xFF17201D)
private val md_surfaceVariant   = Color(0xFFEEEFE8)
private val md_onSurfaceVariant = Color(0xFF52625A)

private val md_outline          = Color(0xFFCBD6D2)
private val md_outlineVariant   = Color(0xFFE2E9E6)
private val md_surfaceTint      = md_primary

private val NikoColors = lightColorScheme(
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
    inverseSurface       = Color(0xFF1C2824),
    inverseOnSurface     = Color(0xFFF4F8F6),
    inversePrimary       = Color(0xFF63E8BE),
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
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
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.7.sp,
    ),
)

private val NikoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
