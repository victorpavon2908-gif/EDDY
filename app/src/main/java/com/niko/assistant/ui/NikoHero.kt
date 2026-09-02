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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * NIKO is drawn natively so the mascot is truly reactive instead of being a static image.
 * The four runtime states drive its face, floating motion, listening rings, thinking orbit
 * and speaking mouth/wave. No GIF/video is kept alive in the background.
 */
@Composable
internal fun NikoHero(
    state: NikoVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "nikoMascot")
    val bob by transition.animateFloat(
        initialValue = -5f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    NikoVisualState.LISTENING -> 720
                    NikoVisualState.THINKING -> 1_050
                    NikoVisualState.SPEAKING -> 560
                    NikoVisualState.IDLE -> 1_900
                },
            ),
            RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    NikoVisualState.LISTENING -> 520
                    NikoVisualState.THINKING -> 760
                    NikoVisualState.SPEAKING -> 360
                    NikoVisualState.IDLE -> 1_600
                },
            ),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    NikoVisualState.LISTENING -> 900
                    NikoVisualState.THINKING -> 1_200
                    NikoVisualState.SPEAKING -> 520
                    NikoVisualState.IDLE -> 2_600
                },
            ),
            RepeatMode.Restart,
        ),
        label = "phase",
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = bob
            rotationZ = when (state) {
                NikoVisualState.SPEAKING -> sin(phase * PI * 2).toFloat() * 1.3f
                NikoVisualState.LISTENING -> sin(phase * PI * 2).toFloat() * 0.7f
                else -> sin(phase * PI * 2).toFloat() * 0.35f
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
            val cyan = Color(0xFF6DEBFF)
            val cyanDeep = Color(0xFF2EC9F2)
            val violet = Color(0xFF8C72FF)
            val shell = Color(0xFF101517)
            val shellSoft = Color(0xFF1B2225)
            val face = Color(0xFF050708)

            // State aura.
            when (state) {
                NikoVisualState.LISTENING -> {
                    repeat(3) { index ->
                        val progress = (phase + index / 3f) % 1f
                        drawCircle(
                            color = cyan.copy(alpha = (1f - progress) * 0.20f),
                            radius = unit * (0.30f + progress * 0.18f),
                            center = Offset(cx, cy * 0.72f),
                            style = Stroke(width = unit * 0.008f),
                        )
                    }
                }
                NikoVisualState.THINKING -> {
                    repeat(4) { index ->
                        val angle = phase * 360f + index * 90f
                        val radians = Math.toRadians(angle.toDouble())
                        val radius = unit * 0.42f
                        drawCircle(
                            color = if (index % 2 == 0) violet else cyan,
                            radius = unit * (0.012f + index * 0.002f),
                            center = Offset(
                                cx + cos(radians).toFloat() * radius,
                                cy * 0.82f + sin(radians).toFloat() * radius * 0.56f,
                            ),
                        )
                    }
                }
                NikoVisualState.SPEAKING -> {
                    val baseX = cx + unit * 0.38f
                    repeat(5) { index ->
                        val wave = 0.035f + 0.055f * kotlin.math.abs(sin((phase * PI * 2 + index).toDouble())).toFloat()
                        drawRoundRect(
                            color = cyan.copy(alpha = 0.82f),
                            topLeft = Offset(baseX + index * unit * 0.035f, cy - unit * wave / 2f),
                            size = Size(unit * 0.014f, unit * wave),
                            cornerRadius = CornerRadius(unit * 0.007f),
                        )
                    }
                }
                NikoVisualState.IDLE -> {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(cyan.copy(alpha = 0.09f), Color.Transparent)),
                        radius = unit * 0.43f,
                        center = Offset(cx, cy * 0.86f),
                    )
                }
            }

            // Hover shadow.
            drawOval(
                brush = Brush.radialGradient(listOf(cyan.copy(alpha = 0.32f), Color.Transparent)),
                topLeft = Offset(cx - unit * 0.24f, cy + unit * 0.41f),
                size = Size(unit * 0.48f, unit * 0.075f),
            )

            // Antenna.
            drawLine(
                color = shell,
                start = Offset(cx + unit * 0.05f, cy - unit * 0.37f),
                end = Offset(cx + unit * 0.075f, cy - unit * 0.49f),
                strokeWidth = unit * 0.022f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, cyan, cyanDeep)),
                radius = unit * 0.045f,
                center = Offset(cx + unit * 0.08f, cy - unit * 0.51f),
            )

            // Body first, so arms tuck behind the head.
            drawRoundRect(
                brush = Brush.linearGradient(listOf(shellSoft, shell, Color(0xFF070A0B))),
                topLeft = Offset(cx - unit * 0.16f, cy + unit * 0.18f),
                size = Size(unit * 0.32f, unit * 0.30f),
                cornerRadius = CornerRadius(unit * 0.15f),
            )
            drawOval(
                brush = Brush.linearGradient(listOf(shell, Color(0xFF090C0D))),
                topLeft = Offset(cx - unit * 0.11f, cy + unit * 0.37f),
                size = Size(unit * 0.22f, unit * 0.18f),
            )

            // Arms. Speaking waves one hand, listening opens both arms slightly.
            val leftAngle = when (state) {
                NikoVisualState.SPEAKING -> -34f + sin(phase * PI * 2).toFloat() * 12f
                NikoVisualState.LISTENING -> -26f
                NikoVisualState.THINKING -> -5f
                NikoVisualState.IDLE -> -12f
            }
            val rightAngle = when (state) {
                NikoVisualState.LISTENING -> 24f
                NikoVisualState.THINKING -> -18f
                NikoVisualState.SPEAKING -> 14f
                NikoVisualState.IDLE -> 9f
            }
            rotate(leftAngle, pivot = Offset(cx - unit * 0.13f, cy + unit * 0.24f)) {
                drawLine(
                    color = shellSoft,
                    start = Offset(cx - unit * 0.13f, cy + unit * 0.24f),
                    end = Offset(cx - unit * 0.34f, cy + unit * 0.25f),
                    strokeWidth = unit * 0.075f,
                    cap = StrokeCap.Round,
                )
                drawCircle(shell, unit * 0.047f, Offset(cx - unit * 0.36f, cy + unit * 0.25f))
            }
            rotate(rightAngle, pivot = Offset(cx + unit * 0.13f, cy + unit * 0.24f)) {
                drawLine(
                    color = shellSoft,
                    start = Offset(cx + unit * 0.13f, cy + unit * 0.24f),
                    end = Offset(cx + unit * 0.34f, cy + unit * 0.25f),
                    strokeWidth = unit * 0.075f,
                    cap = StrokeCap.Round,
                )
                drawCircle(shell, unit * 0.047f, Offset(cx + unit * 0.36f, cy + unit * 0.25f))
            }

            // Head shell.
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF2A3032), shell, Color(0xFF080B0C)),
                    start = Offset(cx - unit * 0.34f, cy - unit * 0.34f),
                    end = Offset(cx + unit * 0.30f, cy + unit * 0.17f),
                ),
                topLeft = Offset(cx - unit * 0.34f, cy - unit * 0.32f),
                size = Size(unit * 0.68f, unit * 0.50f),
                cornerRadius = CornerRadius(unit * 0.20f),
            )

            // Ear pods.
            listOf(-1f, 1f).forEach { side ->
                val earX = cx + side * unit * 0.335f
                drawCircle(shellSoft, unit * 0.087f, Offset(earX, cy - unit * 0.06f))
                drawCircle(cyan.copy(alpha = 0.22f), unit * 0.066f, Offset(earX, cy - unit * 0.06f))
                drawCircle(
                    cyan.copy(alpha = if (state == NikoVisualState.LISTENING) 0.95f else 0.70f),
                    unit * 0.054f,
                    Offset(earX, cy - unit * 0.06f),
                    style = Stroke(width = unit * 0.012f),
                )
            }

            // Face glass.
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF020405), face, Color(0xFF0C1112))),
                topLeft = Offset(cx - unit * 0.275f, cy - unit * 0.255f),
                size = Size(unit * 0.55f, unit * 0.34f),
                cornerRadius = CornerRadius(unit * 0.14f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.035f),
                topLeft = Offset(cx - unit * 0.22f, cy - unit * 0.235f),
                size = Size(unit * 0.35f, unit * 0.028f),
                cornerRadius = CornerRadius(unit * 0.014f),
            )

            // Eyes.
            val eyeY = cy - unit * 0.115f
            val eyeOffset = unit * 0.11f
            when (state) {
                NikoVisualState.THINKING -> {
                    drawCircle(cyan, unit * 0.022f, Offset(cx - eyeOffset, eyeY))
                    drawCircle(cyan, unit * 0.022f, Offset(cx + eyeOffset, eyeY + unit * 0.012f))
                    drawLine(cyan, Offset(cx - eyeOffset - unit * 0.032f, eyeY - unit * 0.035f), Offset(cx - eyeOffset + unit * 0.024f, eyeY - unit * 0.046f), unit * 0.012f, StrokeCap.Round)
                    drawLine(cyan, Offset(cx + eyeOffset - unit * 0.024f, eyeY - unit * 0.045f), Offset(cx + eyeOffset + unit * 0.032f, eyeY - unit * 0.032f), unit * 0.012f, StrokeCap.Round)
                }
                NikoVisualState.IDLE -> {
                    drawLine(cyan.copy(alpha = 0.82f), Offset(cx - eyeOffset - unit * 0.042f, eyeY), Offset(cx - eyeOffset + unit * 0.042f, eyeY), unit * 0.018f, StrokeCap.Round)
                    drawLine(cyan.copy(alpha = 0.82f), Offset(cx + eyeOffset - unit * 0.042f, eyeY), Offset(cx + eyeOffset + unit * 0.042f, eyeY), unit * 0.018f, StrokeCap.Round)
                }
                else -> {
                    fun happyEye(centerX: Float) {
                        val p = Path().apply {
                            moveTo(centerX - unit * 0.047f, eyeY + unit * 0.018f)
                            quadraticBezierTo(centerX, eyeY - unit * 0.045f, centerX + unit * 0.047f, eyeY + unit * 0.018f)
                        }
                        drawPath(p, cyan, style = Stroke(width = unit * 0.021f, cap = StrokeCap.Round))
                    }
                    happyEye(cx - eyeOffset)
                    happyEye(cx + eyeOffset)
                }
            }

            // Mouth reacts to speech amplitude.
            val mouthCenter = Offset(cx, cy - unit * 0.005f)
            when (state) {
                NikoVisualState.SPEAKING -> {
                    val open = unit * (0.035f + 0.032f * kotlin.math.abs(sin(phase * PI * 4).toFloat()))
                    drawOval(
                        brush = Brush.verticalGradient(listOf(cyan, cyanDeep)),
                        topLeft = Offset(mouthCenter.x - unit * 0.045f, mouthCenter.y - open / 2f),
                        size = Size(unit * 0.09f, open),
                    )
                }
                NikoVisualState.THINKING -> drawCircle(cyan.copy(alpha = 0.85f), unit * 0.013f, mouthCenter)
                NikoVisualState.IDLE -> drawLine(cyan.copy(alpha = 0.75f), Offset(cx - unit * 0.032f, mouthCenter.y), Offset(cx + unit * 0.032f, mouthCenter.y), unit * 0.012f, StrokeCap.Round)
                NikoVisualState.LISTENING -> {
                    val smile = Path().apply {
                        moveTo(cx - unit * 0.044f, mouthCenter.y - unit * 0.006f)
                        quadraticBezierTo(cx, mouthCenter.y + unit * 0.046f, cx + unit * 0.044f, mouthCenter.y - unit * 0.006f)
                    }
                    drawPath(smile, cyan, style = Stroke(width = unit * 0.016f, cap = StrokeCap.Round))
                }
            }

            // Glowing chest N.
            drawRoundRect(
                color = cyan.copy(alpha = 0.10f),
                topLeft = Offset(cx - unit * 0.065f, cy + unit * 0.245f),
                size = Size(unit * 0.13f, unit * 0.12f),
                cornerRadius = CornerRadius(unit * 0.04f),
            )
            withTransform({ translate(left = cx - unit * 0.04f, top = cy + unit * 0.268f) }) {
                drawLine(cyan, Offset(0f, 0f), Offset(0f, unit * 0.07f), unit * 0.014f, StrokeCap.Round)
                drawLine(cyan, Offset(0f, 0f), Offset(unit * 0.08f, unit * 0.07f), unit * 0.014f, StrokeCap.Round)
                drawLine(cyan, Offset(unit * 0.08f, 0f), Offset(unit * 0.08f, unit * 0.07f), unit * 0.014f, StrokeCap.Round)
            }
        }
    }
}
