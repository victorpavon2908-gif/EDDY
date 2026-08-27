package com.eddy.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.memory.EddyMemory
import com.eddy.assistant.ui.theme.EddyTheme
import com.eddy.assistant.voice.EddySpeechRecognizer
import com.eddy.assistant.voice.EddyTextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private enum class EddyVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EddyTheme {
                EddyApp()
            }
        }
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
            onPartialResult = { partial -> heardText = partial },
            onResult = { result ->
                heardText = result
                pendingCommand = result
            },
            onError = { error ->
                isThinking = false
                responseText = error
            },
        )
    }

    val tts = remember {
        EddyTextToSpeech(
            context = context.applicationContext,
            onReady = { speechReady = it },
            onSpeakingChanged = { speaking ->
                isSpeaking = speaking
                if (!speaking && autoListeningEnabled) {
                    recognizer.resume()
                }
            },
        )
    }

    fun respond(text: String) {
        responseText = text
        recognizer.pause()
        val queued = tts.speak(text)
        if (!queued && autoListeningEnabled) {
            recognizer.resume()
        }
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
            AssistantCommand.OpenCamera -> {
                val result = executor.openCamera()
                respond(result.spokenMessage)
            }
            AssistantCommand.MemorySummary -> respond(memory.describeLearnedPatterns())
            is AssistantCommand.OpenApp -> {
                val result = executor.openApp(command.app)
                respond(result.spokenMessage)
            }
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

    EddyFullScreen(
        visualState = visualState,
        heardText = heardText,
        responseText = responseText,
        voiceReady = speechReady,
        autoListeningEnabled = autoListeningEnabled,
    )
}

@Composable
private fun EddyFullScreen(
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    voiceReady: Boolean,
    autoListeningEnabled: Boolean,
) {
    val stateText = when {
        !autoListeningEnabled -> "MICRÓFONO DESACTIVADO"
        !voiceReady -> "INICIANDO VOZ"
        visualState == EddyVisualState.LISTENING -> "ESCUCHANDO"
        visualState == EddyVisualState.THINKING -> "PENSANDO"
        visualState == EddyVisualState.SPEAKING -> "HABLANDO"
        else -> "LISTO"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Text(
            text = "EDDY",
            color = Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 7.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )

        Text(
            text = stateText,
            color = Color(0xFF777777),
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp),
        )

        FullScreenEddyAvatar(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 46.dp, bottom = 94.dp),
            state = visualState,
        )

        TranscriptStrip(
            heardText = heardText,
            responseText = responseText,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TranscriptStrip(
    heardText: String,
    responseText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        color = Color.Black,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(
                text = if (heardText.isBlank()) "TÚ: ..." else "TÚ: $heardText",
                color = Color(0xFF969696),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = "EDDY: $responseText",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FullScreenEddyAvatar(
    modifier: Modifier = Modifier,
    state: EddyVisualState,
) {
    val transition = rememberInfiniteTransition(label = "eddyFullScreen")

    val pulse by transition.animateFloat(
        initialValue = 0.988f,
        targetValue = when (state) {
            EddyVisualState.LISTENING -> 1.022f
            EddyVisualState.THINKING -> 1.015f
            EddyVisualState.SPEAKING -> 1.026f
            EddyVisualState.IDLE -> 1.008f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 620
                    EddyVisualState.THINKING -> 850
                    EddyVisualState.SPEAKING -> 420
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

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val mouthMotion by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 170),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mouth",
    )

    val blinkFactor = when {
        blinkPhase in 0.80f..0.86f -> 0.10f
        blinkPhase in 0.89f..0.94f -> 0.18f
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
            val black = Color.Black
            val gray = Color(0xFFE7E7E7)
            val midGray = Color(0xFFBEBEBE)

            val center = Offset(w * 0.50f, h * 0.48f)
            val avatarWidth = w * 0.76f
            val avatarHeight = h * 0.72f
            val left = center.x - avatarWidth * 0.48f
            val right = center.x + avatarWidth * 0.48f
            val top = center.y - avatarHeight * 0.48f
            val bottom = center.y + avatarHeight * 0.48f

            val mainStroke = max(minDim * 0.026f, 8f)
            val faceStroke = mainStroke * 0.64f
            val thinStroke = mainStroke * 0.34f

            // Minimal sensing field.
            drawCircle(
                color = gray,
                radius = minDim * 0.43f,
                center = center,
                style = Stroke(width = thinStroke * 0.35f),
            )
            drawCircle(
                color = Color(0xFFF3F3F3),
                radius = minDim * 0.35f,
                center = center,
                style = Stroke(width = thinStroke * 0.28f),
            )

            if (state == EddyVisualState.THINKING) {
                drawArc(
                    color = black,
                    startAngle = phase * 360f,
                    sweepAngle = 52f,
                    useCenter = false,
                    topLeft = Offset(center.x - minDim * 0.43f, center.y - minDim * 0.43f),
                    size = Size(minDim * 0.86f, minDim * 0.86f),
                    style = Stroke(width = thinStroke, cap = StrokeCap.Round),
                )
            }

            // Main EDDY silhouette: strong, clean monogram-like body.
            drawLine(
                color = black,
                start = Offset(left, top + mainStroke * 0.55f),
                end = Offset(left, bottom),
                strokeWidth = mainStroke,
                cap = StrokeCap.Round,
            )

            drawArc(
                color = black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(right - left, avatarHeight * 0.29f),
                style = Stroke(width = mainStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val middleY = center.y + avatarHeight * 0.06f
            drawLine(
                color = black,
                start = Offset(left, middleY),
                end = Offset(right * 0.99f, middleY),
                strokeWidth = mainStroke,
                cap = StrokeCap.Round,
            )

            drawLine(
                color = black,
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = mainStroke,
                cap = StrokeCap.Round,
            )

            // Eyes.
            val eyeY = center.y - avatarHeight * 0.18f
            val eyeWidth = avatarWidth * 0.19f
            val eyeHeight = max(avatarHeight * 0.045f * blinkFactor, 2f)
            val leftEyeX = center.x - avatarWidth * 0.23f
            val rightEyeX = center.x + avatarWidth * 0.04f

            val eyeStyle = Stroke(
                width = faceStroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            drawArc(
                color = black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeX, eyeY),
                size = Size(eyeWidth, eyeHeight),
                style = eyeStyle,
            )
            drawArc(
                color = black,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeX, eyeY),
                size = Size(eyeWidth, eyeHeight),
                style = eyeStyle,
            )

            // Mouth reacts while EDDY speaks.
            val mouthWidth = avatarWidth * 0.28f
            val mouthLeft = center.x - mouthWidth / 2f
            val mouthTop = center.y - avatarHeight * 0.035f
            val mouthHeight = if (state == EddyVisualState.SPEAKING) {
                avatarHeight * (0.045f + 0.075f * mouthMotion)
            } else {
                avatarHeight * 0.095f
            }

            drawLine(
                color = black,
                start = Offset(mouthLeft, mouthTop),
                end = Offset(mouthLeft + mouthWidth, mouthTop),
                strokeWidth = faceStroke,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = black,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthLeft, mouthTop),
                size = Size(mouthWidth, mouthHeight),
                style = eyeStyle,
            )

            // Y structure.
            val yTop = middleY + avatarHeight * 0.055f
            val yJoint = center.y + avatarHeight * 0.24f
            val yCenter = center.x
            drawLine(
                color = black,
                start = Offset(center.x - avatarWidth * 0.16f, yTop),
                end = Offset(yCenter, yJoint),
                strokeWidth = faceStroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = black,
                start = Offset(center.x + avatarWidth * 0.16f, yTop),
                end = Offset(yCenter, yJoint),
                strokeWidth = faceStroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = black,
                start = Offset(yCenter, yJoint),
                end = Offset(yCenter, bottom),
                strokeWidth = faceStroke,
                cap = StrokeCap.Round,
            )

            // Sound awareness bars: subtle until voice is detected.
            val activity = when (state) {
                EddyVisualState.LISTENING -> 1f
                EddyVisualState.SPEAKING -> 0.72f
                EddyVisualState.THINKING -> 0.25f
                EddyVisualState.IDLE -> 0.10f
            }

            repeat(5) { index ->
                val distance = minDim * (0.37f + index * 0.025f)
                val barHeight = minDim * (0.025f + activity * (0.030f + index * 0.008f))
                val alpha = 0.18f + activity * 0.62f
                val xLeft = center.x - distance
                val xRight = center.x + distance

                drawLine(
                    color = if (index == 2 && state == EddyVisualState.LISTENING) black else midGray.copy(alpha = alpha),
                    start = Offset(xLeft, center.y - barHeight / 2f),
                    end = Offset(xLeft, center.y + barHeight / 2f),
                    strokeWidth = thinStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = if (index == 2 && state == EddyVisualState.LISTENING) black else midGray.copy(alpha = alpha),
                    start = Offset(xRight, center.y - barHeight / 2f),
                    end = Offset(xRight, center.y + barHeight / 2f),
                    strokeWidth = thinStroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
