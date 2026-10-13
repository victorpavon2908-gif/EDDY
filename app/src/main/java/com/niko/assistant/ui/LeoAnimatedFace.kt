package com.niko.assistant.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin

/** Procedural face in the reference emblem's coordinates; no bitmap stretching or 3D runtime. */
@Composable
internal fun LeoAnimatedFace(
    state: NikoVisualState,
    enabled: Boolean,
    input: String,
    reply: String,
    modifier: Modifier = Modifier,
) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var elapsed by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf(LeoFaceMood.FRIENDLY) }
    val active = enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val expressive = active && state != NikoVisualState.THINKING && state != NikoVisualState.LISTENING
    LaunchedEffect(input, reply, enabled) {
        mood = if (enabled) LeoFaceMood.resolve(input, reply) else LeoFaceMood.FRIENDLY
        delay(8000)
        mood = LeoFaceMood.FRIENDLY
    }
    LaunchedEffect(active, state, mood) {
        elapsed = 0f
        if (!active || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        if (state == NikoVisualState.SPEAKING || (expressive && mood == LeoFaceMood.LAUGHING)) {
            val start = withFrameNanos { it }
            while (isActive) {
                elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
                delay(32) // Only the visible face updates, bounded to approximately 30 frames/s.
            }
        }
    }
    LaunchedEffect(active) {
        blink = false
        if (!active || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        while (isActive) {
            delay(3800)
            blink = true
            delay(130)
            blink = false
        }
    }
    Canvas(modifier) {
        val scale = minOf(size.width / 360f, size.height / 341f)
        withTransform({
            translate((size.width - 360f * scale) / 2f, (size.height - 341f * scale) / 2f)
            scale(scale, scale, Offset.Zero)
        }) {
            val ink = Color.Black
            val line = Stroke(6f, cap = StrokeCap.Round)
            val body = Path().apply {
                moveTo(82f, 318f); lineTo(82f, 115f)
                cubicTo(82f, -20f, 286f, -20f, 286f, 115f)
                lineTo(260f, 115f); cubicTo(260f, 15f, 109f, 15f, 109f, 115f)
                lineTo(109f, 199f); lineTo(252f, 199f); lineTo(252f, 220f)
                lineTo(109f, 220f); lineTo(109f, 295f); lineTo(274f, 295f)
                lineTo(274f, 318f); close()
            }
            drawPath(body, ink)
            drawPath(Path().apply { moveTo(139f, 219f); lineTo(183f, 270f); lineTo(225f, 219f); moveTo(183f, 270f); lineTo(183f, 298f) }, ink, style = line)
            val face = if (expressive) mood else LeoFaceMood.FRIENDLY
            val laughing = face == LeoFaceMood.LAUGHING
            val surprised = face == LeoFaceMood.SURPRISED
            val wry = face == LeoFaceMood.WRY
            val caring = face == LeoFaceMood.CARING
            for (i in 0..1) {
                val x = if (i == 0) 147f else 219f
                val shut = blink || laughing || (wry && i == 1)
                if (shut) {
                    drawPath(Path().apply { moveTo(x - 19f, 112f); quadraticBezierTo(x, if (laughing) 92f else 114f, x + 19f, 110f) }, ink, style = line)
                } else {
                    val height = 30f // Keep the same hollow eye outline in every expression.
                    drawOval(ink, Offset(x - 20f, 113f - height), Size(40f, height), style = line)
                }
                if (wry || surprised || caring || state == NikoVisualState.THINKING) {
                    drawLine(ink, Offset(x - 18f, if (wry && i == 0) 70f else 77f), Offset(x + 18f, if (caring && i == 0) 68f else 72f), 5f, StrokeCap.Round)
                }
            }
            val speaking = active && state == NikoVisualState.SPEAKING
            // Rhythm follows speaking state, not phonemes or measured audio amplitude.
            val opening = if (speaking) 7f + abs(sin(elapsed * 13f)) * 25f else if (laughing) 29f + abs(sin(elapsed * 9f)) * 9f else 0f
            when {
                surprised -> drawOval(ink, Offset(170f, 140f), Size(27f, 35f + opening * .3f), style = line)
                wry && !speaking -> drawPath(Path().apply { moveTo(154f, 158f); quadraticBezierTo(185f, 180f, 219f, 143f) }, ink, style = line)
                caring && !speaking -> drawPath(Path().apply { moveTo(159f, 160f); quadraticBezierTo(184f, 154f, 210f, 160f) }, ink, style = line)
                opening > 0f -> {
                    drawOval(ink, Offset(150f, 142f), Size(69f, opening + 12f))
                    drawRoundRect(Color.White, Offset(159f, 144f), Size(50f, 7f), androidx.compose.ui.geometry.CornerRadius(2f))
                }
                else -> drawPath(Path().apply { moveTo(149f, 144f); lineTo(219f, 144f); cubicTo(219f, 197f, 149f, 197f, 149f, 144f); close() }, ink, style = line)
            }
        }
    }
}
