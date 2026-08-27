package com.eddy.assistant

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
import com.eddy.assistant.background.EddyRuntimeState
import com.eddy.assistant.ui.EddyReferenceScreen
import com.eddy.assistant.ui.EddyVisualState
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.delay

class EddyWakeActivity : ComponentActivity() {
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
            EddyTheme {
                EddyWakeScreen(
                    onFinished = { finish() },
                )
            }
        }
    }
}

@Composable
private fun EddyWakeScreen(onFinished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    var snapshot by remember { mutableStateOf(EddyRuntimeState.read(appContext)) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var sawActiveWork by remember { mutableStateOf(false) }
    var quietAfterWorkMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            elapsedMs += 180
            snapshot = EddyRuntimeState.read(appContext)

            if (
                snapshot.state == EddyRuntimeState.State.THINKING ||
                snapshot.state == EddyRuntimeState.State.SPEAKING
            ) {
                sawActiveWork = true
                quietAfterWorkMs = 0L
            } else if (sawActiveWork && snapshot.state == EddyRuntimeState.State.LISTENING) {
                quietAfterWorkMs += 180
            }

            if (quietAfterWorkMs >= 2_800 || elapsedMs >= 30_000) {
                onFinished()
                break
            }
        }
    }

    val visualState = when (snapshot.state) {
        EddyRuntimeState.State.IDLE -> EddyVisualState.IDLE
        EddyRuntimeState.State.LISTENING -> EddyVisualState.LISTENING
        EddyRuntimeState.State.THINKING -> EddyVisualState.THINKING
        EddyRuntimeState.State.SPEAKING -> EddyVisualState.SPEAKING
    }

    val lockScreenStatus = when (snapshot.state) {
        EddyRuntimeState.State.IDLE -> "EDDY activo."
        EddyRuntimeState.State.LISTENING -> "Te escucho."
        EddyRuntimeState.State.THINKING -> "Pensando..."
        EddyRuntimeState.State.SPEAKING -> "Respondiendo..."
    }

    EddyReferenceScreen(
        visualState = visualState,
        heardText = "",
        responseText = lockScreenStatus,
        voiceReady = snapshot.voiceReady,
        autoListeningEnabled = snapshot.running,
    )
}
