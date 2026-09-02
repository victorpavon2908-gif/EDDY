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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niko.assistant.AiSettingsActivity
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.background.NikoRuntimeState.InputState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Product UI for NIKO.
 *
 * This screen deliberately avoids demo-only controls. The live runtime state is communicated
 * through the companion, the voice waveform and a single status capsule. Motion patterns were
 * refined after studying open-source Jetpack Compose hands-free assistant and waveform UIs.
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8FBFF),
                        Color(0xFFF5F8FE),
                        Color(0xFFFAF8FF),
                    ),
                ),
            ),
    ) {
        AmbientBackground(displayState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PremiumTopBar(
                state = displayState,
                onSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(282.dp),
                contentAlignment = Alignment.Center,
            ) {
                NikoHero(
                    state = displayState,
                    modifier = Modifier.size(268.dp),
                )
            }

            LiveStateCapsule(
                state = displayState,
                enabled = autoListeningEnabled,
                inputState = inputState,
                webSearching = webSearching,
            )

            Spacer(Modifier.height(14.dp))

            ConversationCard(
                state = displayState,
                heardText = heardText,
                responseText = responseText,
                webUsed = webUsed,
                webSearching = webSearching,
                sources = webSources,
            )

            Spacer(Modifier.height(14.dp))

            QuickActionsStrip()

            Spacer(Modifier.height(14.dp))

            VoiceDock(
                enabled = autoListeningEnabled,
                inputState = inputState,
                inputStatus = inputStatus,
                voiceReady = voiceReady,
            )

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AmbientBackground(state: NikoVisualState) {
    val accent = stateAccent(state)
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.025f), Color.Transparent),
                center = Offset(size.width * 0.18f, size.height * 0.18f),
                radius = size.width * 0.72f,
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.18f, size.height * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9C7BFF).copy(alpha = 0.07f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.48f),
                radius = size.width * 0.58f,
            ),
            radius = size.width * 0.58f,
            center = Offset(size.width * 0.88f, size.height * 0.48f),
        )
    }
}

@Composable
private fun PremiumTopBar(state: NikoVisualState, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Niko",
                color = Color(0xFF0A1520),
                fontSize = 31.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.size(6.dp))
            Canvas(Modifier.size(9.dp)) { drawCircle(stateAccent(state)) }
        }

        Surface(
            modifier = Modifier.size(44.dp).clickable(onClick = onSettings),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFFE5EBF2)),
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configuración",
                    tint = Color(0xFF22303A),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveStateCapsule(
    state: NikoVisualState,
    enabled: Boolean,
    inputState: InputState,
    webSearching: Boolean,
) {
    val active = enabled && inputState == InputState.READY
    val accent = if (active) stateAccent(state) else Color(0xFF8D99A2)
    val title = when {
        !enabled -> "Niko está en pausa"
        inputState == InputState.PREPARING -> "Preparando escucha"
        inputState != InputState.READY -> "Micrófono no disponible"
        state == NikoVisualState.LISTENING -> "Te escucho"
        state == NikoVisualState.THINKING && webSearching -> "Buscando información"
        state == NikoVisualState.THINKING -> "Pensando"
        state == NikoVisualState.SPEAKING -> "Hablando"
        else -> "Listo · decí Niko"
    }

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                color = Color(0xFF34414A),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ConversationCard(
    state: NikoVisualState,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    webSearching: Boolean,
    sources: List<NikoWebSource>,
) {
    val uriHandler = LocalUriHandler.current
    val heading = when (state) {
        NikoVisualState.LISTENING -> "Te escucho"
        NikoVisualState.THINKING -> if (webSearching) "Estoy buscando eso…" else "Estoy pensando…"
        NikoVisualState.SPEAKING -> "Niko"
        NikoVisualState.IDLE -> "Niko"
    }
    val message = when (state) {
        NikoVisualState.LISTENING -> heardText.ifBlank { "Decime qué necesitás." }
        NikoVisualState.THINKING -> heardText.ifBlank { "Procesando tu petición." }
        NikoVisualState.SPEAKING -> responseText.ifBlank { "Aquí estoy." }
        NikoVisualState.IDLE -> responseText.ifBlank { "Hola. Decí “Niko” y hablame normal." }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color(0xFFE5ECF3)),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(stateAccent(state).copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (webUsed || webSearching) Icons.Rounded.Language else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = stateAccent(state),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = heading,
                        color = Color(0xFF16232D),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = message,
                        color = Color(0xFF4E5D68),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(13.dp))
            VoiceWaveform(
                state = state,
                modifier = Modifier.fillMaxWidth().height(30.dp),
            )

            if (heardText.isNotBlank() && state != NikoVisualState.LISTENING) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Vos: $heardText",
                    color = Color(0xFF7A8790),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(sources.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 9.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.take(4).forEachIndexed { index, source ->
                        Surface(
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF2F6FB),
                        ) {
                            Text(
                                text = "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                color = Color(0xFF5E6D78),
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight Canvas waveform. The spike geometry follows common Compose waveform patterns:
 * equal-width rounded spikes, centered alignment and animated amplitude by assistant state.
 */
@Composable
private fun VoiceWaveform(state: NikoVisualState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "premiumVoiceWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NikoVisualState.SPEAKING -> 430
                    NikoVisualState.LISTENING -> 620
                    NikoVisualState.THINKING -> 900
                    NikoVisualState.IDLE -> 1_600
                },
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(modifier) {
        val count = 28
        val cell = size.width / count
        val active = state != NikoVisualState.IDLE
        repeat(count) { index ->
            val harmonic = abs(sin((phase * PI * 2 + index * 0.62).toDouble())).toFloat()
            val envelope = 0.55f + 0.45f * sin(index * PI / (count - 1)).toFloat().coerceAtLeast(0f)
            val intensity = when (state) {
                NikoVisualState.SPEAKING -> 0.92f
                NikoVisualState.LISTENING -> 0.64f
                NikoVisualState.THINKING -> 0.42f
                NikoVisualState.IDLE -> 0.10f
            }
            val h = size.height * (0.12f + if (active) harmonic * envelope * intensity else 0.04f)
            val fraction = index / (count - 1f)
            val color = mix(Color(0xFF54D9F3), Color(0xFF936CF5), fraction)
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.86f else 0.26f),
                topLeft = Offset(index * cell + cell * 0.31f, size.height / 2f - h / 2f),
                size = Size(cell * 0.38f, h),
                cornerRadius = CornerRadius(cell * 0.19f),
            )
        }
    }
}

@Composable
private fun QuickActionsStrip() {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    val actions = listOf(
        QuickAction("Linterna", Icons.Rounded.FlashlightOn, Color(0xFF22BEDB)) {
            runCatching {
                val manager = context.getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@runCatching
                torchOn = !torchOn
                manager.setTorchMode(cameraId, torchOn)
            }
        },
        QuickAction("YouTube", Icons.Rounded.SmartDisplay, Color(0xFFFF3B30)) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) context.startActivity(launch)
            else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com"))) }
        },
        QuickAction("WhatsApp", Icons.Rounded.Chat, Color(0xFF2ACB70)) {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let(context::startActivity)
        },
        QuickAction("Alarmas", Icons.Rounded.Alarm, Color(0xFF8D6AF4)) {
            runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        actions.forEach { action ->
            Surface(
                modifier = Modifier.width(104.dp).clickable(onClick = action.onClick),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color(0xFFE6EDF3)),
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(30.dp).background(action.tint.copy(alpha = 0.11f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = action.tint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = action.label,
                        color = Color(0xFF394750),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceDock(
    enabled: Boolean,
    inputState: InputState,
    inputStatus: String,
    voiceReady: Boolean,
) {
    val ready = enabled && inputState == InputState.READY
    val title = when {
        !enabled -> "Niko está en pausa"
        inputState == InputState.PREPARING -> "Preparando el oído de Niko…"
        inputState != InputState.READY -> "Revisá el micrófono"
        else -> "Decí “Niko” y hablá normal"
    }
    val subtitle = when {
        inputStatus.isNotBlank() && !ready -> inputStatus.take(86)
        !voiceReady && ready -> "Te voy a responder también en pantalla."
        ready -> "No necesitás tocar ningún botón."
        else -> "Abrí Ajustes para revisar la escucha."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF10171C),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (ready) Brush.radialGradient(listOf(Color(0xFF70E9FA), Color(0xFF6D77F5)))
                        else Brush.radialGradient(listOf(Color(0xFF7C878F), Color(0xFF4D565D))),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
    NikoVisualState.IDLE -> Color(0xFF64CFE8)
    NikoVisualState.LISTENING -> Color(0xFF38D6F3)
    NikoVisualState.THINKING -> Color(0xFF8B70F6)
    NikoVisualState.SPEAKING -> Color(0xFF4ACFEA)
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
