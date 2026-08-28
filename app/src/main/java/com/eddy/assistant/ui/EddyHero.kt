package com.eddy.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun EddyHero(
    state: EddyVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyAliveHero")
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.SPEAKING -> 360
                    EddyVisualState.LISTENING -> 620
                    EddyVisualState.THINKING -> 760
                    EddyVisualState.IDLE -> 2_200
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.THINKING -> 920
                    EddyVisualState.LISTENING -> 2_200
                    EddyVisualState.SPEAKING -> 1_700
                    EddyVisualState.IDLE -> 4_800
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    val voice by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == EddyVisualState.SPEAKING) 145 else 420),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice",
    )
    val eyeEnergy by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (state == EddyVisualState.LISTENING) 520 else 1_300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eyes",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f - unit * 0.015f)
        val field = unit * 0.34f
        val graphite = Color(0xFF07120F)
        val soft = Color(0xFFCAD8D3)
        val mint = EddyMint
        val stateAccent = when (state) {
            EddyVisualState.THINKING -> EddyBlue
            EddyVisualState.SPEAKING -> Color(0xFF54DDB4)
            else -> mint
        }
        val stroke = (unit * 0.018f).coerceAtLeast(5f)
        val detail = (unit * 0.0085f).coerceAtLeast(2.5f)

        // Campo de presencia: respira despacio cuando EDDY está simplemente atento.
        drawCircle(
            color = stateAccent.copy(alpha = 0.055f + 0.06f * breath),
            radius = field * 1.14f * breath,
            center = center,
        )
        drawCircle(
            color = soft.copy(alpha = 0.62f),
            radius = field,
            center = center,
            style = Stroke(width = detail),
        )
        drawCircle(
            color = stateAccent.copy(alpha = 0.12f + 0.10f * breath),
            radius = field * (0.78f + 0.08f * breath),
            center = center,
        )

        // Dos nodos orbitales hacen visible que está atento sin fingir que transcribe.
        val angle = Math.toRadians(orbit.toDouble())
        val node = Offset(
            x = center.x + cos(angle).toFloat() * field,
            y = center.y + sin(angle).toFloat() * field,
        )
        val opposite = Offset(
            x = center.x + cos(angle + Math.PI).toFloat() * field,
            y = center.y + sin(angle + Math.PI).toFloat() * field,
        )
        drawCircle(stateAccent, radius = unit * 0.014f, center = node)
        drawCircle(stateAccent.copy(alpha = 0.42f), radius = unit * 0.007f, center = opposite)

        val w = unit * 0.39f
        val h = unit * 0.46f
        val left = center.x - w / 2f
        val right = center.x + w / 2f
        val top = center.y - h / 2f
        val bottom = center.y + h / 2f
        val cut = unit * 0.065f

        val shell = Path().apply {
            moveTo(left + cut, top)
            lineTo(right - cut, top)
            lineTo(right, top + cut)
            lineTo(right, bottom - cut)
            lineTo(right - cut, bottom)
            lineTo(left + cut, bottom)
            lineTo(left, bottom - cut)
            lineTo(left, top + cut)
            close()
        }
        drawPath(shell, graphite, style = Stroke(width = stroke, cap = StrokeCap.Square))

        // Un segundo borde interior le da profundidad sin convertirlo en un robot 3D pesado.
        drawPath(
            shell,
            stateAccent.copy(alpha = 0.18f + if (state == EddyVisualState.LISTENING) 0.12f else 0f),
            style = Stroke(width = detail),
        )

        val midY = center.y + h * 0.08f
        drawLine(graphite, Offset(left, midY), Offset(right, midY), stroke, StrokeCap.Square)

        val browY = center.y - h * 0.15f
        val eyeHalf = w * 0.12f
        val browLift = if (state == EddyVisualState.LISTENING) -h * 0.012f else 0f
        drawLine(
            graphite,
            Offset(center.x - w * 0.25f, browY + browLift),
            Offset(center.x - w * 0.08f, browY + detail + browLift),
            detail,
            StrokeCap.Round,
        )
        drawLine(
            graphite,
            Offset(center.x + w * 0.08f, browY + detail + browLift),
            Offset(center.x + w * 0.25f, browY + browLift),
            detail,
            StrokeCap.Round,
        )
        val eyeRadius = unit * (0.010f + 0.004f * eyeEnergy)
        drawCircle(stateAccent.copy(alpha = 0.35f), eyeRadius * 2.1f, Offset(center.x - eyeHalf, browY + h * 0.055f))
        drawCircle(stateAccent.copy(alpha = 0.35f), eyeRadius * 2.1f, Offset(center.x + eyeHalf, browY + h * 0.055f))
        drawCircle(stateAccent, eyeRadius, Offset(center.x - eyeHalf, browY + h * 0.055f))
        drawCircle(stateAccent, eyeRadius, Offset(center.x + eyeHalf, browY + h * 0.055f))

        val mouthY = center.y - h * 0.015f
        val mouthWidth = w * 0.25f
        val mouthThickness = if (state == EddyVisualState.SPEAKING) detail + voice * unit * 0.004f else detail
        drawLine(
            graphite,
            Offset(center.x - mouthWidth, mouthY),
            Offset(center.x + mouthWidth, mouthY),
            mouthThickness,
            StrokeCap.Round,
        )

        val coreTop = midY + h * 0.055f
        val coreBottom = bottom - h * 0.09f
        drawLine(graphite, Offset(center.x, coreTop), Offset(center.x, coreBottom), detail, StrokeCap.Square)
        drawLine(graphite, Offset(center.x - w * 0.20f, coreTop), Offset(center.x, center.y + h * 0.24f), detail, StrokeCap.Square)
        drawLine(graphite, Offset(center.x + w * 0.20f, coreTop), Offset(center.x, center.y + h * 0.24f), detail, StrokeCap.Square)
        drawCircle(
            color = stateAccent.copy(alpha = if (state == EddyVisualState.THINKING) 0.95f else 0.55f),
            radius = unit * 0.011f * breath,
            center = Offset(center.x, center.y + h * 0.24f),
        )

        val activity = when (state) {
            EddyVisualState.IDLE -> 3
            EddyVisualState.LISTENING -> 5
            EddyVisualState.THINKING -> 6
            EddyVisualState.SPEAKING -> 7
        }
        repeat(activity) { index ->
            val x = center.x - unit * 0.112f + index * unit * 0.037f
            val phase = if (state == EddyVisualState.SPEAKING) voice else breath
            val barHeight = unit * (0.022f + ((index % 4) * 0.010f)) * phase
            drawLine(
                color = stateAccent,
                start = Offset(x, bottom + unit * 0.075f - barHeight / 2f),
                end = Offset(x, bottom + unit * 0.075f + barHeight / 2f),
                strokeWidth = detail,
                cap = StrokeCap.Square,
            )
        }
    }
}
