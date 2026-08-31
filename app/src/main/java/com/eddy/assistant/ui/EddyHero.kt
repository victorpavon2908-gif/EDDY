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
 * EDDY minimalista: gran E antropomórfica, sonrisa simple y corbata geométrica.
 * El halo mint comunica presencia sin llenar la pantalla de controles.
 */
@Composable
internal fun EddyHero(
    state: EddyVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyHero")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (state) {
                    EddyVisualState.LISTENING -> 680
                    EddyVisualState.THINKING -> 860
                    EddyVisualState.SPEAKING -> 480
                    EddyVisualState.IDLE -> 2_200
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
                when (state) {
                    EddyVisualState.THINKING -> 1_350
                    EddyVisualState.LISTENING -> 2_450
                    EddyVisualState.SPEAKING -> 1_900
                    EddyVisualState.IDLE -> 5_400
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    Canvas(modifier.fillMaxSize()) {
        val unit = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val ink = Color(0xFF101111)
        val mint = Color(0xFF35D7AA)
        val blue = Color(0xFF5AA7FF)
        val accent = if (state == EddyVisualState.THINKING) blue else mint

        // Halo amplio como en la referencia aprobada.
        val halo = unit * 0.47f
        drawCircle(accent.copy(alpha = 0.030f + 0.035f * pulse), halo * 1.16f, center)
        drawCircle(accent.copy(alpha = 0.055f), halo * 1.07f, center)
        drawCircle(
            accent.copy(alpha = 0.42f),
            halo,
            center,
            style = Stroke(width = (unit * 0.0045f).coerceAtLeast(2f)),
        )

        val angle = Math.toRadians(orbit.toDouble())
        val node = Offset(
            center.x + cos(angle).toFloat() * halo,
            center.y + sin(angle).toFloat() * halo,
        )
        drawCircle(accent, unit * 0.014f * pulse, node)

        // Personaje deliberadamente grande: ocupa ~74% del ancho útil.
        val w = unit * 0.72f
        val h = unit * 0.80f
        val left = center.x - w / 2f
        val right = center.x + w / 2f
        val top = center.y - h / 2f
        val bottom = center.y + h / 2f
        val bodyStroke = (unit * 0.058f).coerceAtLeast(12f)
        val detail = (unit * 0.018f).coerceAtLeast(5f)

        // Arco superior + columna izquierda.
        val body = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + h * 0.29f)
            cubicTo(
                left, top + h * 0.07f,
                left + w * 0.18f, top,
                left + w * 0.42f, top,
            )
            cubicTo(
                left + w * 0.73f, top,
                right, top + h * 0.08f,
                right, top + h * 0.30f,
            )
        }
        drawPath(body, ink, style = Stroke(width = bodyStroke, cap = StrokeCap.Butt))

        // Base inferior de la E.
        drawLine(
            ink,
            Offset(left, bottom),
            Offset(right * 0.995f, bottom),
            bodyStroke,
            StrokeCap.Butt,
        )

        // Barra central/hombros.
        val shoulderY = center.y + h * 0.105f
        drawLine(
            ink,
            Offset(left, shoulderY),
            Offset(right * 0.995f, shoulderY),
            bodyStroke * 0.72f,
            StrokeCap.Butt,
        )

        // Ojos semicirculares relajados.
        val eyeY = center.y - h * 0.16f
        val eyeW = w * 0.20f
        val eyeH = h * 0.095f
        listOf(center.x - w * 0.17f, center.x + w * 0.17f).forEach { eyeX ->
            drawArc(
                ink,
                180f,
                180f,
                false,
                Offset(eyeX - eyeW / 2f, eyeY - eyeH / 2f),
                androidx.compose.ui.geometry.Size(eyeW, eyeH),
                style = Stroke(width = detail, cap = StrokeCap.Butt),
            )
            drawLine(
                ink,
                Offset(eyeX - eyeW / 2f, eyeY),
                Offset(eyeX + eyeW / 2f, eyeY),
                detail,
                StrokeCap.Butt,
            )
        }

        // Sonrisa abierta, grande y limpia.
        val mouth = Rect(
            center.x - w * 0.16f,
            center.y - h * 0.045f,
            center.x + w * 0.16f,
            center.y + h * 0.12f,
        )
        drawArc(
            ink,
            0f,
            180f,
            false,
            mouth.topLeft,
            mouth.size,
            style = Stroke(width = detail * 1.06f, cap = StrokeCap.Butt),
        )
        drawLine(
            ink,
            Offset(mouth.left, mouth.top),
            Offset(mouth.right, mouth.top),
            detail * 1.06f,
            StrokeCap.Butt,
        )

        // Corbata Y minimalista.
        val tieTop = shoulderY + bodyStroke * 0.34f
        val knot = center.y + h * 0.30f
        val tieBottom = bottom - bodyStroke * 0.48f
        val tie = Path().apply {
            moveTo(center.x - w * 0.17f, tieTop)
            lineTo(center.x, knot)
            lineTo(center.x + w * 0.17f, tieTop)
            moveTo(center.x, knot)
            lineTo(center.x, tieBottom)
        }
        drawPath(tie, ink, style = Stroke(width = detail, cap = StrokeCap.Butt))
    }
}
