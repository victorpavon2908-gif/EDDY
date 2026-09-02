package com.niko.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * NIKO's neural core.
 *
 * The old literal robot was replaced by a state-reactive organic orb. The morphing/blob idea is
 * adapted from the MIT-licensed HandsFreeBar in souravanand001/ai-assistant-android, while the
 * render, palette, layered core, particles and NIKO monogram are original to this project.
 * No image, GIF, video or WebView is required: everything is rendered by Compose Canvas.
 */
@Composable
internal fun NikoHero(
    state: NikoVisualState,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "nikoNeuralCore")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 5_200
                    NikoVisualState.LISTENING -> 2_200
                    NikoVisualState.THINKING -> 1_700
                    NikoVisualState.SPEAKING -> 920
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbPhase",
    )
    val breath by infinite.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 2_800
                    NikoVisualState.LISTENING -> 1_350
                    NikoVisualState.THINKING -> 1_650
                    NikoVisualState.SPEAKING -> 780
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbBreath",
    )
    val energy by animateFloatAsState(
        targetValue = when (state) {
            NikoVisualState.IDLE -> 0.18f
            NikoVisualState.LISTENING -> 0.72f
            NikoVisualState.THINKING -> 0.88f
            NikoVisualState.SPEAKING -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "orbEnergy",
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = breath
            scaleY = breath
            rotationZ = when (state) {
                NikoVisualState.THINKING -> sin(phase * PI * 2).toFloat() * 0.9f
                NikoVisualState.SPEAKING -> sin(phase * PI * 4).toFloat() * 0.45f
                else -> 0f
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val cyan = Color(0xFF5CEBFF)
            val electricBlue = Color(0xFF5A7CFF)
            val violet = Color(0xFF9B6CFF)
            val magenta = Color(0xFFFF6FD8)
            val mint = Color(0xFF49F2C2)
            val accentA = when (state) {
                NikoVisualState.IDLE -> cyan
                NikoVisualState.LISTENING -> mint
                NikoVisualState.THINKING -> violet
                NikoVisualState.SPEAKING -> magenta
            }
            val accentB = when (state) {
                NikoVisualState.IDLE -> electricBlue
                NikoVisualState.LISTENING -> cyan
                NikoVisualState.THINKING -> electricBlue
                NikoVisualState.SPEAKING -> cyan
            }

            fun organicPath(
                radius: Float,
                distortion: Float,
                phaseOffset: Float,
                lobesA: Int,
                lobesB: Int,
            ): Path {
                val path = Path()
                val steps = 112
                for (index in 0..steps) {
                    val angle = (index.toFloat() / steps) * (PI * 2).toFloat()
                    val waveA = sin(angle * lobesA + phase * PI.toFloat() * 2f + phaseOffset)
                    val waveB = cos(angle * lobesB - phase * PI.toFloat() * 1.65f - phaseOffset)
                    val waveC = sin(angle * 2f + phase * PI.toFloat() * 0.8f)
                    val r = radius + distortion * (waveA * 0.55f + waveB * 0.30f + waveC * 0.15f)
                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                return path
            }

            // Atmospheric halo: the visual presence is larger than the actual core.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentA.copy(alpha = 0.20f + energy * 0.07f),
                        accentB.copy(alpha = 0.075f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = unit * 0.50f,
                ),
                radius = unit * 0.50f,
                center = center,
            )

            // Listening expands in clean acoustic ripples instead of showing a generic spinner.
            if (state == NikoVisualState.LISTENING) {
                repeat(4) { index ->
                    val progress = (phase * 1.45f + index * 0.25f) % 1f
                    drawCircle(
                        color = mint.copy(alpha = (1f - progress) * 0.22f),
                        radius = unit * (0.29f + progress * 0.17f),
                        center = center,
                        style = Stroke(width = unit * 0.0055f),
                    )
                }
            }

            // Thin orbital arcs add depth without turning the UI into a sci-fi HUD overload.
            repeat(3) { index ->
                val radius = unit * (0.335f + index * 0.035f)
                val spin = phase * 360f * (if (index % 2 == 0) 1f else -0.72f) + index * 71f
                rotate(spin, pivot = center) {
                    drawArc(
                        color = (if (index == 1) accentA else accentB).copy(alpha = 0.12f + energy * 0.10f),
                        startAngle = 18f + index * 31f,
                        sweepAngle = 54f + index * 16f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = unit * (0.004f + index * 0.0007f), cap = StrokeCap.Round),
                    )
                }
            }

            // A small particle field makes thinking/speaking feel alive but remains deterministic.
            val particleCount = if (state == NikoVisualState.IDLE) 8 else 16
            repeat(particleCount) { index ->
                val baseAngle = (index.toFloat() / particleCount) * (PI * 2).toFloat()
                val direction = if (index % 2 == 0) 1f else -1f
                val angle = baseAngle + phase * direction * (0.55f + index % 3 * 0.08f) * (PI * 2).toFloat()
                val orbit = unit * (0.34f + (index % 4) * 0.025f)
                val wobble = unit * 0.018f * sin(phase * PI.toFloat() * 4f + index)
                val point = Offset(
                    center.x + cos(angle) * (orbit + wobble),
                    center.y + sin(angle) * (orbit * 0.72f + wobble),
                )
                drawCircle(
                    color = (if (index % 3 == 0) accentA else accentB).copy(alpha = 0.20f + energy * 0.38f),
                    radius = unit * (0.0045f + (index % 3) * 0.0017f),
                    center = point,
                )
            }

            val baseRadius = unit * 0.235f
            val distortion = unit * when (state) {
                NikoVisualState.IDLE -> 0.008f
                NikoVisualState.LISTENING -> 0.019f
                NikoVisualState.THINKING -> 0.027f
                NikoVisualState.SPEAKING -> 0.034f
            }

            val outer = organicPath(baseRadius * 1.10f, distortion * 1.22f, 0.35f, 3, 5)
            val body = organicPath(baseRadius, distortion, 1.05f, 4, 7)
            val core = organicPath(baseRadius * 0.67f, distortion * 0.42f, 2.1f, 3, 6)

            // Outer chromatic membrane.
            drawPath(
                path = outer,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        accentA.copy(alpha = 0.15f),
                        accentB.copy(alpha = 0.70f),
                        accentA.copy(alpha = 0.78f),
                        violet.copy(alpha = 0.46f),
                        accentA.copy(alpha = 0.15f),
                    ),
                    center = center,
                ),
                alpha = 0.58f + energy * 0.28f,
            )

            // Dark liquid-glass body with a colored internal light source.
            drawPath(
                path = body,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF26334A),
                        Color(0xFF101628),
                        Color(0xFF060914),
                    ),
                    center = Offset(center.x - unit * 0.07f, center.y - unit * 0.09f),
                    radius = baseRadius * 1.65f,
                ),
            )
            drawPath(
                path = body,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        accentA.copy(alpha = 0.15f),
                        Color.Transparent,
                        accentB.copy(alpha = 0.22f),
                        Color.Transparent,
                        accentA.copy(alpha = 0.15f),
                    ),
                    center = center,
                ),
                alpha = 0.88f,
            )

            // Luminous neural core.
            drawPath(
                path = core,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        accentA.copy(alpha = 0.78f),
                        accentB.copy(alpha = 0.34f),
                        Color(0xFF09101E).copy(alpha = 0.92f),
                    ),
                    center = Offset(center.x - unit * 0.035f, center.y - unit * 0.045f),
                    radius = baseRadius * 0.95f,
                ),
                alpha = 0.62f + energy * 0.30f,
            )

            // Specular highlight sells the glass material on OLED and LCD screens alike.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(center.x - unit * 0.09f, center.y - unit * 0.11f),
                    radius = unit * 0.12f,
                ),
                radius = unit * 0.12f,
                center = Offset(center.x - unit * 0.09f, center.y - unit * 0.11f),
            )

            // Minimal NIKO monogram: recognizable without putting text inside Canvas.
            val markColor = Color.White.copy(alpha = 0.88f)
            val markWidth = unit * 0.010f
            val markLeft = center.x - unit * 0.045f
            val markRight = center.x + unit * 0.045f
            val markTop = center.y - unit * 0.050f
            val markBottom = center.y + unit * 0.050f
            drawLine(markColor, Offset(markLeft, markBottom), Offset(markLeft, markTop), markWidth, StrokeCap.Round)
            drawLine(markColor, Offset(markLeft, markTop), Offset(markRight, markBottom), markWidth, StrokeCap.Round)
            drawLine(markColor, Offset(markRight, markBottom), Offset(markRight, markTop), markWidth, StrokeCap.Round)

            // Speaking energy bars sit on the orbit, not on a cartoon mouth.
            if (state == NikoVisualState.SPEAKING) {
                repeat(20) { index ->
                    val angle = (index / 20f) * (PI * 2).toFloat()
                    val wave = abs(sin(phase * PI.toFloat() * 7f + index * 0.83f))
                    val inner = unit * 0.305f
                    val outerRadius = inner + unit * (0.018f + wave * 0.050f)
                    val start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
                    val end = Offset(center.x + cos(angle) * outerRadius, center.y + sin(angle) * outerRadius)
                    drawLine(
                        color = (if (index % 2 == 0) magenta else cyan).copy(alpha = 0.46f + wave * 0.40f),
                        start = start,
                        end = end,
                        strokeWidth = unit * 0.006f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
