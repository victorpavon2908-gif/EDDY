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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FlashlightOn
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * NIKO immersive voice surface.
 *
 * The screen deliberately avoids the old stack of white cards and mascot-demo controls. The
 * neural core is the product identity; supporting controls stay visually subordinate and work
 * like an ambient assistant rather than a dashboard.
 */
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
) {
    val context = LocalContext.current
    val displayState = if (visualState == NikoVisualState.LISTENING && inputState != InputState.READY) {
        NikoVisualState.IDLE
    } else visualState
    val accent = stateAccent(displayState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF03050A),
                        Color(0xFF070B14),
                        Color(0xFF0A0F1B),
                        Color(0xFF05070D),
                    ),
                ),
            ),
    ) {
        NeuralBackdrop(accent)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NikoTopBar(
                state = displayState,
                autoListeningEnabled = autoListeningEnabled,
                onSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                NikoHero(
                    state = displayState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 6.dp),
                )

                LiveStateBadge(
                    state = displayState,
                    enabled = autoListeningEnabled,
                    inputState = inputState,
                    webSearching = webSearching,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
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

            WakeDock(
                enabled = autoListeningEnabled,
                inputState = inputState,
                inputStatus = inputStatus,
                voiceReady = voiceReady,
                state = displayState,
            )

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NeuralBackdrop(accent: Color) {
    val infinite = rememberInfiniteTransition(label = "nikoBackdrop")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Restart),
        label = "backdropPhase",
    )

    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.52f, size.height * 0.28f),
                radius = size.width * 0.86f,
            ),
            radius = size.width * 0.86f,
            center = Offset(size.width * 0.52f, size.height * 0.28f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF805CFF).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.10f, size.height * 0.78f),
                radius = size.width * 0.72f,
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.10f, size.height * 0.78f),
        )

        repeat(22) { index ->
            val x = ((index * 83) % 101) / 100f * size.width
            val baseY = ((index * 47) % 97) / 100f * size.height
            val y = (baseY + sin(phase * PI * 2 + index).toFloat() * 9f)
            val alpha = 0.08f + (index % 4) * 0.025f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 0.7f + (index % 3) * 0.45f,
                center = Offset(x, y),
            )
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
                    text = "NIKO",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.3.sp,
                )
                Spacer(Modifier.size(7.dp))
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(if (autoListeningEnabled) stateAccent(state) else Color(0xFF66717B))
                }
            }
            Text(
                text = "NEURAL COMPANION",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.55.sp,
            )
        }

        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onSettings),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.055f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configuración",
                    tint = Color.White.copy(alpha = 0.82f),
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
        inputState == InputState.PREPARING -> "INICIANDO OÍDO LOCAL"
        inputState != InputState.READY -> "MICRÓFONO NO DISPONIBLE"
        webSearching -> "BUSCANDO EN INTERNET"
        state == NikoVisualState.LISTENING -> "ESCUCHANDO"
        state == NikoVisualState.THINKING -> "PENSANDO"
        state == NikoVisualState.SPEAKING -> "HABLANDO"
        else -> "LISTO PARA VOS"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFF0B111C).copy(alpha = 0.82f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(7.dp)) { drawCircle(accent) }
            Spacer(Modifier.size(8.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 9.5.sp,
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
    val accent = stateAccent(state)
    val label = when (state) {
        NikoVisualState.LISTENING -> "TE ESCUCHO"
        NikoVisualState.THINKING -> if (webSearching) "BUSCANDO" else "PROCESANDO"
        NikoVisualState.SPEAKING -> "NIKO"
        NikoVisualState.IDLE -> "NIKO"
    }
    val message = when (state) {
        NikoVisualState.LISTENING -> heardText.ifBlank { "Decime qué necesitás." }
        NikoVisualState.THINKING -> heardText.ifBlank { "Estoy trabajando en eso." }
        NikoVisualState.SPEAKING -> responseText.ifBlank { "Aquí estoy." }
        NikoVisualState.IDLE -> responseText.ifBlank { "Decí “Niko” y hablame normal." }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0A101A).copy(alpha = 0.86f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp)) {
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
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            NeuralWaveform(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(27.dp),
            )

            if (heardText.isNotBlank() && state != NikoVisualState.LISTENING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "VOS · $heardText",
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 10.sp,
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
                            color = Color.White.copy(alpha = 0.055f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                        ) {
                            Text(
                                text = "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 9.5.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
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
            val tint = mix(accent, Color(0xFF7D73FF), index / (bars - 1f))
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
        QuickAction("Linterna", Icons.Rounded.FlashlightOn, Color(0xFF4FE4F8)) {
            runCatching {
                val manager = context.getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@runCatching
                torchOn = !torchOn
                manager.setTorchMode(cameraId, torchOn)
            }
        },
        QuickAction("YouTube", Icons.Rounded.SmartDisplay, Color(0xFFFF5A67)) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) context.startActivity(launch)
            else runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com")))
            }
        },
        QuickAction("WhatsApp", Icons.Rounded.Chat, Color(0xFF54E38C)) {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let(context::startActivity)
        },
        QuickAction("Alarmas", Icons.Rounded.Alarm, Color(0xFFA97BFF)) {
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
                    color = Color.White.copy(alpha = 0.055f),
                    border = BorderStroke(1.dp, action.tint.copy(alpha = 0.22f)),
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
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WakeDock(
    enabled: Boolean,
    inputState: InputState,
    inputStatus: String,
    voiceReady: Boolean,
    state: NikoVisualState,
) {
    val ready = enabled && inputState == InputState.READY
    val accent = if (ready) stateAccent(state) else Color(0xFF69757E)
    val title = when {
        !enabled -> "NIKO EN PAUSA"
        inputState == InputState.PREPARING -> "PREPARANDO ESCUCHA LOCAL"
        inputState != InputState.READY -> "ESCUCHA NO DISPONIBLE"
        else -> "ESCUCHA AMBIENTAL ACTIVA"
    }
    val detail = when {
        inputStatus.isNotBlank() && !ready -> inputStatus.take(74)
        ready && voiceReady -> "Decí “Niko” · no necesitás tocar la pantalla"
        ready -> "Decí “Niko” · respuesta disponible en pantalla"
        else -> "Revisá Ajustes para completar la preparación"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF080D15).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.075f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.72f), accent.copy(alpha = 0.15f)),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(19.dp),
                )
            }

            Spacer(Modifier.size(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Canvas(Modifier.size(22.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                repeat(3) { index ->
                    val radius = size.minDimension * (0.12f + index * 0.12f)
                    drawCircle(
                        color = accent.copy(alpha = if (ready) 0.54f - index * 0.13f else 0.16f),
                        radius = radius,
                        center = center,
                    )
                }
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
    NikoVisualState.IDLE -> Color(0xFF5CEBFF)
    NikoVisualState.LISTENING -> Color(0xFF49F2C2)
    NikoVisualState.THINKING -> Color(0xFFA071FF)
    NikoVisualState.SPEAKING -> Color(0xFFFF72D8)
}

private fun mix(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}
