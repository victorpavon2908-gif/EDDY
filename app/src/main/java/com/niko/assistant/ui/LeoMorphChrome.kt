package com.niko.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun leoModeAccent(mode: NikoUiMode): Color = when (mode) {
    NikoUiMode.ASSISTANT -> Color(0xFF55E8FF)
    NikoUiMode.CAMERA -> Color(0xFF45F0B5)
    NikoUiMode.VIDEO -> Color(0xFFFF5D7A)
    NikoUiMode.AUDIO_RECORDER -> Color(0xFFFFB45C)
    NikoUiMode.MUSIC -> Color(0xFFB77CFF)
    NikoUiMode.CALCULATOR -> Color(0xFF65A9FF)
    NikoUiMode.STOPWATCH -> Color(0xFF54E6E6)
    NikoUiMode.TIMER -> Color(0xFFFFD75A)
    NikoUiMode.CLOCK -> Color(0xFF7E9CFF)
    NikoUiMode.NOTES -> Color(0xFF73E58F)
    NikoUiMode.CONVERTER -> Color(0xFFFF8D62)
    NikoUiMode.VOICE_DIAGNOSTICS -> Color(0xFF77F2C3)
    NikoUiMode.TOOLBOX -> Color(0xFF8C7CFF)
    NikoUiMode.GENERATED -> Color(0xFF4C9F8E)
}

internal fun leoModeDescription(mode: NikoUiMode): String = when (mode) {
    NikoUiMode.ASSISTANT -> "ASISTENTE"
    NikoUiMode.CAMERA -> "VISIÓN"
    NikoUiMode.VIDEO -> "CAPTURA DE VIDEO"
    NikoUiMode.AUDIO_RECORDER -> "CAPTURA DE AUDIO"
    NikoUiMode.MUSIC -> "REPRODUCTOR"
    NikoUiMode.CALCULATOR -> "CÁLCULO"
    NikoUiMode.STOPWATCH -> "MEDICIÓN DE TIEMPO"
    NikoUiMode.TIMER -> "CUENTA REGRESIVA"
    NikoUiMode.CLOCK -> "TIEMPO LOCAL"
    NikoUiMode.NOTES -> "MEMORIA RÁPIDA"
    NikoUiMode.CONVERTER -> "CONVERSIÓN"
    NikoUiMode.VOICE_DIAGNOSTICS -> "DIAGNÓSTICO"
    NikoUiMode.TOOLBOX -> "POLIMORFISMO"
    NikoUiMode.GENERATED -> "FORMA GENERADA"
}

@Composable
internal fun LeoToolSurface(
    mode: NikoUiMode,
    title: String = mode.title,
    onHome: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = leoModeAccent(mode)
    val pulseTransition = rememberInfiniteTransition(label = "leoToolPulse")
    val dotPulse by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "leoToolDotPulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFBFCFB),
                        Color(0xFFF4F8F7),
                        Color(0xFFEFF4F2),
                        Color(0xFFF8FAF9),
                    ),
                ),
            ),
    ) {
        LeoToolBackdrop(accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LEO  /  ${leoModeDescription(mode)}",
                        color = accent.copy(alpha = 0.90f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = title,
                        color = Color(0xFF19231F),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onHome),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.98f),
                    border = BorderStroke(1.2.dp, accent.copy(alpha = 0.30f)),
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "L",
                            color = accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(12.dp)) {
                        drawCircle(accent.copy(alpha = 0.22f * dotPulse), radius = size.minDimension * 0.50f)
                        drawCircle(accent.copy(alpha = 0.70f + 0.30f * dotPulse), radius = size.minDimension * 0.28f)
                    }
                    Spacer(Modifier.size(9.dp))
                    Text(
                        text = "MODO ${mode.title.uppercase()} ACTIVO",
                        color = Color(0xFF53635E),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    )
                }
            }

            content()
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun LeoToolBackdrop(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.19f), Color.Transparent),
                center = Offset(size.width * 0.84f, size.height * 0.12f),
                radius = size.width * 0.78f,
            ),
            radius = size.width * 0.78f,
            center = Offset(size.width * 0.84f, size.height * 0.12f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9EB7E8).copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.86f),
                radius = size.width * 0.70f,
            ),
            radius = size.width * 0.70f,
            center = Offset(size.width * 0.08f, size.height * 0.86f),
        )
        val gap = 34.dp.toPx()
        val line = Color(0xFF6F7D77).copy(alpha = 0.045f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += gap
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += gap
        }
    }
}

@Composable
internal fun LeoTransformCard(
    mode: NikoUiMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = leoModeAccent(mode)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "leoCardPressScale",
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (pressed) 0.55f else 0.26f)),
        shadowElevation = if (pressed) 1.dp else 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.06f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = accent.copy(alpha = 0.28f),
                        radius = size.minDimension * 0.46f,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                Text(
                    text = mode.title.take(1).uppercase(),
                    color = accent,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
            Text(
                text = mode.title,
                color = Color(0xFF1D2723),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = leoModeDescription(mode),
                color = Color(0xFF6B7974),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun LeoMorphTransitionOverlay(mode: NikoUiMode, visible: Boolean) {
    val accent = leoModeAccent(mode)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(110)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.06f, animationSpec = tween(220)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFCFFFFFF),
                            Color(0xF8F4F8F7),
                            Color(0xFAECF2F0),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val infinite = rememberInfiniteTransition(label = "leoMorphRing")
            val rotation by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(820, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "leoMorphRotation",
            )
            val counterRotation by infinite.animateFloat(
                initialValue = 360f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1280, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "leoMorphCounterRotation",
            )
            val corePulse by infinite.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "leoMorphPulse",
            )
            val sweepProgress by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "leoMorphSweep",
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(144.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Pulsing ambient halo
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = corePulse, scaleY = corePulse),
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                            ),
                            radius = size.minDimension * 0.50f,
                        )
                    }
                    // Outer primary energy arc
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(rotationZ = rotation),
                    ) {
                        drawArc(
                            color = accent,
                            startAngle = 12f,
                            sweepAngle = 116f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                            size = Size(size.width * 0.84f, size.height * 0.84f),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = accent.copy(alpha = 0.32f),
                            startAngle = 192f,
                            sweepAngle = 56f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                            size = Size(size.width * 0.84f, size.height * 0.84f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    // Inner counter-rotating orbital ring
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(rotationZ = counterRotation),
                    ) {
                        drawArc(
                            color = Color(0xFF475852).copy(alpha = 0.26f),
                            startAngle = 40f,
                            sweepAngle = 82f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.17f, size.height * 0.17f),
                            size = Size(size.width * 0.66f, size.height * 0.66f),
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = accent.copy(alpha = 0.55f),
                            startAngle = 220f,
                            sweepAngle = 64f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.17f, size.height * 0.17f),
                            size = Size(size.width * 0.66f, size.height * 0.66f),
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    // Core emblem medallion
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer(scaleX = corePulse, scaleY = corePulse),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.96f),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.35f)),
                        shadowElevation = 6.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "L",
                                color = Color(0xFF1A2420),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(20.dp))
                Text(
                    text = "LEO  //  TRANSFORMANDO",
                    color = accent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.0.sp,
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = mode.title.uppercase(),
                    color = Color(0xFF18221E),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = leoModeDescription(mode),
                    color = Color(0xFF687872),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.size(16.dp))
                // Energy sweep bar
                Canvas(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(CircleShape),
                ) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.16f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                    val barW = size.width * 0.42f
                    val startX = (size.width - barW) * sweepProgress
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(startX, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                }
            }
        }
    }
}
