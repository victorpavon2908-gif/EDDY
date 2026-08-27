package com.eddy.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var heardText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Listo para ayudarte.") }
    var speechReady by remember { mutableStateOf(false) }

    val tts = remember {
        EddyTextToSpeech(context.applicationContext) { speechReady = it }
    }

    fun respond(text: String) {
        responseText = text
        tts.speak(text)
    }

    fun handleCommand(text: String) {
        heardText = text
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

    val recognizer = remember {
        EddySpeechRecognizer(
            context = context.applicationContext,
            onListeningChanged = { isListening = it },
            onResult = { handleCommand(it) },
            onError = { respond(it) },
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
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) recognizer.start()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        EddyHomeScreen(
            modifier = Modifier.padding(padding),
            isListening = isListening,
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
    isListening: Boolean,
    heardText: String,
    responseText: String,
    speechReady: Boolean,
    onMicClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "eddyPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 650 else 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val glow = if (isListening) Color(0xFF72D7FF) else MaterialTheme.colorScheme.primary

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
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "EDDY",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            letterSpacing = 6.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Everyday Digital Dynamic Intelligence",
            color = Color(0xFF89A3B2),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.7f))

        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulse),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(190.dp)
                    .border(1.dp, glow.copy(alpha = 0.16f), CircleShape)
            )
            Box(
                Modifier
                    .size(146.dp)
                    .border(2.dp, glow.copy(alpha = 0.34f), CircleShape)
            )
            Box(
                Modifier
                    .size(104.dp)
                    .background(glow.copy(alpha = 0.13f), CircleShape)
                    .border(2.dp, glow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isListening) "●" else "E",
                    color = glow,
                    fontSize = if (isListening) 38.sp else 48.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = if (isListening) "Te escucho..." else "¿Qué necesitas?",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B25).copy(alpha = 0.92f)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                if (heardText.isNotBlank()) {
                    Text("TÚ", color = Color(0xFF718B99), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(heardText, color = Color(0xFFC7D7DE), fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                }
                Text("EDDY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(responseText, color = Color.White, fontSize = 17.sp, lineHeight = 24.sp)
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
                imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (isListening) "Detener" else "Hablar con EDDY",
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
                text = if (speechReady) "VOZ LISTA • v0.1" else "INICIANDO VOZ • v0.1",
                color = Color(0xFF607986),
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            )
        }
    }
}
