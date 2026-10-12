package com.niko.assistant.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niko.assistant.R
import com.niko.assistant.ui.robot.RobotActivity
import com.niko.assistant.ui.robot.RobotMotion
import com.niko.assistant.ui.robot.RobotMotionBus
import com.niko.assistant.ui.robot.RobotMotionDirector
import kotlin.math.sin

/** The supplied emblem is displayed unmodified; only explicit gestures move the whole image. */
@Composable
internal fun NikoHero(state: NikoVisualState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val request by RobotMotionBus.requests.collectAsStateWithLifecycle()
    val duration: (String) -> Float = { when (it) {
        "Jump" -> .7f; "Dance" -> 1.6f; "Spin" -> 1.1f; else -> .8f
    } }
    val director = remember { RobotMotionDirector(duration) }
    var consumedRequest by remember { mutableStateOf<Long?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var lift by remember { mutableFloatStateOf(0f) }
    val jumpHeight = with(LocalDensity.current) { 24.dp.toPx() }
    val activity = when (state) {
        NikoVisualState.IDLE -> RobotActivity.IDLE
        NikoVisualState.LISTENING -> RobotActivity.LISTENING
        NikoVisualState.THINKING -> RobotActivity.THINKING
        NikoVisualState.SPEAKING -> RobotActivity.SPEAKING
    }
    val description = when (state) {
        NikoVisualState.IDLE -> if (enabled) "LEO está listo" else "LEO está en pausa"
        NikoVisualState.LISTENING -> "LEO te escucha"
        NikoVisualState.THINKING -> "LEO está pensando"
        NikoVisualState.SPEAKING -> "LEO está hablando"
    }
    // No render loop at rest. Stop a gesture on deactivation, backgrounding or a new voice state.
    LaunchedEffect(request, state, enabled, lifecycleState) {
        rotation = 0f
        lift = 0f
        director.cancelMotion()
        val active = enabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
        director.setActivity(activity, active, !ValueAnimator.areAnimatorsEnabled())
        val nextRequest = request ?: return@LaunchedEffect
        if (nextRequest.id == consumedRequest) return@LaunchedEffect
        consumedRequest = nextRequest.id
        val motion = nextRequest.motion
        if (!active || motion == null || state == NikoVisualState.LISTENING || state == NikoVisualState.THINKING) return@LaunchedEffect
        val start = withFrameNanos { it / 1_000_000_000.0 }
        director.perform(motion, start)
        while (director.hasMotion) {
            val now = withFrameNanos { it / 1_000_000_000.0 }
            val frame = director.frame(now)
            val progress = (frame.seconds / duration(frame.clip)).coerceIn(0f, 1f)
            rotation = when (frame.clip) {
                "Wave" -> sin(progress * Math.PI * 4).toFloat() * 8f
                "Dance" -> sin(progress * Math.PI * 8).toFloat() * 10f
                "Spin" -> progress * 360f
                else -> 0f
            }
            lift = if (frame.clip == "Jump") -sin(progress * Math.PI).toFloat() * jumpHeight else 0f
        }
        rotation = 0f
        lift = 0f
    }
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Image(
            painter = painterResource(R.drawable.leo_emblem),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 24.dp)
                .graphicsLayer { rotationZ = rotation; translationY = lift }
                .clickable(enabled = enabled, onClickLabel = "Saludar con LEO") {
                    RobotMotionBus.perform(RobotMotion.WAVE)
                },
        )
        if (state == NikoVisualState.IDLE && enabled) Text(
            text = "Decí «Leo» y hablame normal",
            color = Color(0xFF7C8A85), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        )
    }
}
