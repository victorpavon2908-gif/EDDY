package com.eddy.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eddy.assistant.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hero visual PRO. El personaje ya no se calcula con geometría dependiente del Canvas:
 * usa un vector fijo y escalable para mantener exactamente las mismas proporciones en
 * teléfonos pequeños y grandes. El Canvas queda únicamente para el halo de estado.
 */
@Composable
internal fun EddyHero(
    state: EddyVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyProHero")
    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 520
                    EddyVisualState.THINKING -> 760
                    EddyVisualState.SPEAKING -> 390
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
                    EddyVisualState.THINKING -> 1_050
                    EddyVisualState.LISTENING -> 1_900
                    EddyVisualState.SPEAKING -> 1_450
                    EddyVisualState.IDLE -> 5_200
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val accent = if (state == EddyVisualState.THINKING) Color(0xFF6EAEFF) else Color(0xFF42DDB4)
            val halo = unit * 0.465f

            drawCircle(
                color = accent.copy(alpha = 0.035f + 0.025f * pulse),
                radius = halo * 1.15f,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.065f),
                radius = halo * 1.045f,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.42f),
                radius = halo,
                center = center,
                style = Stroke(width = (unit * 0.0038f).coerceAtLeast(1.7f)),
            )

            val radians = Math.toRadians(orbit.toDouble())
            drawCircle(
                color = accent,
                radius = unit * 0.0115f * pulse,
                center = Offset(
                    x = center.x + cos(radians).toFloat() * halo,
                    y = center.y + sin(radians).toFloat() * halo,
                ),
            )
        }

        Image(
            painter = painterResource(R.drawable.ic_eddy_mascot_pro),
            contentDescription = "EDDY",
            modifier = Modifier.size(width = 300.dp, height = 338.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
