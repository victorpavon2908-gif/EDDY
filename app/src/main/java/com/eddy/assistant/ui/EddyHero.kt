package com.eddy.assistant.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun EddyHero(
    state: EddyVisualState,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyHero")

    val pulse by transition.animateFloat(
        initialValue = 0.992f,
        targetValue = when (state) {
            EddyVisualState.LISTENING -> 1.022f
            EddyVisualState.THINKING -> 1.016f
            EddyVisualState.SPEAKING -> 1.026f
            EddyVisualState.IDLE -> 1.008f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 680
                    EddyVisualState.THINKING -> 860
                    EddyVisualState.SPEAKING -> 440
                    EddyVisualState.IDLE -> 1900
                }
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    val orbitPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == EddyVisualState.THINKING) 980 else 4200,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit",
    )

    val mouthMotion by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 170),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mouth",
    )

    val waveMotion by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == EddyVisualState.SPEAKING) 190 else 520,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wave",
    )

    val blinkFactor = when {
        blinkPhase in 0.80f..0.86f -> 0.08f
        blinkPhase in 0.89f..0.94f -> 0.16f
        else -> 1f
    }

    Box(
        modifier = modifier.graphicsLayer(
            scaleX = pulse,
            scaleY = pulse,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val center = Offset(w * 0.50f, h * 0.49f)
            val fieldRadius = minDim * 0.43f
            val accent = when (state) {
                EddyVisualState.LISTENING -> EddyMint
                EddyVisualState.THINKING -> EddyBlue
                EddyVisualState.SPEAKING -> EddyMintDeep
                EddyVisualState.IDLE -> EddyMint
            }

            drawCircle(
                color = accent.copy(alpha = 0.055f),
                radius = fieldRadius * 0.94f,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.30f),
                radius = fieldRadius,
                center = center,
                style = Stroke(width = max(1.4f, minDim * 0.0026f)),
            )
            drawCircle(
                color = EddySoftGray.copy(alpha = 0.74f),
                radius = fieldRadius * 0.82f,
                center = center,
                style = Stroke(width = max(1f, minDim * 0.0017f)),
            )
            drawArc(
                color = accent.copy(alpha = 0.54f),
                startAngle = 210f,
                sweepAngle = 78f,
                useCenter = false,
                topLeft = Offset(center.x - fieldRadius * 0.92f, center.y - fieldRadius * 0.92f),
                size = Size(fieldRadius * 1.84f, fieldRadius * 1.84f),
                style = Stroke(width = max(1.8f, minDim * 0.0042f), cap = StrokeCap.Round),
            )
            drawArc(
                color = EddySoftGray.copy(alpha = 0.72f),
                startAngle = 322f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - fieldRadius * 0.70f, center.y - fieldRadius * 0.70f),
                size = Size(fieldRadius * 1.40f, fieldRadius * 1.40f),
                style = Stroke(width = max(1f, minDim * 0.0014f)),
            )

            val orbitRadius = fieldRadius * 0.96f
            val angle = Math.toRadians((orbitPhase * 360f).toDouble())
            val movingDot = Offset(
                x = center.x + (kotlin.math.cos(angle) * orbitRadius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * orbitRadius).toFloat(),
            )
            drawCircle(accent.copy(alpha = 0.16f), radius = max(9f, minDim * 0.020f), center = movingDot)
            drawCircle(accent, radius = max(4f, minDim * 0.009f), center = movingDot)
            drawCircle(EddyMint, radius = max(3.5f, minDim * 0.008f), center = Offset(center.x, center.y - orbitRadius))
            drawCircle(EddyMint.copy(alpha = 0.84f), radius = max(3.5f, minDim * 0.008f), center = Offset(center.x - orbitRadius, center.y + fieldRadius * 0.10f))
            drawCircle(EddyMint.copy(alpha = 0.84f), radius = max(3.5f, minDim * 0.008f), center = Offset(center.x + orbitRadius, center.y + fieldRadius * 0.10f))

            val waveY = center.y + fieldRadius * 0.03f
            val waveColor = when (state) {
                EddyVisualState.SPEAKING -> accent.copy(alpha = 0.74f)
                EddyVisualState.LISTENING -> accent.copy(alpha = 0.58f)
                EddyVisualState.THINKING -> accent.copy(alpha = 0.48f)
                EddyVisualState.IDLE -> EddyGraphite.copy(alpha = 0.20f)
            }
            val baseHeights = floatArrayOf(0.16f, 0.30f, 0.46f, 0.25f, 0.18f, 0.34f, 0.22f, 0.52f, 0.31f, 0.16f)
            val gap = minDim * 0.026f
            val maxWaveHeight = fieldRadius * 0.27f
            val startLeft = center.x - fieldRadius * 0.90f
            val startRight = center.x + fieldRadius * 0.62f

            baseHeights.forEachIndexed { index, factor ->
                val animated = factor * (0.70f + waveMotion * 0.30f)
                val barHeight = maxWaveHeight * animated
                val xLeft = startLeft + gap * index
                val xRight = startRight + gap * index
                drawLine(
                    color = waveColor,
                    start = Offset(xLeft, waveY - barHeight / 2f),
                    end = Offset(xLeft, waveY + barHeight / 2f),
                    strokeWidth = max(2f, minDim * 0.006f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = waveColor,
                    start = Offset(xRight, waveY - barHeight / 2f),
                    end = Offset(xRight, waveY + barHeight / 2f),
                    strokeWidth = max(2f, minDim * 0.006f),
                    cap = StrokeCap.Round,
                )
            }

            val avatarWidth = minDim * 0.58f
            val avatarHeight = minDim * 0.78f
            val left = center.x - avatarWidth * 0.50f
            val right = center.x + avatarWidth * 0.50f
            val top = center.y - avatarHeight * 0.48f
            val bottom = center.y + avatarHeight * 0.48f
            val mainStroke = max(13f, minDim * 0.050f)
            val faceStroke = mainStroke * 0.47f

            drawLine(
                color = EddyBlack,
                start = Offset(left, top + mainStroke * 0.45f),
                end = Offset(left, bottom),
                strokeWidth = mainStroke,
                cap = StrokeCap.Butt,
            )
            drawArc(
                color = EddyBlack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(right - left, avatarHeight * 0.29f),
                style = Stroke(width = mainStroke, cap = StrokeCap.Butt, join = StrokeJoin.Round),
            )

            val middleY = center.y + avatarHeight * 0.07f
            drawLine(
                color = EddyBlack,
                start = Offset(left, middleY),
                end = Offset(right * 0.985f, middleY),
                strokeWidth = mainStroke,
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = EddyBlack,
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = mainStroke,
                cap = StrokeCap.Butt,
            )

            val eyeY = center.y - avatarHeight * 0.19f
            val eyeW = avatarWidth * 0.20f
            val eyeH = max(avatarHeight * 0.050f * blinkFactor, 2f)
            val eyeStyle = Stroke(width = faceStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawArc(
                color = EddyBlack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - avatarWidth * 0.25f, eyeY),
                size = Size(eyeW, eyeH),
                style = eyeStyle,
            )
            drawArc(
                color = EddyBlack,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x + avatarWidth * 0.05f, eyeY),
                size = Size(eyeW, eyeH),
                style = eyeStyle,
            )

            val mouthLeft = center.x - avatarWidth * 0.18f
            val mouthRight = center.x + avatarWidth * 0.18f
            val mouthTop = center.y - avatarHeight * 0.05f
            val mouthHeight = if (state == EddyVisualState.SPEAKING) {
                avatarHeight * (0.040f + mouthMotion * 0.075f)
            } else {
                avatarHeight * 0.095f
            }
            drawLine(
                color = EddyBlack,
                start = Offset(mouthLeft, mouthTop),
                end = Offset(mouthRight, mouthTop),
                strokeWidth = faceStroke,
                cap = StrokeCap.Butt,
            )
            drawArc(
                color = EddyBlack,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthLeft, mouthTop - mouthHeight * 0.02f),
                size = Size(mouthRight - mouthLeft, mouthHeight),
                style = Stroke(width = faceStroke, cap = StrokeCap.Round),
            )

            val yTop = middleY + mainStroke * 0.12f
            val yCenter = center.y + avatarHeight * 0.28f
            drawLine(
                color = EddyBlack,
                start = Offset(center.x - avatarWidth * 0.18f, yTop),
                end = Offset(center.x, yCenter),
                strokeWidth = faceStroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = EddyBlack,
                start = Offset(center.x + avatarWidth * 0.18f, yTop),
                end = Offset(center.x, yCenter),
                strokeWidth = faceStroke,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = EddyBlack,
                start = Offset(center.x, yCenter),
                end = Offset(center.x, bottom),
                strokeWidth = faceStroke,
                cap = StrokeCap.Square,
            )

            if (state == EddyVisualState.THINKING) {
                drawArc(
                    color = accent,
                    startAngle = orbitPhase * 360f,
                    sweepAngle = 48f,
                    useCenter = false,
                    topLeft = Offset(center.x - fieldRadius, center.y - fieldRadius),
                    size = Size(fieldRadius * 2f, fieldRadius * 2f),
                    style = Stroke(width = max(2.2f, minDim * 0.0065f), cap = StrokeCap.Round),
                )
            }
        }
    }
}
