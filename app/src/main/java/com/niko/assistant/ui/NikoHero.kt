package com.niko.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Premium procedural NIKO mascot.
 *
 * Listening ripples use the same staggered expanding-wave idea popularized by the
 * Apache-2.0 Canopas Compose Animations sample, rewritten here for NIKO's runtime state.
 * Everything else is native Canvas so there are no GIFs, videos or sprite dependencies.
 */
@Composable
internal fun NikoHero(
    state: NikoVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nikoPremiumHero")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 2800
                    NikoVisualState.LISTENING -> 1200
                    NikoVisualState.THINKING -> 1500
                    NikoVisualState.SPEAKING -> 620
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 1700
                    NikoVisualState.LISTENING -> 700
                    NikoVisualState.THINKING -> 950
                    NikoVisualState.SPEAKING -> 430
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = sin(phase * PI * 2).toFloat() * if (state == NikoVisualState.IDLE) 5f else 3f
            rotationZ = when (state) {
                NikoVisualState.SPEAKING -> sin(phase * PI * 2).toFloat() * 0.7f
                NikoVisualState.THINKING -> sin(phase * PI * 2).toFloat() * 0.35f
                else -> 0f
            }
            scaleX = breathe
            scaleY = breathe
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = min(size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cyan = Color(0xFF57D9F6)
            val blue = Color(0xFF5B82FF)
            val violet = Color(0xFF8A63F6)
            val graphite = Color(0xFF11171B)
            val graphite2 = Color(0xFF1D252B)
            val glass = Color(0xFF040709)
            val accent = when (state) {
                NikoVisualState.THINKING -> violet
                NikoVisualState.SPEAKING -> blue
                else -> cyan
            }

            // Soft ambient glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.17f), accent.copy(alpha = 0.035f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = unit * 0.52f,
                ),
                radius = unit * 0.52f,
                center = Offset(cx, cy),
            )

            // Expanding listening rings / gentle idle halo.
            val ringCount = if (state == NikoVisualState.LISTENING) 4 else 1
            repeat(ringCount) { index ->
                val progress = if (state == NikoVisualState.LISTENING) (phase + index / ringCount.toFloat()) % 1f else 0.15f
                val radius = unit * (0.31f + progress * 0.18f)
                val alpha = if (state == NikoVisualState.LISTENING) (1f - progress) * 0.17f else 0.055f
                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(cx, cy - unit * 0.035f),
                    style = Stroke(width = unit * 0.006f),
                )
            }

            // Thinking orbit.
            if (state == NikoVisualState.THINKING) {
                repeat(5) { index ->
                    val angle = phase * 360f + index * 72f
                    val rad = Math.toRadians(angle.toDouble())
                    val rx = unit * 0.38f
                    val ry = unit * 0.25f
                    drawCircle(
                        color = if (index % 2 == 0) violet else cyan,
                        radius = unit * (0.009f + index * 0.0014f),
                        center = Offset(
                            cx + cos(rad).toFloat() * rx,
                            cy - unit * 0.03f + sin(rad).toFloat() * ry,
                        ),
                    )
                }
            }

            // Hover shadow.
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0xFF355B79).copy(alpha = 0.22f), Color.Transparent)),
                topLeft = Offset(cx - unit * 0.21f, cy + unit * 0.34f),
                size = Size(unit * 0.42f, unit * 0.085f),
            )

            // Small floating body, deliberately understated so the face remains premium.
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(graphite2, Color(0xFF090D10))),
                topLeft = Offset(cx - unit * 0.105f, cy + unit * 0.16f),
                size = Size(unit * 0.21f, unit * 0.20f),
                cornerRadius = CornerRadius(unit * 0.095f),
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(cyan.copy(alpha = 0.18f), blue.copy(alpha = 0.18f))),
                topLeft = Offset(cx - unit * 0.042f, cy + unit * 0.205f),
                size = Size(unit * 0.084f, unit * 0.068f),
                cornerRadius = CornerRadius(unit * 0.022f),
            )
            drawLine(cyan, Offset(cx - unit * 0.023f, cy + unit * 0.258f), Offset(cx - unit * 0.023f, cy + unit * 0.220f), unit * 0.008f, StrokeCap.Round)
            drawLine(cyan, Offset(cx - unit * 0.023f, cy + unit * 0.220f), Offset(cx + unit * 0.023f, cy + unit * 0.258f), unit * 0.008f, StrokeCap.Round)
            drawLine(cyan, Offset(cx + unit * 0.023f, cy + unit * 0.258f), Offset(cx + unit * 0.023f, cy + unit * 0.220f), unit * 0.008f, StrokeCap.Round)

            // Head shell - compact, rounded and more product-like than the previous robot.
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2A3338), graphite2, Color(0xFF0A0E11)),
                    start = Offset(cx - unit * 0.31f, cy - unit * 0.27f),
                    end = Offset(cx + unit * 0.30f, cy + unit * 0.16f),
                ),
                topLeft = Offset(cx - unit * 0.31f, cy - unit * 0.255f),
                size = Size(unit * 0.62f, unit * 0.42f),
                cornerRadius = CornerRadius(unit * 0.18f),
            )

            // Side audio pods.
            listOf(-1f, 1f).forEach { side ->
                val x = cx + side * unit * 0.31f
                drawCircle(graphite2, unit * 0.067f, Offset(x, cy - unit * 0.04f))
                drawCircle(accent.copy(alpha = 0.13f), unit * 0.050f, Offset(x, cy - unit * 0.04f))
                drawCircle(
                    color = accent.copy(alpha = if (state == NikoVisualState.LISTENING) 0.95f else 0.68f),
                    radius = unit * 0.043f,
                    center = Offset(x, cy - unit * 0.04f),
                    style = Stroke(width = unit * 0.008f),
                )
            }

            // Face glass.
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF020405), glass, Color(0xFF10171B))),
                topLeft = Offset(cx - unit * 0.245f, cy - unit * 0.205f),
                size = Size(unit * 0.49f, unit * 0.30f),
                cornerRadius = CornerRadius(unit * 0.125f),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent)),
                topLeft = Offset(cx - unit * 0.19f, cy - unit * 0.185f),
                size = Size(unit * 0.30f, unit * 0.025f),
                cornerRadius = CornerRadius(unit * 0.012f),
            )

            // Face expressions.
            val eyeY = cy - unit * 0.075f
            val eyeX = unit * 0.085f
            when (state) {
                NikoVisualState.IDLE -> {
                    drawLine(cyan.copy(alpha = 0.76f), Offset(cx - eyeX - unit * 0.03f, eyeY), Offset(cx - eyeX + unit * 0.03f, eyeY), unit * 0.012f, StrokeCap.Round)
                    drawLine(cyan.copy(alpha = 0.76f), Offset(cx + eyeX - unit * 0.03f, eyeY), Offset(cx + eyeX + unit * 0.03f, eyeY), unit * 0.012f, StrokeCap.Round)
                }
                NikoVisualState.THINKING -> {
                    drawCircle(violet, unit * 0.017f, Offset(cx - eyeX, eyeY))
                    drawCircle(violet, unit * 0.017f, Offset(cx + eyeX, eyeY + unit * 0.008f))
                }
                else -> {
                    fun eye(centerX: Float) {
                        val path = Path().apply {
                            moveTo(centerX - unit * 0.032f, eyeY + unit * 0.012f)
                            quadraticBezierTo(centerX, eyeY - unit * 0.030f, centerX + unit * 0.032f, eyeY + unit * 0.012f)
                        }
                        drawPath(path, accent, style = Stroke(width = unit * 0.015f, cap = StrokeCap.Round))
                    }
                    eye(cx - eyeX)
                    eye(cx + eyeX)
                }
            }

            val mouthY = cy + unit * 0.018f
            when (state) {
                NikoVisualState.SPEAKING -> {
                    val open = unit * (0.030f + abs(sin(phase * PI * 6).toFloat()) * 0.035f)
                    drawOval(
                        brush = Brush.verticalGradient(listOf(cyan, blue)),
                        topLeft = Offset(cx - unit * 0.040f, mouthY - open / 2f),
                        size = Size(unit * 0.080f, open),
                    )
                }
                NikoVisualState.LISTENING -> {
                    val smile = Path().apply {
                        moveTo(cx - unit * 0.040f, mouthY - unit * 0.003f)
                        quadraticBezierTo(cx, mouthY + unit * 0.033f, cx + unit * 0.040f, mouthY - unit * 0.003f)
                    }
                    drawPath(smile, cyan, style = Stroke(width = unit * 0.012f, cap = StrokeCap.Round))
                }
                NikoVisualState.THINKING -> drawCircle(violet.copy(alpha = 0.88f), unit * 0.010f, Offset(cx, mouthY))
                NikoVisualState.IDLE -> drawLine(cyan.copy(alpha = 0.58f), Offset(cx - unit * 0.022f, mouthY), Offset(cx + unit * 0.022f, mouthY), unit * 0.008f, StrokeCap.Round)
            }

            // Speaking waveform sits close to the face rather than floating far away.
            if (state == NikoVisualState.SPEAKING) {
                repeat(5) { index ->
                    val h = unit * (0.018f + abs(sin((phase * PI * 4 + index * 0.9).toDouble())).toFloat() * 0.045f)
                    val x = cx + unit * 0.19f + index * unit * 0.024f
                    drawRoundRect(
                        color = blue.copy(alpha = 0.82f),
                        topLeft = Offset(x, cy - h / 2f),
                        size = Size(unit * 0.009f, h),
                        cornerRadius = CornerRadius(unit * 0.005f),
                    )
                }
            }
        }
    }
}
