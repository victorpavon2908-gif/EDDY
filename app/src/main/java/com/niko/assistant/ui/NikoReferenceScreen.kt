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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
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
 * Main assistant surface modeled after the playful NIKO concept: character first,
 * conversation second, shortcuts third. Runtime state changes the character and waveform
 * instead of showing technical diagnostics as the primary UI.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFCFEFF),
                        Color(0xFFF7FAFE),
                        Color(0xFFFBF9FF),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NikoTopBar(onSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) })

        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(332.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(322.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                stateAccent(displayState).copy(alpha = 0.13f),
                                stateAccent(displayState).copy(alpha = 0.035f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                NikoHero(displayState, Modifier.size(306.dp))
            }

            StateBadge(
                state = displayState,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 8.dp),
            )
        }

        ConversationPanel(
            state = displayState,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            webSearching = webSearching,
            sources = webSources,
        )

        Spacer(Modifier.height(14.dp))

        QuickActionsRow()

        Spacer(Modifier.height(14.dp))

        StatePreviewPanel(activeState = displayState)

        Spacer(Modifier.height(14.dp))

        VoiceWakePill(
            enabled = autoListeningEnabled,
            inputState = inputState,
            inputStatus = inputStatus,
            voiceReady = voiceReady,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NikoTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SoftCircleButton(Icons.Rounded.Menu, "Menú", onSettings)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Niko",
                color = Color(0xFF0A1621),
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.size(4.dp))
            Canvas(Modifier.size(9.dp)) { drawCircle(Color(0xFF668CFF)) }
        }
        SoftCircleButton(Icons.Rounded.AccountCircle, "Ajustes", onSettings)
    }
}

@Composable
private fun SoftCircleButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFEAF0F3)),
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color(0xFF16232B), modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun StateBadge(state: NikoVisualState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, stateAccent(state).copy(alpha = 0.20f)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = stateAccent(state),
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stateLabel(state),
                color = stateAccent(state),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ConversationPanel(
    state: NikoVisualState,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    webSearching: Boolean,
    sources: List<NikoWebSource>,
) {
    val uriHandler = LocalUriHandler.current
    val prompt = when (state) {
        NikoVisualState.LISTENING -> "Te escucho, decime."
        NikoVisualState.THINKING -> if (webSearching) "Estoy buscando eso…" else "Déjame pensarlo…"
        NikoVisualState.SPEAKING -> responseText.ifBlank { "Aquí estoy." }
        NikoVisualState.IDLE -> responseText.ifBlank { "Hola, ¿en qué te ayudo?" }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE9EFF3)),
        shadowElevation = 7.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                prompt,
                color = Color(0xFF101B25),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            AnimatedWaveform(state = state, modifier = Modifier.fillMaxWidth().height(35.dp))
            if (heardText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vos: $heardText",
                    color = Color(0xFF78858E),
                    fontSize = 11.5.sp,
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
                            shape = RoundedCornerShape(10.dp),
                            color = if (webUsed) Color(0xFFF0F6FF) else Color(0xFFF5F8FA),
                        ) {
                            Text(
                                "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = Color(0xFF60717E),
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

@Composable
private fun AnimatedWaveform(state: NikoVisualState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nikoWave")
    val phase by transition.animateFloat(
        0f,
        1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == NikoVisualState.SPEAKING) 430 else 720),
            RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val count = 30
        val gap = size.width / count
        val centerY = size.height / 2f
        repeat(count) { index ->
            val active = state == NikoVisualState.LISTENING || state == NikoVisualState.SPEAKING || state == NikoVisualState.THINKING
            val wave = if (active) abs(sin((phase * PI * 2 + index * 0.55).toDouble())).toFloat() else 0.18f
            val height = size.height * (0.16f + wave * if (state == NikoVisualState.SPEAKING) 0.72f else 0.50f)
            val fraction = index / (count - 1f)
            val color = lerpColor(Color(0xFF56DFF4), Color(0xFF9C67F7), fraction)
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.90f else 0.38f),
                topLeft = Offset(index * gap + gap * 0.28f, centerY - height / 2f),
                size = Size(gap * 0.38f, height),
                cornerRadius = CornerRadius(gap * 0.19f),
            )
        }
    }
}

@Composable
private fun QuickActionsRow() {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    val actions = listOf(
        QuickAction("Linterna", Icons.Rounded.FlashlightOn, Color(0xFF21BFE5)) {
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
        QuickAction("WhatsApp", Icons.Rounded.Chat, Color(0xFF2CC86B)) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (launch != null) context.startActivity(launch)
        },
        QuickAction("Alarmas", Icons.Rounded.Alarm, Color(0xFF8B65F5)) {
            runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        actions.forEach { action ->
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = action.onClick),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE9EFF3)),
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 13.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, action.label, tint = action.tint, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.height(7.dp))
                    Text(action.label, color = Color(0xFF28343D), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatePreviewPanel(activeState: NikoVisualState) {
    val states = listOf(
        NikoVisualState.IDLE,
        NikoVisualState.LISTENING,
        NikoVisualState.THINKING,
        NikoVisualState.SPEAKING,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(27.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE9EFF3)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            states.forEach { state ->
                val active = state == activeState
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) stateAccent(state).copy(alpha = 0.085f) else Color.Transparent,
                    border = if (active) BorderStroke(1.dp, stateAccent(state).copy(alpha = 0.25f)) else null,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MiniNikoFace(state, Modifier.size(42.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stateLabel(state),
                            color = if (active) stateAccent(state) else Color(0xFF74808A),
                            fontSize = 9.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniNikoFace(state: NikoVisualState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val accent = stateAccent(state)
        drawCircle(Color(0xFF111719), radius = size.minDimension * 0.46f, center = center)
        drawRoundRect(
            color = Color(0xFF040607),
            topLeft = Offset(size.width * 0.18f, size.height * 0.26f),
            size = Size(size.width * 0.64f, size.height * 0.45f),
            cornerRadius = CornerRadius(size.width * 0.18f),
        )
        if (state == NikoVisualState.IDLE) {
            drawLine(accent, Offset(size.width * 0.29f, size.height * 0.44f), Offset(size.width * 0.41f, size.height * 0.44f), size.width * 0.035f)
            drawLine(accent, Offset(size.width * 0.59f, size.height * 0.44f), Offset(size.width * 0.71f, size.height * 0.44f), size.width * 0.035f)
        } else {
            drawCircle(accent, size.width * 0.035f, Offset(size.width * 0.35f, size.height * 0.44f))
            drawCircle(accent, size.width * 0.035f, Offset(size.width * 0.65f, size.height * 0.44f))
        }
        if (state == NikoVisualState.SPEAKING) {
            drawOval(accent, topLeft = Offset(size.width * 0.44f, size.height * 0.54f), size = Size(size.width * 0.12f, size.height * 0.10f))
        } else {
            drawLine(accent, Offset(size.width * 0.44f, size.height * 0.59f), Offset(size.width * 0.56f, size.height * 0.59f), size.width * 0.025f)
        }
        if (state == NikoVisualState.THINKING) {
            drawCircle(Color(0xFF9C67F7), size.width * 0.035f, Offset(size.width * 0.82f, size.height * 0.18f))
        }
    }
}

@Composable
private fun VoiceWakePill(
    enabled: Boolean,
    inputState: InputState,
    inputStatus: String,
    voiceReady: Boolean,
) {
    val ready = enabled && inputState == InputState.READY
    val title = when {
        !enabled -> "Niko está en pausa"
        inputState == InputState.PREPARING -> "Preparando escucha…"
        inputState != InputState.READY -> "Revisá el micrófono"
        else -> "Decí “NIKO” para hablar"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (ready) Brush.horizontalGradient(listOf(Color(0xFF4DD5EF), Color(0xFF8C66F4)))
                    else Brush.horizontalGradient(listOf(Color(0xFFE3E9ED), Color(0xFFD7DEE5))),
                    RoundedCornerShape(30.dp),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.size(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    if (inputStatus.isNotBlank() && !ready) {
                        Text(inputStatus.take(72), color = Color.White.copy(alpha = 0.84f), fontSize = 9.5.sp, textAlign = TextAlign.Center)
                    } else if (!voiceReady && ready) {
                        Text("La respuesta también aparecerá en pantalla", color = Color.White.copy(alpha = 0.82f), fontSize = 9.5.sp)
                    }
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

private fun stateLabel(state: NikoVisualState): String = when (state) {
    NikoVisualState.IDLE -> "Idle"
    NikoVisualState.LISTENING -> "Escuchando"
    NikoVisualState.THINKING -> "Pensando"
    NikoVisualState.SPEAKING -> "Hablando"
}

private fun stateAccent(state: NikoVisualState): Color = when (state) {
    NikoVisualState.IDLE -> Color(0xFF5BCFEA)
    NikoVisualState.LISTENING -> Color(0xFF27C8F0)
    NikoVisualState.THINKING -> Color(0xFF916AF5)
    NikoVisualState.SPEAKING -> Color(0xFF39BDE9)
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}
