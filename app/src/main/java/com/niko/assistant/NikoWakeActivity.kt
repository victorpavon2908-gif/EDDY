package com.niko.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.niko.assistant.background.NikoRuntimeState
import com.niko.assistant.ui.NikoReferenceScreen
import com.niko.assistant.ui.NikoVisualState
import com.niko.assistant.ui.theme.NikoTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

open class NikoWakeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            NikoTheme {
                NikoWakeScreen(
                    onFinished = { finish() },
                )
            }
        }
    }
}

@Composable
private fun NikoWakeScreen(onFinished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        NikoRuntimeState.init(context)
    }

    val snapshot by NikoRuntimeState.stateFlow.collectAsStateWithLifecycle()
    var sawActiveWork by remember { mutableStateOf(false) }

    LaunchedEffect(snapshot.state) {
        if (
            snapshot.state == NikoRuntimeState.State.THINKING ||
            snapshot.state == NikoRuntimeState.State.SPEAKING
        ) {
            sawActiveWork = true
        } else if (sawActiveWork && snapshot.state == NikoRuntimeState.State.LISTENING) {
            delay(2_800)
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        delay(30_000)
        onFinished()
    }

    val visualState = when (snapshot.state) {
        NikoRuntimeState.State.IDLE -> NikoVisualState.IDLE
        NikoRuntimeState.State.LISTENING -> NikoVisualState.LISTENING
        NikoRuntimeState.State.THINKING -> NikoVisualState.THINKING
        NikoRuntimeState.State.SPEAKING -> NikoVisualState.SPEAKING
    }

    NikoReferenceScreen(
        visualState = visualState,
        heardText = "",
        responseText = snapshot.responseText,
        voiceReady = snapshot.voiceReady,
        autoListeningEnabled = snapshot.running,
        inputState = snapshot.inputState,
        inputStatus = snapshot.inputStatus,
    )
}
