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
import kotlin.math.cos
import kotlin.math.sin

/** Procedural face in the reference emblem's coordinates with organic micro-expressions; no bitmap stretching or 3D runtime. */
@Composable
internal fun LeoAnimatedFace(
    state: NikoVisualState,
    enabled: Boolean,
    input: String,
    reply: String,
    modifier: Modifier = Modifier,
) {
    val mode = rememberLeoMode()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var elapsed by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf(LeoFaceMood.FRIENDLY) }
    val active = enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val expressive = active && state != NikoVisualState.THINKING && state != NikoVisualState.LISTENING
    LaunchedEffect(input, reply, enabled, mode) {
        mood = if (enabled) LeoFaceMood.forMode(mode, input, reply) else LeoFaceMood.FRIENDLY
        delay(8000)
        mood = if (mode.serious) LeoFaceMood.SERIOUS else LeoFaceMood.FRIENDLY
    }
    LaunchedEffect(active, state, mood) {
        elapsed = 0f
        if (!active || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        val animateLoop = state == NikoVisualState.SPEAKING ||
            state == NikoVisualState.LISTENING ||
            state == NikoVisualState.THINKING ||
            (expressive && mood == LeoFaceMood.LAUGHING)
        if (animateLoop) {
            val start = withFrameNanos { it }
            while (isActive) {
                elapsed = withFrameNanos { (it - start) / 1_000_000_000f }
                delay(32) // Bounded to ~30 fps only while actively listening, thinking, speaking, or laughing.
            }
        }
    }
    LaunchedEffect(active) {
        blink = false
        if (!active || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        var cycle = 0
        while (isActive) {
            val waitMs = when (cycle % 4) {
                0 -> 3400L
                1 -> 4600L
                2 -> 2800L
                else -> 4100L
            }
            delay(waitMs)
            blink = true
            delay(120)
            blink = false
            // Occasional natural double-blink
            if (cycle % 5 == 3) {
                delay(150)
                blink = true
                delay(105)
                blink = false
            }
            cycle++
        }
    }
    Canvas(modifier) {
        val scale = minOf(size.width / 360f, size.height / 341f)
        val listeningBob = if (active && state == NikoVisualState.LISTENING) sin(elapsed * 3.2f) * 2.2f else 0f
        val thinkingTiltX = if (active && state == NikoVisualState.THINKING) sin(elapsed * 2.4f) * 3.5f else 0f
        withTransform({
            translate((size.width - 360f * scale) / 2f, (size.height - 341f * scale) / 2f + listeningBob * scale)
            scale(scale, scale, Offset.Zero)
        }) {
            val ink = if (mode == com.niko.assistant.modes.LeoMode.NORMAL) Color.Black else Color(mode.ink)
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
            val face = if (mode.serious) LeoFaceMood.SERIOUS else if (expressive) mood else LeoFaceMood.FRIENDLY
            val serious = face == LeoFaceMood.SERIOUS
            val laughing = face == LeoFaceMood.LAUGHING
            val surprised = face == LeoFaceMood.SURPRISED
            val wry = face == LeoFaceMood.WRY
            val caring = face == LeoFaceMood.CARING
            val afraid = face == LeoFaceMood.AFRAID
            val angry = face == LeoFaceMood.ANGRY
            val sad = face == LeoFaceMood.SAD
            val listeningPulse = if (active && state == NikoVisualState.LISTENING) (1f + sin(elapsed * 4.5f)) * 1.6f else 0f
            for (i in 0..1) {
                val x = (if (i == 0) 148.5f else 219.5f) + thinkingTiltX * 0.45f
                val shut = blink || laughing || (wry && i == 1)
                if (shut) {
                    drawPath(Path().apply { moveTo(x - 19f, 112f); quadraticBezierTo(x, if (laughing) 92f else 114f, x + 19f, 110f) }, ink, style = line)
                } else {
                    // Reference eyes: an upper arch closed by a straight horizontal base.
                    // Keep this silhouette for every open-eye expression, without pupils.
                    drawPath(Path().apply {
                        moveTo(x - 23.5f, 117f)
                        val crown = (if (serious) 101f else 91f) - listeningPulse
                        val shoulder = (if (serious) 108f else 102.64f) - listeningPulse * 0.5f
                        cubicTo(x - 23.5f, shoulder, x - 12.98f, crown, x, crown)
                        cubicTo(x + 12.98f, crown, x + 23.5f, shoulder, x + 23.5f, 117f)
                        close()
                    }, ink, style = line)
                }
                if (serious) {
                    drawLine(ink, Offset(x - 20f, if (i == 0) 83f else 88f), Offset(x + 20f, if (i == 0) 88f else 83f), 5f, StrokeCap.Round)
                } else if (angry || afraid || sad) {
                    val inner = if (angry) 83f else 66f
                    val outer = if (angry) 66f else 80f
                    drawLine(ink, Offset(x - 18f, if (i == 0) outer else inner), Offset(x + 18f, if (i == 0) inner else outer), 5f, StrokeCap.Round)
                } else if (wry || surprised || caring || state == NikoVisualState.THINKING) {
                    val thinkLift = if (state == NikoVisualState.THINKING) cos(elapsed * 2.4f) * 2.0f else 0f
                    drawLine(
                        ink,
                        Offset(x - 18f, (if (wry && i == 0) 70f else 77f) - thinkLift),
                        Offset(x + 18f, (if (caring && i == 0) 68f else 72f) + thinkLift * 0.5f),
                        5f,
                        StrokeCap.Round,
                    )
                } else if (state == NikoVisualState.LISTENING) {
                    // Subtle attentive raised brows while actively listening.
                    drawLine(ink, Offset(x - 16f, 75f - listeningPulse), Offset(x + 16f, 73f - listeningPulse), 4.5f, StrokeCap.Round)
                }
            }
            val speaking = active && state == NikoVisualState.SPEAKING
            // Organic multi-harmonic cadence during speech so lip motion feels lifelike.
            val speechEnvelope = if (speaking) {
                val harmonic = abs(sin(elapsed * 12.5f)) * 0.58f +
                    abs(sin(elapsed * 21.3f + 0.7f)) * 0.27f +
                    abs(sin(elapsed * 6.9f + 1.4f)) * 0.15f
                7f + harmonic * 27f
            } else if (laughing) {
                29f + abs(sin(elapsed * 9f)) * 9f
            } else 0f
            val opening = speechEnvelope
            when {
                serious && speaking -> drawOval(ink, Offset(161f, 149f), Size(47f, 8f + opening * .35f), style = line)
                serious -> drawLine(ink, Offset(157f, 158f), Offset(211f, 158f), 6f, StrokeCap.Round)
                speaking -> {
                    // Preserve the reference smile; vary only its lower curve during speech.
                    val depth = 8f + opening
                    drawPath(Path().apply {
                        moveTo(149f, 144f)
                        lineTo(219f, 144f)
                        cubicTo(219f, 144f + depth, 149f, 144f + depth, 149f, 144f)
                        close()
                    }, ink, style = line)
                }
                afraid -> drawPath(Path().apply {
                    moveTo(155f, 163f); lineTo(165f, 154f); lineTo(175f, 163f)
                    lineTo(185f, 154f); lineTo(195f, 163f); lineTo(210f, 154f)
                }, ink, style = line)
                angry || sad -> drawPath(Path().apply {
                    moveTo(156f, 167f); quadraticBezierTo(183f, if (sad) 139f else 153f, 211f, 167f)
                }, ink, style = line)
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
