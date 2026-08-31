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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
    val transition = rememberInfiniteTransition(label = "eddyPremiumHero")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            tween(
                when (state) {
                    EddyVisualState.LISTENING -> 620
                    EddyVisualState.THINKING -> 850
                    EddyVisualState.SPEAKING -> 430
                    EddyVisualState.IDLE -> 2_200
                },
            ),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(
                when (state) {
                    EddyVisualState.THINKING -> 1_200
                    EddyVisualState.LISTENING -> 2_300
                    EddyVisualState.SPEAKING -> 1_700
                    EddyVisualState.IDLE -> 5_200
                },
            ),
            RepeatMode.Restart,
        ),
        label = "orbit",
    )

    Canvas(modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f - unit * 0.015f)
        val black = Color(0xFF080A09)
        val mint = Color(0xFF39D8AD)
        val blue = Color(0xFF64AFFF)
        val accent = if (state == EddyVisualState.THINKING) blue else mint

        val halo = unit * 0.445f
        drawCircle(accent.copy(alpha = 0.028f + 0.025f * pulse), halo * 1.20f, center)
        drawCircle(accent.copy(alpha = 0.055f), halo * 1.09f, center)
        drawCircle(accent.copy(alpha = 0.44f), halo, center, style = Stroke((unit * 0.0034f).coerceAtLeast(1.8f)))

        val angle = Math.toRadians(orbit.toDouble())
        drawCircle(
            accent,
            unit * 0.0105f * pulse,
            Offset(
                center.x + cos(angle).toFloat() * halo,
                center.y + sin(angle).toFloat() * halo,
            ),
        )

        // Mascota EDDY basada en el logo aprobado: una gran E humana, limpia y amigable.
        val w = unit * 0.66f
        val h = unit * 0.70f
        val left = center.x - w / 2f
        val right = center.x + w / 2f
        val top = center.y - h / 2f
        val bottom = center.y + h / 2f
        val outerStroke = (unit * 0.055f).coerceAtLeast(12f)
        val detailStroke = (unit * 0.021f).coerceAtLeast(5f)

        val shell = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + h * 0.30f)
            cubicTo(
                left, top + h * 0.10f,
                left + w * 0.15f, top,
                left + w * 0.39f, top,
            )
            cubicTo(
                left + w * 0.69f, top,
                right, top + h * 0.08f,
                right, top + h * 0.29f,
            )
        }
        drawPath(shell, black, style = Stroke(outerStroke, cap = StrokeCap.Butt))
        drawLine(black, Offset(left, bottom), Offset(right, bottom), outerStroke, StrokeCap.Butt)

        val middleY = center.y + h * 0.08f
        drawLine(black, Offset(left, middleY), Offset(right * 0.985f, middleY), outerStroke * 0.72f, StrokeCap.Butt)

        // Ojos cerrados, simétricos y suaves.
        val eyeY = center.y - h * 0.17f
        val eyeW = w * 0.20f
        val eyeH = h * 0.095f
        listOf(center.x - w * 0.17f, center.x + w * 0.17f).forEach { x ->
            drawArc(
                black,
                180f,
                180f,
                false,
                Offset(x - eyeW / 2f, eyeY - eyeH / 2f),
                Size(eyeW, eyeH),
                style = Stroke(detailStroke, cap = StrokeCap.Butt),
            )
            drawLine(
                black,
                Offset(x - eyeW / 2f, eyeY),
                Offset(x + eyeW / 2f, eyeY),
                detailStroke,
                StrokeCap.Butt,
            )
        }

        // Sonrisa ancha y limpia.
        val mouth = Rect(
            center.x - w * 0.17f,
            center.y - h * 0.055f,
            center.x + w * 0.17f,
            center.y + h * 0.105f,
        )
        drawLine(black, Offset(mouth.left, mouth.top), Offset(mouth.right, mouth.top), detailStroke, StrokeCap.Butt)
        drawArc(
            black,
            0f,
            180f,
            false,
            mouth.topLeft,
            mouth.size,
            style = Stroke(detailStroke, cap = StrokeCap.Butt),
        )

        // Corbata geométrica centrada bajo la barra media.
        val tieTop = middleY + outerStroke * 0.30f
        val knot = center.y + h * 0.31f
        val tieBottom = bottom - outerStroke * 0.48f
        val tie = Path().apply {
            moveTo(center.x - w * 0.16f, tieTop)
            lineTo(center.x, knot)
            lineTo(center.x + w * 0.16f, tieTop)
            moveTo(center.x, knot)
            lineTo(center.x, tieBottom)
        }
        drawPath(tie, black, style = Stroke(detailStroke, cap = StrokeCap.Butt))
    }
}
