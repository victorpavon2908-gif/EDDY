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
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 560
                    EddyVisualState.THINKING -> 780
                    EddyVisualState.SPEAKING -> 400
                    EddyVisualState.IDLE -> 2_300
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
                durationMillis = when (state) {
                    EddyVisualState.THINKING -> 1_100
                    EddyVisualState.LISTENING -> 2_100
                    EddyVisualState.SPEAKING -> 1_500
                    EddyVisualState.IDLE -> 5_000
                },
            ),
            RepeatMode.Restart,
        ),
        label = "orbit",
    )

    Canvas(modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f + unit * 0.015f)
        val black = Color(0xFF090A0A)
        val mint = Color(0xFF42DDB4)
        val blue = Color(0xFF66AEFF)
        val accent = if (state == EddyVisualState.THINKING) blue else mint

        // Halo muy limpio, como en el mockup aprobado.
        val halo = unit * 0.485f
        drawCircle(accent.copy(alpha = 0.025f + 0.025f * pulse), halo * 1.16f, center)
        drawCircle(accent.copy(alpha = 0.055f), halo * 1.06f, center)
        drawCircle(
            color = accent.copy(alpha = 0.40f),
            radius = halo,
            center = center,
            style = Stroke(width = (unit * 0.0035f).coerceAtLeast(1.5f)),
        )

        val angle = Math.toRadians(orbit.toDouble())
        drawCircle(
            color = accent,
            radius = unit * 0.010f * pulse,
            center = Offset(
                x = center.x + cos(angle).toFloat() * halo,
                y = center.y + sin(angle).toFloat() * halo,
            ),
        )

        // EDDY ocupa casi todo el interior del halo.
        val w = unit * 0.78f
        val h = unit * 0.78f
        val left = center.x - w / 2f
        val right = center.x + w / 2f
        val top = center.y - h / 2f
        val bottom = center.y + h / 2f
        val outerStroke = (unit * 0.053f).coerceAtLeast(11f)
        val detailStroke = (unit * 0.0175f).coerceAtLeast(4.5f)

        // Forma exterior: gran E redondeada y abierta a la derecha.
        val shell = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + h * 0.28f)
            cubicTo(
                left, top + h * 0.10f,
                left + w * 0.13f, top,
                left + w * 0.36f, top,
            )
            cubicTo(
                left + w * 0.68f, top,
                right, top + h * 0.08f,
                right, top + h * 0.29f,
            )
        }
        drawPath(
            path = shell,
            color = black,
            style = Stroke(width = outerStroke, cap = StrokeCap.Butt),
        )
        drawLine(
            color = black,
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            strokeWidth = outerStroke,
            cap = StrokeCap.Butt,
        )

        // Barra central de la E.
        val middleY = center.y + h * 0.085f
        drawLine(
            color = black,
            start = Offset(left, middleY),
            end = Offset(right - w * 0.015f, middleY),
            strokeWidth = outerStroke * 0.68f,
            cap = StrokeCap.Butt,
        )

        // Ojos semicerrados, más grandes y amistosos.
        val eyeY = center.y - h * 0.18f
        val eyeW = w * 0.205f
        val eyeH = h * 0.105f
        listOf(center.x - w * 0.17f, center.x + w * 0.17f).forEach { x ->
            drawArc(
                color = black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(x - eyeW / 2f, eyeY - eyeH / 2f),
                size = Size(eyeW, eyeH),
                style = Stroke(width = detailStroke, cap = StrokeCap.Butt),
            )
            drawLine(
                color = black,
                start = Offset(x - eyeW / 2f, eyeY),
                end = Offset(x + eyeW / 2f, eyeY),
                strokeWidth = detailStroke,
                cap = StrokeCap.Butt,
            )
        }

        // Sonrisa abierta inspirada directamente en la referencia.
        val mouth = Rect(
            left = center.x - w * 0.17f,
            top = center.y - h * 0.055f,
            right = center.x + w * 0.17f,
            bottom = center.y + h * 0.105f,
        )
        drawLine(
            color = black,
            start = Offset(mouth.left, mouth.top),
            end = Offset(mouth.right, mouth.top),
            strokeWidth = detailStroke,
            cap = StrokeCap.Butt,
        )
        drawArc(
            color = black,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = mouth.topLeft,
            size = mouth.size,
            style = Stroke(width = detailStroke, cap = StrokeCap.Butt),
        )

        // Corbata limpia, sin adornos extra.
        val tieTop = middleY + outerStroke * 0.28f
        val knot = center.y + h * 0.30f
        val tieBottom = bottom - outerStroke * 0.46f
        val tie = Path().apply {
            moveTo(center.x - w * 0.15f, tieTop)
            lineTo(center.x, knot)
            lineTo(center.x + w * 0.15f, tieTop)
            moveTo(center.x, knot)
            lineTo(center.x, tieBottom)
        }
        drawPath(
            path = tie,
            color = black,
            style = Stroke(width = detailStroke, cap = StrokeCap.Butt),
        )
    }
}
