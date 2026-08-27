package com.eddy.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.memory.EddyMemory
import com.eddy.assistant.ui.EddyReferenceScreen
import com.eddy.assistant.ui.EddyVisualState
import com.eddy.assistant.ui.theme.EddyTheme
import com.eddy.assistant.voice.EddySpeechRecognizer
import com.eddy.assistant.voice.EddyTextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { EddyTheme { EddyApp() } }
    }
}

@Composable
private fun EddyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val brain = remember { LocalBrain() }
    val executor = remember { ActionExecutor(context.applicationContext) }
    val memory = remember { EddyMemory(context.applicationContext) }

    var autoListeningEnabled by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var heardText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Listo para ayudarte.") }
    var speechReady by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }

    val recognizer = remember {
        EddySpeechRecognizer(
            context = context.applicationContext,
            onListeningChanged = { isListening = it },
            onPartialResult = { heardText = it },
            onResult = {
                heardText = it
                pendingCommand = it
            },
            onError = {
                isThinking = false
                responseText = it
            },
        )
    }

    val tts = remember {
        EddyTextToSpeech(
            context = context.applicationContext,
            onReady = { speechReady = it },
            onSpeakingChanged = { speaking ->
                isSpeaking = speaking
                if (!speaking && autoListeningEnabled) recognizer.resume()
            },
        )
    }

    fun respond(text: String) {
        responseText = text
        recognizer.pause()
        val queued = tts.speak(text)
        if (!queued && autoListeningEnabled) recognizer.resume()
    }

    fun handleCommand(text: String) {
        memory.rememberUtterance(text)
        val command = brain.understand(text)
        memory.rememberCommand(command)

        when (command) {
            AssistantCommand.Greeting -> respond("Aquí estoy. Te escucho.")
            AssistantCommand.TellTime -> {
                val time = SimpleDateFormat("h:mm a", Locale("es", "NI")).format(Date())
                respond("Son las $time.")
            }
            AssistantCommand.OpenCamera -> respond(executor.openCamera().spokenMessage)
            AssistantCommand.MemorySummary -> respond(memory.describeLearnedPatterns())
            is AssistantCommand.OpenApp -> respond(executor.openApp(command.app).spokenMessage)
            is AssistantCommand.Unknown -> respond(
                "Te escuché decir: ${command.originalText}. Aún estoy ampliando mis habilidades, pero ya lo guardé como parte de nuestro contexto local."
            )
        }
    }

    LaunchedEffect(pendingCommand) {
        val commandText = pendingCommand ?: return@LaunchedEffect
        recognizer.pause()
        isThinking = true
        delay(220)
        handleCommand(commandText)
        isThinking = false
        pendingCommand = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            autoListeningEnabled = true
            recognizer.startContinuous()
        } else {
            autoListeningEnabled = false
            responseText = "Activa el permiso del micrófono para que pueda escucharte sin tocar la pantalla."
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            autoListeningEnabled = true
            recognizer.startContinuous()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.destroy()
            tts.shutdown()
        }
    }

    val visualState = when {
        isSpeaking -> EddyVisualState.SPEAKING
        isThinking -> EddyVisualState.THINKING
        isListening -> EddyVisualState.LISTENING
        else -> EddyVisualState.IDLE
    }

    EddyReferenceScreen(
        visualState = visualState,
        heardText = heardText,
        responseText = responseText,
        voiceReady = speechReady,
        autoListeningEnabled = autoListeningEnabled,
    )
}
