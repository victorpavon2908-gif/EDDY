package com.niko.assistant.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niko.assistant.R
import com.niko.assistant.ui.robot.LeoRobotView
import com.niko.assistant.ui.robot.RobotActivity
import com.niko.assistant.ui.robot.RobotMotion
import com.niko.assistant.ui.robot.RobotMotionBus

@Composable
internal fun NikoHero(state: NikoVisualState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val request by RobotMotionBus.requests.collectAsStateWithLifecycle()
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val activity = when (state) {
        NikoVisualState.IDLE -> RobotActivity.IDLE
        NikoVisualState.LISTENING -> RobotActivity.LISTENING
        NikoVisualState.THINKING -> RobotActivity.THINKING
        NikoVisualState.SPEAKING -> RobotActivity.SPEAKING
    }
    val description = when (activity) {
        RobotActivity.IDLE -> if (enabled) "Leo está listo" else "Leo está en pausa"
        RobotActivity.LISTENING -> "Leo te escucha"
        RobotActivity.THINKING -> "Leo está pensando"
        RobotActivity.SPEAKING -> "Leo está hablando"
    }
    Box(modifier.semantics(mergeDescendants = true) {
        contentDescription = description
        if (enabled) onClick(label = "Saludar con Leo") { RobotMotionBus.perform(RobotMotion.WAVE); true }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .5f, size.height * .865f)
            drawOval(
                brush = Brush.radialGradient(listOf(Color(0xFF56DBC4).copy(alpha = .12f), Color.Transparent), center, size.width * .4f),
                topLeft = Offset(size.width * .12f, center.y - size.height * .025f),
                size = Size(size.width * .76f, size.height * .05f),
            )
        }
        if (!failed) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                try {
                    LeoRobotView(context, lifecycle, onReady = { ready = true }, onFailure = { failed = true })
                } catch (_: Exception) {
                    failed = true
                    android.view.View(context)
                }
            },
            update = { view -> (view as? LeoRobotView)?.update(activity, enabled, !ValueAnimator.areAnimatorsEnabled(), request) },
            onReset = null,
            onRelease = { (it as? LeoRobotView)?.destroy() },
        )
        if (!ready || failed) Image(
            painter = painterResource(R.drawable.leo_robot_poster), contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
        )
        if (state == NikoVisualState.IDLE && enabled) Text(
            text = if (failed) "Leo sigue aquí para escucharte" else "Tocame o decí «Leo, bailá»",
            color = Color(0xFF76939E), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        )
    }
}
