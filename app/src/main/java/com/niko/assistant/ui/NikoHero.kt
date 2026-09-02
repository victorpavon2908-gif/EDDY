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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Premium native companion for NIKO.
 *
 * Motion language is intentionally state-driven: breathing at rest, expanding acoustic
 * rings while listening, orbiting cognition dots while thinking and a live equalizer while
 * speaking. The approach was adapted to NIKO after studying open-source Compose assistant
 * motion patterns; the character geometry and state rendering are NIKO-specific.
 */
@Composable
internal fun NikoHero(
    state: NikoVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nikoPremiumCompanion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 2_600
                    NikoVisualState.LISTENING -> 1_100
                    NikoVisualState.THINKING -> 1_350
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
                    NikoVisualState.IDLE -> 1_850
                    NikoVisualState.LISTENING -> 780
                    NikoVisualState.THINKING -> 1_050
                    NikoVisualState.SPEAKING -> 520
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val floatY by transition.animateFloat(
        initialValue = -4f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == NikoVisualState.IDLE) 1_900 else 1_250),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatY",
    )

    val accent = when (state) {
        NikoVisualState.IDLE -> Color(0xFF62CFEA)
        NikoVisualState.LISTENING -> Color(0xFF37D6F5)
        NikoVisualState.THINKING -> Color(0xFF8B73FF)
        NikoVisualState.SPEAKING -> Color(0xFF4CD8F2)
    }
    val violet = Color(0xFF8C6CF4)

    Box(
        modifier = modifier.graphicsLayer {
            translationY = floatY
            scaleX = breathe
            scaleY = breathe
            rotationZ = when (state) {
                NikoVisualState.SPEAKING -> sin(phase * PI * 2).toFloat() * 0.8f
                NikoVisualState.LISTENING -> sin(phase * PI * 2).toFloat() * 0.35f
                else -> 0f
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = min(size.width, size.height)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val shellDark = Color(0xFF090D10)
            val shell = Color(0xFF121A1E)
            val shellLight = Color(0xFF263238)
            val face = Color(0xFF020507)
            val cyanSoft = Color(0xFF91F2FF)

            // Ambient field: inspired by modern hands-free assistant orbs, but tailored to NIKO.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(cx, cy * 0.88f),
                    radius = unit * 0.52f,
                ),
                radius = unit * 0.52f,
                center = Offset(cx, cy * 0.88f),
            )

            when (state) {
                NikoVisualState.LISTENING -> {
                    repeat(3) { index ->
                        val p = (phase + index / 3f) % 1f
                        drawCircle(
                            color = accent.copy(alpha = (1f - p) * 0.23f),
                            radius = unit * (0.31f + p * 0.16f),
                            center = Offset(cx, cy * 0.84f),
                            style = Stroke(width = unit * 0.0065f),
                        )
                    }
                }
                NikoVisualState.THINKING -> {
                    drawCircle(
                        color = violet.copy(alpha = 0.10f + 0.06f * abs(sin(phase * PI * 2).toFloat())),
                        radius = unit * 0.43f,
                        center = Offset(cx, cy * 0.83f),
                        style = Stroke(width = unit * 0.008f),
                    )
                    repeat(4) { index ->
                        val angle = phase * PI * 2 + index * PI / 2
                        val orbitX = cx + cos(angle).toFloat() * unit * 0.40f
                        val orbitY = cy * 0.83f + sin(angle).toFloat() * unit * 0.25f
                        drawCircle(
                            color = if (index % 2 == 0) violet else accent,
                            radius = unit * (0.010f + index * 0.0015f),
                            center = Offset(orbitX, orbitY),
                        )
                    }
                }
                NikoVisualState.SPEAKING -> {
                    repeat(6) { index ->
                        val amp = 0.025f + abs(sin((phase * PI * 4 + index * 0.8).toDouble())).toFloat() * 0.065f
                        val barHeight = unit * amp
                        val leftX = cx - unit * (0.43f + index * 0.025f)
                        val rightX = cx + unit * (0.43f + index * 0.025f)
                        listOf(leftX, rightX).forEach { x ->
                            drawRoundRect(
                                color = accent.copy(alpha = 0.75f - index * 0.07f),
                                topLeft = Offset(x - unit * 0.006f, cy * 0.84f - barHeight / 2f),
                                size = Size(unit * 0.012f, barHeight),
                                cornerRadius = CornerRadius(unit * 0.006f),
                            )
                        }
                    }
                }
                NikoVisualState.IDLE -> Unit
            }

            // Floating shadow.
            drawOval(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)),
                topLeft = Offset(cx - unit * 0.20f, cy + unit * 0.36f),
                size = Size(unit * 0.40f, unit * 0.07f),
            )

            // Compact torso first so it remains behind the head.
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(shellLight, shell, shellDark)),
                topLeft = Offset(cx - unit * 0.115f, cy + unit * 0.17f),
                size = Size(unit * 0.23f, unit * 0.25f),
                cornerRadius = CornerRadius(unit * 0.105f),
            )
            drawOval(
                color = shellDark,
                topLeft = Offset(cx - unit * 0.075f, cy + unit * 0.34f),
                size = Size(unit * 0.15f, unit * 0.11f),
            )

            // Small expressive arms. They never overpower the face.
            val armWave = sin(phase * PI * 2).toFloat()
            val leftAngle = when (state) {
                NikoVisualState.SPEAKING -> -28f + armWave * 11f
                NikoVisualState.LISTENING -> -20f
                NikoVisualState.THINKING -> -4f
                NikoVisualState.IDLE -> -10f
            }
            val rightAngle = when (state) {
                NikoVisualState.SPEAKING -> 16f
                NikoVisualState.LISTENING -> 20f
                NikoVisualState.THINKING -> -15f
                NikoVisualState.IDLE -> 10f
            }
            rotate(leftAngle, pivot = Offset(cx - unit * 0.09f, cy + unit * 0.22f)) {
                drawLine(
                    color = shellLight,
                    start = Offset(cx - unit * 0.09f, cy + unit * 0.22f),
                    end = Offset(cx - unit * 0.24f, cy + unit * 0.25f),
                    strokeWidth = unit * 0.052f,
                    cap = StrokeCap.Round,
                )
                drawCircle(shellDark, unit * 0.032f, Offset(cx - unit * 0.25f, cy + unit * 0.25f))
            }
            rotate(rightAngle, pivot = Offset(cx + unit * 0.09f, cy + unit * 0.22f)) {
                drawLine(
                    color = shellLight,
                    start = Offset(cx + unit * 0.09f, cy + unit * 0.22f),
                    end = Offset(cx + unit * 0.24f, cy + unit * 0.25f),
                    strokeWidth = unit * 0.052f,
                    cap = StrokeCap.Round,
                )
                drawCircle(shellDark, unit * 0.032f, Offset(cx + unit * 0.25f, cy + unit * 0.25f))
            }

            // Antenna and state light.
            drawLine(
                color = shellLight,
                start = Offset(cx + unit * 0.045f, cy - unit * 0.28f),
                end = Offset(cx + unit * 0.065f, cy - unit * 0.385f),
                strokeWidth = unit * 0.014f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, accent, accent.copy(alpha = 0.35f))),
                radius = unit * 0.035f,
                center = Offset(cx + unit * 0.068f, cy - unit * 0.405f),
            )

            // Head shell with a subtle rim; narrower and less toy-like than the previous version.
            drawRoundRect(
                color = accent.copy(alpha = 0.09f),
                topLeft = Offset(cx - unit * 0.315f, cy - unit * 0.265f),
                size = Size(unit * 0.63f, unit * 0.42f),
                cornerRadius = CornerRadius(unit * 0.17f),
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF344148), shell, shellDark),
                    start = Offset(cx - unit * 0.31f, cy - unit * 0.25f),
                    end = Offset(cx + unit * 0.29f, cy + unit * 0.15f),
                ),
                topLeft = Offset(cx - unit * 0.30f, cy - unit * 0.25f),
                size = Size(unit * 0.60f, unit * 0.39f),
                cornerRadius = CornerRadius(unit * 0.16f),
            )

            // Ear pods glow more intensely only while NIKO is actively listening.
            listOf(-1f, 1f).forEach { side ->
                val earX = cx + side * unit * 0.302f
                val earY = cy - unit * 0.055f
                drawCircle(shellLight, unit * 0.069f, Offset(earX, earY))
                drawCircle(accent.copy(alpha = 0.17f), unit * 0.052f, Offset(earX, earY))
                drawCircle(
                    color = accent.copy(alpha = if (state == NikoVisualState.LISTENING) 0.95f else 0.68f),
                    radius = unit * 0.043f,
                    center = Offset(earX, earY),
                    style = Stroke(width = unit * 0.009f),
                )
            }

            // Face glass.
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF010304), face, Color(0xFF0C1418)),
                    start = Offset(cx - unit * 0.24f, cy - unit * 0.20f),
                    end = Offset(cx + unit * 0.24f, cy + unit * 0.08f),
                ),
                topLeft = Offset(cx - unit * 0.245f, cy - unit * 0.195f),
                size = Size(unit * 0.49f, unit * 0.265f),
                cornerRadius = CornerRadius(unit * 0.115f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.035f),
                topLeft = Offset(cx - unit * 0.19f, cy - unit * 0.174f),
                size = Size(unit * 0.29f, unit * 0.018f),
                cornerRadius = CornerRadius(unit * 0.009f),
            )

            val eyeY = cy - unit * 0.082f
            val eyeDx = unit * 0.088f
            val eyeColor = cyanSoft
            val blink = state == NikoVisualState.IDLE && phase > 0.88f

            fun drawHappyEye(centerX: Float) {
                val eyePath = Path().apply {
                    moveTo(centerX - unit * 0.036f, eyeY + unit * 0.012f)
                    quadraticBezierTo(centerX, eyeY - unit * 0.032f, centerX + unit * 0.036f, eyeY + unit * 0.012f)
                }
                drawPath(eyePath, eyeColor, style = Stroke(width = unit * 0.014f, cap = StrokeCap.Round))
            }

            when {
                blink -> {
                    drawLine(eyeColor, Offset(cx - eyeDx - unit * 0.03f, eyeY), Offset(cx - eyeDx + unit * 0.03f, eyeY), unit * 0.012f, StrokeCap.Round)
                    drawLine(eyeColor, Offset(cx + eyeDx - unit * 0.03f, eyeY), Offset(cx + eyeDx + unit * 0.03f, eyeY), unit * 0.012f, StrokeCap.Round)
                }
                state == NikoVisualState.THINKING -> {
                    drawCircle(eyeColor, unit * 0.016f, Offset(cx - eyeDx, eyeY))
                    drawCircle(eyeColor, unit * 0.016f, Offset(cx + eyeDx, eyeY + unit * 0.008f))
                    drawLine(eyeColor, Offset(cx - eyeDx - unit * 0.025f, eyeY - unit * 0.026f), Offset(cx - eyeDx + unit * 0.018f, eyeY - unit * 0.034f), unit * 0.009f, StrokeCap.Round)
                }
                state == NikoVisualState.IDLE -> {
                    drawCircle(eyeColor.copy(alpha = 0.82f), unit * 0.015f, Offset(cx - eyeDx, eyeY))
                    drawCircle(eyeColor.copy(alpha = 0.82f), unit * 0.015f, Offset(cx + eyeDx, eyeY))
                }
                else -> {
                    drawHappyEye(cx - eyeDx)
                    drawHappyEye(cx + eyeDx)
                }
            }

            // Mouth: tiny smile while listening, quiet line at rest, animated equalizer when speaking.
            val mouthY = cy + unit * 0.005f
            when (state) {
                NikoVisualState.SPEAKING -> {
                    repeat(3) { index ->
                        val h = unit * (0.018f + abs(sin((phase * PI * 4 + index).toDouble())).toFloat() * 0.028f)
                        val x = cx + (index - 1) * unit * 0.026f
                        drawRoundRect(
                            color = eyeColor,
                            topLeft = Offset(x - unit * 0.006f, mouthY - h / 2f),
                            size = Size(unit * 0.012f, h),
                            cornerRadius = CornerRadius(unit * 0.006f),
                        )
                    }
                }
                NikoVisualState.LISTENING -> {
                    val smile = Path().apply {
                        moveTo(cx - unit * 0.032f, mouthY - unit * 0.004f)
                        quadraticBezierTo(cx, mouthY + unit * 0.025f, cx + unit * 0.032f, mouthY - unit * 0.004f)
                    }
                    drawPath(smile, eyeColor, style = Stroke(width = unit * 0.010f, cap = StrokeCap.Round))
                }
                NikoVisualState.THINKING -> drawCircle(eyeColor.copy(alpha = 0.9f), unit * 0.008f, Offset(cx, mouthY))
                NikoVisualState.IDLE -> drawLine(eyeColor.copy(alpha = 0.64f), Offset(cx - unit * 0.022f, mouthY), Offset(cx + unit * 0.022f, mouthY), unit * 0.008f, StrokeCap.Round)
            }

            // Chest mark: simple N drawn as strokes, keeping the interface independent from image assets.
            val logoYTop = cy + unit * 0.245f
            val logoYBottom = cy + unit * 0.315f
            val logoLeft = cx - unit * 0.035f
            val logoRight = cx + unit * 0.035f
            drawLine(accent, Offset(logoLeft, logoYBottom), Offset(logoLeft, logoYTop), unit * 0.010f, StrokeCap.Round)
            drawLine(accent, Offset(logoLeft, logoYTop), Offset(logoRight, logoYBottom), unit * 0.010f, StrokeCap.Round)
            drawLine(accent, Offset(logoRight, logoYBottom), Offset(logoRight, logoYTop), unit * 0.010f, StrokeCap.Round)
        }
    }
}
