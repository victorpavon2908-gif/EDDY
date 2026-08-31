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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Identidad visual minimalista de EDDY.
 *
 * El personaje se dibuja directamente con Canvas para conservar nitidez a cualquier
 * resolución y evitar agregar imágenes pesadas al APK. La forma sigue la identidad de
 * una E geométrica con rostro y corbata, mientras el halo comunica estado de escucha.
 */
@Composable
internal fun EddyHero(
    state: EddyVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyMinimalHero")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 720
                    EddyVisualState.THINKING -> 920
                    EddyVisualState.SPEAKING -> 520
                    EddyVisualState.IDLE -> 2_400
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
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 2_500
                    EddyVisualState.THINKING -> 1_300
                    EddyVisualState.SPEAKING -> 1_900
                    EddyVisualState.IDLE -> 5_200
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f - unit * 0.01f)
        val graphite = Color(0xFF0A0B0B)
        val mint = Color(0xFF35D7AA)
        val blue = Color(0xFF58A8FF)
        val accent = when (state) {
            EddyVisualState.THINKING -> blue
            else -> mint
        }

        val haloRadius = unit * 0.44f
        drawCircle(
            color = accent.copy(alpha = 0.035f + 0.035f * pulse),
            radius = haloRadius * 1.18f,
            center = center,
        )
        drawCircle(
            color = accent.copy(alpha = 0.09f),
            radius = haloRadius * 1.06f,
            center = center,
        )
        drawCircle(
            color = accent.copy(alpha = 0.40f),
            radius = haloRadius,
            center = center,
            style = Stroke(width = (unit * 0.004f).coerceAtLeast(2f)),
        )

        val angle = Math.toRadians(orbit.toDouble())
        val node = Offset(
            center.x + cos(angle).toFloat() * haloRadius,
            center.y + sin(angle).toFloat() * haloRadius,
        )
        drawCircle(accent, unit * 0.012f * pulse, node)

        // EDDY ocupa la mayor parte del hero, tal como en la referencia aprobada.
        val w = unit * 0.52f
        val h = unit * 0.62f
        val left = center.x - w / 2f
        val right = center.x + w / 2f
        val top = center.y - h / 2f
        val bottom = center.y + h / 2f
        val stroke = (unit * 0.052f).coerceAtLeast(11f)
        val detail = (unit * 0.020f).coerceAtLeast(5f)

        // Trazo exterior tipo E: arco superior + columna izquierda + base inferior.
        val arch = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + h * 0.30f)
            cubicTo(
                left, top + h * 0.07f,
                left + w * 0.18f, top,
                left + w * 0.42f, top,
            )
            cubicTo(
                left + w * 0.72f, top,
                right, top + h * 0.08f,
                right, top + h * 0.31f,
            )
        }
        drawPath(arch, graphite, style = Stroke(width = stroke, cap = StrokeCap.Butt))
        drawLine(graphite, Offset(left, bottom), Offset(right, bottom), stroke, StrokeCap.Butt)

        // Barra central de la E, también funciona como hombros.
        val midY = center.y + h * 0.10f
        drawLine(graphite, Offset(left, midY), Offset(right * 0.98f, midY), stroke * 0.78f, StrokeCap.Butt)

        // Ojos semicirculares cerrados/amables.
        val eyeY = center.y - h * 0.15f
        val eyeW = w * 0.19f
        val eyeH = h * 0.10f
        listOf(center.x - w * 0.16f, center.x + w * 0.16f).forEach { eyeX ->
            drawArc(
                color = graphite,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(eyeX - eyeW / 2f, eyeY - eyeH / 2f),
                size = androidx.compose.ui.geometry.Size(eyeW, eyeH),
                style = Stroke(width = detail, cap = StrokeCap.Butt),
            )
            drawLine(
                graphite,
                Offset(eyeX - eyeW / 2f, eyeY),
                Offset(eyeX + eyeW / 2f, eyeY),
                detail,
                StrokeCap.Butt,
            )
        }

        // Sonrisa geométrica abierta.
        val mouthRect = Rect(
            left = center.x - w * 0.15f,
            top = center.y - h * 0.045f,
            right = center.x + w * 0.15f,
            bottom = center.y + h * 0.105f,
        )
        drawArc(
            color = graphite,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = mouthRect.topLeft,
            size = mouthRect.size,
            style = Stroke(width = detail, cap = StrokeCap.Butt),
        )
        drawLine(
            graphite,
            Offset(mouthRect.left, mouthRect.top),
            Offset(mouthRect.right, mouthRect.top),
            detail,
            StrokeCap.Butt,
        )

        // Corbata minimalista.
        val tieTopY = midY + stroke * 0.34f
        val tieKnotY = center.y + h * 0.31f
        val tieBottomY = bottom - stroke * 0.48f
        val tie = Path().apply {
            moveTo(center.x - w * 0.16f, tieTopY)
            lineTo(center.x, tieKnotY)
            lineTo(center.x + w * 0.16f, tieTopY)
            moveTo(center.x, tieKnotY)
            lineTo(center.x, tieBottomY)
        }
        drawPath(tie, graphite, style = Stroke(width = detail, cap = StrokeCap.Butt))
    }
}
