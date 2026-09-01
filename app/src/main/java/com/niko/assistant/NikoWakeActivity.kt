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
import kotlinx.coroutines.delay

open class NikoWakeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
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
    val appContext = context.applicationContext
    var snapshot by remember { mutableStateOf(NikoRuntimeState.read(appContext)) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var sawActiveWork by remember { mutableStateOf(false) }
    var quietAfterWorkMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            elapsedMs += 180
            snapshot = NikoRuntimeState.read(appContext)

            if (
                snapshot.state == NikoRuntimeState.State.THINKING ||
                snapshot.state == NikoRuntimeState.State.SPEAKING
            ) {
                sawActiveWork = true
                quietAfterWorkMs = 0L
            } else if (sawActiveWork && snapshot.state == NikoRuntimeState.State.LISTENING) {
                quietAfterWorkMs += 180
            }

            if (quietAfterWorkMs >= 2_800 || elapsedMs >= 30_000) {
                onFinished()
                break
            }
        }
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
