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
    val transition = rememberInfiniteTransition(label = "eddyCompactHero")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.SPEAKING -> 420
                    EddyVisualState.LISTENING -> 720
                    EddyVisualState.THINKING -> 900
                    EddyVisualState.IDLE -> 1_800
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == EddyVisualState.THINKING) 1_150 else 3_600,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )
    val mouth by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mouth",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val field = unit * 0.34f
        val graphite = Color(0xFF0A1512)
        val soft = Color(0xFFCBD8D3)
        val mint = EddyMint
        val stroke = (unit * 0.018f).coerceAtLeast(5f)
        val detail = (unit * 0.009f).coerceAtLeast(2.6f)

        drawCircle(
            color = soft.copy(alpha = 0.62f),
            radius = field,
            center = center,
            style = Stroke(width = detail),
        )
        drawCircle(
            color = mint.copy(alpha = 0.12f + 0.12f * pulse),
            radius = field * (0.80f + 0.07f * pulse),
            center = center,
        )

        val angle = Math.toRadians(orbit.toDouble())
        val node = Offset(
            x = center.x + cos(angle).toFloat() * field,
            y = center.y + sin(angle).toFloat() * field,
        )
        drawCircle(mint, radius = unit * 0.014f, center = node)

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
        drawPath(
            path = shell,
            color = graphite,
            style = Stroke(width = stroke, cap = StrokeCap.Square),
        )

        val midY = center.y + h * 0.08f
        drawLine(
            graphite,
            Offset(left, midY),
            Offset(right, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Square,
        )

        val browY = center.y - h * 0.15f
        val eyeHalf = w * 0.12f
        drawLine(graphite, Offset(center.x - w * 0.25f, browY), Offset(center.x - w * 0.08f, browY + detail), detail, StrokeCap.Round)
        drawLine(graphite, Offset(center.x + w * 0.08f, browY + detail), Offset(center.x + w * 0.25f, browY), detail, StrokeCap.Round)
        drawCircle(mint, radius = unit * 0.012f, center = Offset(center.x - eyeHalf, browY + h * 0.055f))
        drawCircle(mint, radius = unit * 0.012f, center = Offset(center.x + eyeHalf, browY + h * 0.055f))

        val mouthY = center.y - h * 0.015f
        val mouthWidth = w * 0.25f
        if (state == EddyVisualState.SPEAKING) {
            drawLine(
                graphite,
                Offset(center.x - mouthWidth, mouthY),
                Offset(center.x + mouthWidth, mouthY),
                detail + mouth * unit * 0.004f,
                StrokeCap.Round,
            )
        } else {
            drawLine(
                graphite,
                Offset(center.x - mouthWidth, mouthY),
                Offset(center.x + mouthWidth, mouthY),
                detail,
                StrokeCap.Round,
            )
        }

        val coreTop = midY + h * 0.055f
        val coreBottom = bottom - h * 0.09f
        drawLine(graphite, Offset(center.x, coreTop), Offset(center.x, coreBottom), detail, StrokeCap.Square)
        drawLine(graphite, Offset(center.x - w * 0.20f, coreTop), Offset(center.x, center.y + h * 0.24f), detail, StrokeCap.Square)
        drawLine(graphite, Offset(center.x + w * 0.20f, coreTop), Offset(center.x, center.y + h * 0.24f), detail, StrokeCap.Square)

        val activity = when (state) {
            EddyVisualState.IDLE -> 2
            EddyVisualState.LISTENING -> 4
            EddyVisualState.THINKING -> 5
            EddyVisualState.SPEAKING -> 6
        }
        repeat(activity) { index ->
            val x = center.x - unit * 0.095f + index * unit * 0.038f
            val barHeight = unit * (0.026f + ((index % 3) * 0.012f)) * pulse
            drawLine(
                color = if (state == EddyVisualState.THINKING) EddyBlue else mint,
                start = Offset(x, bottom + unit * 0.075f - barHeight / 2f),
                end = Offset(x, bottom + unit * 0.075f + barHeight / 2f),
                strokeWidth = detail,
                cap = StrokeCap.Square,
            )
        }
    }
}
