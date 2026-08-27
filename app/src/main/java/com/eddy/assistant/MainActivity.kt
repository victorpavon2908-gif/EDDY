package com.eddy.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.LocalBrain
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

    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var heardText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Listo para ayudarte.") }
    var speechReady by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }

    val tts = remember {
        EddyTextToSpeech(
            context = context.applicationContext,
            onReady = { speechReady = it },
            onSpeakingChanged = { isSpeaking = it },
        )
    }

    fun respond(text: String) {
        responseText = text
        tts.speak(text)
    }

    fun handleCommand(text: String) {
        when (val command = brain.understand(text)) {
            is AssistantCommand.Greeting -> respond("Aquí estoy. ¿Qué necesitas?")
            is AssistantCommand.TellTime -> {
                val time = SimpleDateFormat("h:mm a", Locale("es", "NI")).format(Date())
                respond("Son las $time.")
            }
            is AssistantCommand.OpenApp -> {
                val result = executor.openApp(command.app)
                respond(result.spokenMessage)
            }
            is AssistantCommand.OpenCamera -> {
                val result = executor.openCamera()
                respond(result.spokenMessage)
            }
            is AssistantCommand.Unknown -> respond(
                "Te escuché decir: ${command.originalText}. Todavía no tengo esa habilidad, pero la agregaremos al cerebro de EDDY."
            )
        }
    }

    LaunchedEffect(pendingCommand) {
        val commandText = pendingCommand ?: return@LaunchedEffect
        isThinking = true
        delay(320)
        handleCommand(commandText)
        isThinking = false
        pendingCommand = null
    }

    val recognizer = remember {
        EddySpeechRecognizer(
            context = context.applicationContext,
            onListeningChanged = { isListening = it },
            onResult = {
                heardText = it
                pendingCommand = it
            },
            onError = {
                isThinking = false
                respond(it)
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.destroy()
            tts.shutdown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recognizer.start()
        } else {
            respond("Necesito permiso para usar el micrófono.")
        }
    }

    fun startListening() {
        tts.stop()
        isThinking = false
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) recognizer.start()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val visualState = when {
        isListening -> EddyVisualState.LISTENING
        isThinking -> EddyVisualState.THINKING
        isSpeaking -> EddyVisualState.SPEAKING
        else -> EddyVisualState.IDLE
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        EddyHomeScreen(
            modifier = Modifier.padding(padding),
            visualState = visualState,
            heardText = heardText,
            responseText = responseText,
            speechReady = speechReady,
            onMicClick = {
                if (isListening) recognizer.stop() else startListening()
            },
        )
    }
}

@Composable
private fun EddyHomeScreen(
    modifier: Modifier = Modifier,
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    speechReady: Boolean,
    onMicClick: () -> Unit,
) {
    val glow = when (visualState) {
        EddyVisualState.IDLE -> MaterialTheme.colorScheme.primary
        EddyVisualState.LISTENING -> Color(0xFF72D7FF)
        EddyVisualState.THINKING -> Color(0xFFB594FF)
        EddyVisualState.SPEAKING -> Color(0xFF4FFFC0)
    }

    val title = when (visualState) {
        EddyVisualState.IDLE -> "¿Qué necesitas?"
        EddyVisualState.LISTENING -> "Te escucho..."
        EddyVisualState.THINKING -> "Pensando..."
        EddyVisualState.SPEAKING -> "Hablando..."
    }

    val subtitle = when (visualState) {
        EddyVisualState.IDLE -> "EDDY está listo"
        EddyVisualState.LISTENING -> "Estoy captando tu voz"
        EddyVisualState.THINKING -> "Estoy procesando tu orden"
        EddyVisualState.SPEAKING -> "Respuesta de EDDY en curso"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071018),
                        Color(0xFF0A1823),
                        Color(0xFF061018),
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "EDDY",
            color = glow,
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Asistente personal inteligente",
            color = Color(0xFF89A3B2),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.48f))

        AnimatedEddySymbol(
            modifier = Modifier.size(238.dp),
            glow = glow,
            state = visualState,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = subtitle,
            color = Color(0xFF8EA6B4),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D1B25).copy(alpha = 0.92f)
            ),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                if (heardText.isNotBlank()) {
                    Text(
                        "TÚ",
                        color = Color(0xFF718B99),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        heardText,
                        color = Color(0xFFC7D7DE),
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    "EDDY",
                    color = glow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    responseText,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(78.dp)
                .background(glow.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, glow.copy(alpha = 0.65f), CircleShape)
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (visualState == EddyVisualState.LISTENING) {
                    Icons.Rounded.Stop
                } else {
                    Icons.Rounded.Mic
                },
                contentDescription = if (visualState == EddyVisualState.LISTENING) {
                    "Detener"
                } else {
                    "Hablar con EDDY"
                },
                tint = glow,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (speechReady) {
                    "VOZ LISTA • AVATAR ACTIVO"
                } else {
                    "INICIANDO VOZ • AVATAR ACTIVO"
                },
                color = Color(0xFF607986),
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AnimatedEddySymbol(
    modifier: Modifier = Modifier,
    glow: Color,
    state: EddyVisualState,
) {
    val transition = rememberInfiniteTransition(label = "eddySymbol")

    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = when (state) {
            EddyVisualState.LISTENING -> 1.06f
            EddyVisualState.THINKING -> 1.035f
            EddyVisualState.SPEAKING -> 1.05f
            EddyVisualState.IDLE -> 1.025f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.LISTENING -> 520
                    EddyVisualState.THINKING -> 720
                    EddyVisualState.SPEAKING -> 420
                    EddyVisualState.IDLE -> 1700
                }
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val bob by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    val speakingWave by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mouth",
    )

    val blinkFactor = when {
        blinkPhase in 0.79f..0.86f -> 0.16f
        blinkPhase in 0.89f..0.94f -> 0.22f
        else -> 1f
    }

    Box(
        modifier = modifier.graphicsLayer(
            scaleX = pulse,
            scaleY = pulse,
            translationY = bob,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val lineColor = Color(0xFFF5FBFF)
            val strokeMain = minDim * 0.072f
            val strokeThin = strokeMain * 0.48f
            val glowAlpha = when (state) {
                EddyVisualState.IDLE -> 0.14f
                EddyVisualState.LISTENING -> 0.28f
                EddyVisualState.THINKING -> 0.24f
                EddyVisualState.SPEAKING -> 0.30f
            }

            drawCircle(
                color = glow.copy(alpha = 0.05f),
                radius = minDim * 0.50f,
            )
            drawCircle(
                color = glow.copy(alpha = glowAlpha),
                radius = minDim * 0.44f,
                style = Stroke(width = minDim * 0.010f),
            )
            drawCircle(
                color = glow.copy(alpha = glowAlpha * 0.7f),
                radius = minDim * 0.35f,
                style = Stroke(width = minDim * 0.007f),
            )

            if (state == EddyVisualState.THINKING) {
                drawArc(
                    color = glow,
                    startAngle = phase * 360f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(w * 0.06f, h * 0.06f),
                    size = Size(w * 0.88f, h * 0.88f),
                    style = Stroke(width = minDim * 0.018f, cap = StrokeCap.Round),
                )
            }

            val leftX = w * 0.23f
            val rightX = w * 0.77f
            val topY = h * 0.12f
            val midY = h * 0.59f
            val bottomY = h * 0.86f

            val mainStroke = Stroke(
                width = strokeMain,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Round,
            )
            val thinStroke = Stroke(
                width = strokeThin,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            drawLine(
                color = glow.copy(alpha = 0.18f),
                start = Offset(leftX, topY + strokeMain * 0.4f),
                end = Offset(leftX, bottomY),
                strokeWidth = strokeMain * 1.55f,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = glow.copy(alpha = 0.18f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftX, topY),
                size = Size(rightX - leftX, h * 0.27f),
                style = Stroke(width = strokeMain * 1.55f, cap = StrokeCap.Round),
            )

            drawLine(
                color = lineColor,
                start = Offset(leftX, topY + strokeMain * 0.45f),
                end = Offset(leftX, bottomY),
                strokeWidth = strokeMain,
                cap = StrokeCap.Butt,
            )
            drawArc(
                color = lineColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftX, topY),
                size = Size(rightX - leftX, h * 0.27f),
                style = mainStroke,
            )
            drawLine(
                color = lineColor,
                start = Offset(leftX, midY),
                end = Offset(w * 0.75f, midY),
                strokeWidth = strokeMain,
                cap = StrokeCap.Butt,
            )
            drawLine(
                color = lineColor,
                start = Offset(leftX, bottomY),
                end = Offset(w * 0.77f, bottomY),
                strokeWidth = strokeMain,
                cap = StrokeCap.Butt,
            )

            val eyeWidth = w * 0.14f
            val eyeHeight = max(h * 0.058f * blinkFactor, 2f)
            drawArc(
                color = lineColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.29f, h * 0.29f),
                size = Size(eyeWidth, eyeHeight),
                style = thinStroke,
            )
            drawArc(
                color = lineColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.57f, h * 0.29f),
                size = Size(eyeWidth, eyeHeight),
                style = thinStroke,
            )

            val mouthLeft = w * 0.38f
            val mouthRight = w * 0.63f
            val mouthWidth = mouthRight - mouthLeft
            val mouthTop = h * 0.46f

            if (state == EddyVisualState.SPEAKING) {
                val mouthHeight = h * (0.055f + 0.075f * speakingWave)
                drawOval(
                    color = lineColor,
                    topLeft = Offset(mouthLeft, mouthTop),
                    size = Size(mouthWidth, mouthHeight),
                    style = thinStroke,
                )
            } else {
                drawLine(
                    color = lineColor,
                    start = Offset(mouthLeft, mouthTop),
                    end = Offset(mouthRight, mouthTop),
                    strokeWidth = strokeThin,
                    cap = StrokeCap.Round,
                )
                drawArc(
                    color = lineColor,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(mouthLeft, mouthTop),
                    size = Size(mouthWidth, h * 0.12f),
                    style = thinStroke,
                )
            }

            val yTop = h * 0.62f
            val yMiddle = h * 0.74f
            val centerX = w * 0.49f
            drawLine(
                color = lineColor,
                start = Offset(w * 0.39f, yTop),
                end = Offset(centerX, yMiddle),
                strokeWidth = strokeThin,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(w * 0.60f, yTop),
                end = Offset(centerX, yMiddle),
                strokeWidth = strokeThin,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = lineColor,
                start = Offset(centerX, yMiddle),
                end = Offset(centerX, bottomY),
                strokeWidth = strokeThin,
                cap = StrokeCap.Round,
            )

            when (state) {
                EddyVisualState.LISTENING -> {
                    drawCircle(
                        color = glow,
                        radius = strokeThin * 0.58f,
                        center = Offset(w * 0.76f, h * 0.17f),
                    )
                }
                EddyVisualState.SPEAKING -> {
                    val baseX = w * 0.83f
                    val centerY = h * 0.48f
                    for (i in 0..2) {
                        val wave = 0.35f + speakingWave * (0.35f + i * 0.12f)
                        val half = h * 0.035f * wave
                        drawLine(
                            color = glow.copy(alpha = 0.9f - i * 0.18f),
                            start = Offset(baseX + i * w * 0.035f, centerY - half),
                            end = Offset(baseX + i * w * 0.035f, centerY + half),
                            strokeWidth = minDim * 0.012f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
