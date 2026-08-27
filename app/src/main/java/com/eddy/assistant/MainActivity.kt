package com.eddy.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.eddy.assistant.ai.EddyAiClient
import com.eddy.assistant.ai.EddyFallbackConversation
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.memory.EddyMemory
import com.eddy.assistant.proactive.EddyProactiveScheduler
import com.eddy.assistant.ui.EddyReferenceScreen
import com.eddy.assistant.ui.EddyVisualState
import com.eddy.assistant.ui.theme.EddyTheme
import com.eddy.assistant.voice.EddySpeechRecognizer
import com.eddy.assistant.voice.EddyTextToSpeech
import com.eddy.assistant.voice.WakeResult
import com.eddy.assistant.voice.WakeWordGate
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
    val appContext = context.applicationContext
    val brain = remember { LocalBrain() }
    val executor = remember { ActionExecutor(appContext) }
    val memory = remember { EddyMemory(appContext) }
    val wakeGate = remember { WakeWordGate() }
    val aiClient = remember { EddyAiClient() }
    val fallbackConversation = remember { EddyFallbackConversation() }
    val proactiveScheduler = remember { EddyProactiveScheduler(appContext, memory) }

    var autoListeningEnabled by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var heardText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Di EDDY para activarme.") }
    var speechReady by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }

    lateinit var recognizer: EddySpeechRecognizer

    recognizer = remember {
        EddySpeechRecognizer(
            context = appContext,
            onListeningChanged = { isListening = it },
            onPartialResult = { partial -> heardText = partial },
            onResult = { raw ->
                when (val wakeResult = wakeGate.consume(raw)) {
                    WakeResult.Ignored -> {
                        heardText = ""
                        responseText = "Di EDDY para activarme."
                        recognizer.resume()
                    }

                    WakeResult.Activated -> {
                        heardText = "EDDY"
                        responseText = "Te escucho."
                        recognizer.resume()
                    }

                    is WakeResult.Command -> {
                        heardText = wakeResult.text
                        pendingCommand = wakeResult.text
                    }
                }
            },
            onError = { error ->
                isThinking = false
                responseText = error
            },
        )
    }

    val tts = remember {
        EddyTextToSpeech(
            context = appContext,
            onReady = { speechReady = it },
            onSpeakingChanged = { speaking ->
                isSpeaking = speaking
                if (!speaking && autoListeningEnabled) recognizer.resume()
            },
        )
    }

    fun speakResponse(text: String, rememberResponse: Boolean = true) {
        responseText = text
        if (rememberResponse) memory.rememberAssistantTurn(text)
        recognizer.pause()
        val queued = tts.speak(text)
        if (!queued && autoListeningEnabled) recognizer.resume()
    }

    suspend fun handleCommand(text: String) {
        memory.rememberUserTurn(text)
        val command = brain.understand(text)

        if (command == AssistantCommand.ClearMemory) {
            memory.clearAll()
            speakResponse("He borrado mi memoria local. Empezamos de nuevo desde aquí.")
            return
        }

        memory.rememberCommand(command)
        proactiveScheduler.maybeSchedule(command)

        val response = when (command) {
            AssistantCommand.Greeting -> "Aquí estoy. Te escucho."

            AssistantCommand.TellTime -> {
                val time = SimpleDateFormat("h:mm a", Locale("es", "NI")).format(Date())
                "Son las $time."
            }

            AssistantCommand.OpenCamera -> executor.openCamera().spokenMessage
            AssistantCommand.MemorySummary -> memory.describeLearnedPatterns()
            AssistantCommand.ClearMemory -> "He borrado mi memoria local."
            is AssistantCommand.OpenApp -> executor.openApp(command.app).spokenMessage
            is AssistantCommand.Dial -> executor.dial(command.number).spokenMessage
            is AssistantCommand.ComposeMessage -> executor.composeMessage(command.number, command.message).spokenMessage
            is AssistantCommand.SetAlarm -> executor.setAlarm(command.hour, command.minute, command.label).spokenMessage
            is AssistantCommand.OpenMaps -> executor.openMaps(command.query).spokenMessage

            is AssistantCommand.Unknown -> {
                aiClient.reply(
                    message = command.originalText,
                    memoryContext = memory.contextForAi(),
                ) ?: fallbackConversation.reply(command.originalText, memory)
            }
        }

        speakResponse(response)
    }

    LaunchedEffect(pendingCommand) {
        val commandText = pendingCommand ?: return@LaunchedEffect
        recognizer.pause()
        isThinking = true
        try {
            delay(120)
            handleCommand(commandText)
        } finally {
            isThinking = false
            pendingCommand = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED)

        if (micGranted) {
            autoListeningEnabled = true
            recognizer.startContinuous()
        } else {
            autoListeningEnabled = false
            responseText = "Necesito permiso de micrófono para activarme cuando digas EDDY."
        }
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            autoListeningEnabled = true
            recognizer.startContinuous()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
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
