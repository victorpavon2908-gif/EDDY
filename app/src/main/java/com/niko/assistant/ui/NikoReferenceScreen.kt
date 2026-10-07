package com.niko.assistant.ui

import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niko.assistant.AiSettingsActivity
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.background.NikoRuntimeState.InputState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** LEO: articulated companion, with conversation and controls below the stage. */
@Composable
internal fun NikoReferenceScreen(
    visualState: NikoVisualState,
    heardText: String,
    responseText: String,
    voiceReady: Boolean,
    autoListeningEnabled: Boolean,
    inputState: InputState,
    webUsed: Boolean = false,
    webSources: List<NikoWebSource> = emptyList(),
    inputStatus: String = "",
    webSearching: Boolean = false,
    onTools: () -> Unit = {},
) {
    val context = LocalContext.current
    val displayState = if (visualState == NikoVisualState.LISTENING && inputState != InputState.READY) {
        NikoVisualState.IDLE
    } else visualState
    val accent = stateAccent(displayState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F1EB)),
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NikoTopBar(
                state = displayState,
                autoListeningEnabled = autoListeningEnabled,
                onSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(minOf(260.dp, (maxHeight - 46.dp).coerceAtLeast(0.dp))),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF192B27),
                    border = BorderStroke(1.dp, Color(0xFF3D5147)),
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFB7C8B2).copy(alpha = 0.16f), Color.Transparent),
                                ),
                                radius = size.minDimension * 0.48f,
                            )
                        }
                        NikoHero(
                            state = displayState,
                            enabled = autoListeningEnabled,
                            modifier = Modifier.size(220.dp).padding(12.dp),
                        )
                    }
                }

                LiveStateBadge(
                    state = displayState,
                    enabled = autoListeningEnabled,
                    inputState = inputState,
                    webSearching = webSearching,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                )
            }

            ConversationGlass(
                state = displayState,
                heardText = heardText,
                responseText = responseText,
                webUsed = webUsed,
                webSearching = webSearching,
                sources = webSources,
            )

            Spacer(Modifier.height(12.dp))
            QuickActionsRail()
            Spacer(Modifier.height(12.dp))

            PremiumBottomDock(
                enabled = autoListeningEnabled,
                inputState = inputState,
                inputStatus = inputStatus,
                voiceReady = voiceReady,
                state = displayState,
                onTools = onTools,
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NikoTopBar(
    state: NikoVisualState,
    autoListeningEnabled: Boolean,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LEO",
                    color = Color(0xFF1A2421),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 5.sp,
                )
                Spacer(Modifier.size(7.dp))
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(if (autoListeningEnabled) stateAccent(state) else Color(0xFF66717B))
                }
            }
            Text(
                text = "ASISTENTE PERSONAL",
                color = Color(0xFF59665F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.2.sp,
            )
        }

        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onSettings),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color(0xFFE2E9E6)),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF4B5A55),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveStateBadge(
    state: NikoVisualState,
    enabled: Boolean,
    inputState: InputState,
    webSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (enabled && inputState == InputState.READY) stateAccent(state) else Color(0xFF7C8791)
    val text = when {
        !enabled -> "EN PAUSA"
        inputState == InputState.PREPARING -> "INICIANDO MICRÓFONO"
        inputState != InputState.READY -> "MICRÓFONO NO DISPONIBLE"
        webSearching -> "BUSCANDO EN INTERNET"
        state == NikoVisualState.LISTENING -> "ESCUCHANDO"
        state == NikoVisualState.THINKING -> "PENSANDO"
        state == NikoVisualState.SPEAKING -> "Respondiendo"
        else -> "Decime, te escucho"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(7.dp)) { drawCircle(accent) }
            Spacer(Modifier.size(8.dp))
            Text(
                text = text,
                color = Color(0xFF4E5D58),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
        }
    }
}

@Composable
private fun ConversationGlass(
    state: NikoVisualState,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    webSearching: Boolean,
    sources: List<NikoWebSource>,
) {
    val uriHandler = LocalUriHandler.current
    var showFullResponse by remember { mutableStateOf(false) }
    val accent = stateAccent(state)
    val label = when (state) {
        NikoVisualState.LISTENING -> "TE ESCUCHO"
        NikoVisualState.THINKING -> if (webSearching) "BUSCANDO" else "PROCESANDO"
        NikoVisualState.SPEAKING -> "LEO"
        NikoVisualState.IDLE -> "LEO"
    }
    val message = when (state) {
        NikoVisualState.LISTENING -> heardText.ifBlank { "Decime qué necesitás." }
        NikoVisualState.THINKING -> heardText.ifBlank { "Estoy trabajando en eso." }
        NikoVisualState.SPEAKING -> responseText.ifBlank { "Aquí estoy." }
        NikoVisualState.IDLE -> responseText.ifBlank { "Decí “Leo” y hablame normal." }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFFE2E9E6)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f)),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (webUsed || webSearching) Icons.Rounded.Language else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(Modifier.size(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        color = accent.copy(alpha = 0.90f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = message,
                        color = Color(0xFF1C2623),
                        fontSize = 18.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (responseText.isNotBlank() && state != NikoVisualState.LISTENING && state != NikoVisualState.THINKING) {
                TextButton(onClick = { showFullResponse = true }) { Text("Leer respuesta completa") }
            }
            if (state != NikoVisualState.IDLE) NeuralWaveform(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(27.dp),
            )

            if (heardText.isNotBlank() && state != NikoVisualState.LISTENING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "VOS · $heardText",
                    color = Color(0xFF56675F),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(sources.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.take(4).forEachIndexed { index, source ->
                        Surface(
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFF5F8F7),
                            border = BorderStroke(1.dp, Color(0xFFE1E8E5)),
                        ) {
                            Text(
                                text = "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = Color(0xFF687670),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFullResponse) FullResponseSheet(responseText, sources) { showFullResponse = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullResponseSheet(response: String, sources: List<NikoWebSource>, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("LEO · Conversación", style = MaterialTheme.typography.titleLarge)
            SelectionContainer { Text(response, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface) }
            if (sources.isNotEmpty()) Text("Fuentes consultadas", style = MaterialTheme.typography.titleMedium)
            sources.forEachIndexed { index, source ->
                TextButton(onClick = { runCatching { uriHandler.openUri(source.url) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("[${index + 1}] ${source.title}")
                }
            }
            TextButton(onClick = onDismiss) { Text("Volver a LEO") }
        }
    }
}

@Composable
private fun NeuralWaveform(state: NikoVisualState, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "nikoNeuralWave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.IDLE -> 1_900
                    NikoVisualState.LISTENING -> 720
                    NikoVisualState.THINKING -> 1_050
                    NikoVisualState.SPEAKING -> 460
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(modifier) {
        val bars = 38
        val slot = size.width / bars
        val centerY = size.height / 2f
        val accent = stateAccent(state)
        repeat(bars) { index ->
            val harmonic = abs(sin(phase * PI * 2 + index * 0.53).toFloat())
            val envelope = 0.42f + 0.58f * sin(index * PI / (bars - 1)).toFloat().coerceAtLeast(0f)
            val amount = when (state) {
                NikoVisualState.IDLE -> 0.06f
                NikoVisualState.LISTENING -> 0.50f
                NikoVisualState.THINKING -> 0.30f
                NikoVisualState.SPEAKING -> 0.83f
            }
            val height = size.height * (0.10f + harmonic * envelope * amount)
            val tint = accent
            drawRoundRect(
                color = tint.copy(alpha = if (state == NikoVisualState.IDLE) 0.22f else 0.68f),
                topLeft = Offset(index * slot + slot * 0.34f, centerY - height / 2f),
                size = Size(slot * 0.26f, height),
                cornerRadius = CornerRadius(slot * 0.15f),
            )
        }
    }
}

@Composable
private fun QuickActionsRail() {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    val actions = listOf(
        QuickAction("Linterna", Icons.Rounded.FlashlightOn, Color(0xFF345849)) {
            runCatching {
                val manager = context.getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@runCatching
                torchOn = !torchOn
                manager.setTorchMode(cameraId, torchOn)
            }
        },
        QuickAction("YouTube", Icons.Rounded.SmartDisplay, Color(0xFF345849)) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) context.startActivity(launch)
            else runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com")))
            }
        },
        QuickAction("WhatsApp", Icons.Rounded.Chat, Color(0xFF345849)) {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let(context::startActivity)
        },
        QuickAction("Alarmas", Icons.Rounded.Alarm, Color(0xFF345849)) {
            runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier
                        .size(50.dp)
                        .clickable(onClick = action.onClick),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, action.tint.copy(alpha = 0.18f)),
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = action.tint,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = action.label,
                    color = Color(0xFF67756F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PremiumBottomDock(
    enabled: Boolean,
    inputState: InputState,
    inputStatus: String,
    voiceReady: Boolean,
    state: NikoVisualState,
    onTools: () -> Unit,
) {
    val ready = enabled && inputState == InputState.READY
    val accent = if (ready) stateAccent(state) else Color(0xFF87938F)
    val status = when {
        !enabled -> "En pausa"
        inputState == InputState.PREPARING -> "Preparando"
        inputState != InputState.READY -> "No disponible"
        state == NikoVisualState.LISTENING -> "Escuchando"
        state == NikoVisualState.THINKING -> "Pensando"
        state == NikoVisualState.SPEAKING -> "Hablando"
        else -> if (voiceReady) "Decí “Leo”" else "Listo"
    }
    val detail = when {
        inputStatus.isNotBlank() && !ready -> inputStatus.take(64)
        ready -> "Podés hablarme sin tocar la pantalla"
        else -> "Revisá Ajustes si necesitás activar la escucha"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0xFFE0E8E4)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Home, contentDescription = "Inicio", tint = Color(0xFF8A9691), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text("Inicio", color = Color(0xFF7A8782), fontSize = 11.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = if (ready) 0.14f else 0.08f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(status, color = Color(0xFF33413C), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Color(0xFF52635A), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Column(
                modifier = Modifier.clickable(onClick = onTools).padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = "Herramientas", tint = Color(0xFF65736E), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text("Herramientas", color = Color(0xFF65736E), fontSize = 11.sp)
            }
        }
    }
}

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

private fun stateAccent(state: NikoVisualState): Color = when (state) {
    NikoVisualState.IDLE -> Color(0xFF176B57)
    NikoVisualState.LISTENING -> Color(0xFF176B57)
    NikoVisualState.THINKING -> Color(0xFF6B5792)
    NikoVisualState.SPEAKING -> Color(0xFF916A34)
}
