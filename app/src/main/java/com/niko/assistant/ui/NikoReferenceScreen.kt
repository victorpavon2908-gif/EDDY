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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
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
 * Premium assistant surface. The old debug-like state gallery was removed; runtime state is
 * communicated by the mascot, one compact status chip and motion. This keeps the hierarchy
 * close to polished consumer assistants instead of a component showcase.
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
    val accent = accentFor(displayState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFDFEFF),
                        Color(0xFFF8FBFF),
                        Color(0xFFF6F8FF),
                    ),
                ),
            ),
    ) {
        // Ambient color field keeps the white UI from feeling flat.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.34f),
                    radius = size.width * 0.66f,
                ),
                radius = size.width * 0.66f,
                center = Offset(size.width * 0.5f, size.height * 0.34f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) })

            Spacer(Modifier.height(6.dp))

            StatusPill(
                state = displayState,
                enabled = autoListeningEnabled,
                inputState = inputState,
                webSearching = webSearching,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                NikoHero(displayState, Modifier.size(330.dp))
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
            QuickActionsRow()
            Spacer(Modifier.height(12.dp))

            AmbientWakeBar(
                enabled = autoListeningEnabled,
                inputState = inputState,
                inputStatus = inputStatus,
                voiceReady = voiceReady,
                state = displayState,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GhostCircle(Icons.Rounded.Menu, "Menú", onSettings)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Niko",
                color = Color(0xFF0B1520),
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.size(5.dp))
            Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFF668AFF)) }
        }
        GhostCircle(Icons.Rounded.AccountCircle, "Perfil", onSettings)
    }
}

@Composable
private fun GhostCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, Color(0xFFE8EEF4)),
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color(0xFF18242D), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatusPill(
    state: NikoVisualState,
    enabled: Boolean,
    inputState: InputState,
    webSearching: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "pulse",
    )
    val accent = accentFor(state)
    val label = when {
        !enabled -> "Niko en pausa"
        inputState == InputState.PREPARING -> "Preparando escucha"
        inputState != InputState.READY -> "Escucha no disponible"
        webSearching -> "Buscando en internet"
        state == NikoVisualState.LISTENING -> "Te escucho"
        state == NikoVisualState.THINKING -> "Pensando"
        state == NikoVisualState.SPEAKING -> "Hablando"
        else -> "Listo para vos"
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(8.dp)) {
                drawCircle(
                    color = if (enabled && inputState == InputState.READY) accent.copy(alpha = pulse) else Color(0xFF9CA8B0),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(label, color = Color(0xFF53616B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
    val title = when (state) {
        NikoVisualState.LISTENING -> "Decime, te estoy escuchando."
        NikoVisualState.THINKING -> if (webSearching) "Estoy buscando eso…" else "Un segundo, lo estoy pensando…"
        NikoVisualState.SPEAKING -> responseText.ifBlank { "Aquí estoy." }
        NikoVisualState.IDLE -> responseText.ifBlank { "Decí “Niko” y hablame normal." }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFFE8EEF5)),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = accentFor(state).copy(alpha = 0.11f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = accentFor(state),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF111A22),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))
            PremiumWaveform(state = state, modifier = Modifier.fillMaxWidth().height(30.dp))

            if (heardText.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "Vos · $heardText",
                    color = Color(0xFF7A8790),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(sources.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.take(4).forEachIndexed { index, source ->
                        Surface(
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                            shape = RoundedCornerShape(10.dp),
                            color = if (webUsed) Color(0xFFF0F5FF) else Color(0xFFF5F7FA),
                        ) {
                            Text(
                                text = "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = Color(0xFF63717B),
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
private fun PremiumWaveform(state: NikoVisualState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "premiumWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (state) {
                    NikoVisualState.SPEAKING -> 440
                    NikoVisualState.LISTENING -> 650
                    NikoVisualState.THINKING -> 900
                    NikoVisualState.IDLE -> 1800
                },
            ),
            RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val bars = 28
        val slot = size.width / bars
        val center = size.height / 2f
        repeat(bars) { index ->
            val active = state != NikoVisualState.IDLE
            val raw = abs(sin((phase * PI * 2 + index * 0.48).toDouble())).toFloat()
            val amplitude = when (state) {
                NikoVisualState.SPEAKING -> 0.78f
                NikoVisualState.LISTENING -> 0.52f
                NikoVisualState.THINKING -> 0.34f
                NikoVisualState.IDLE -> 0.08f
            }
            val height = size.height * (0.10f + if (active) raw * amplitude else amplitude)
            val t = index / (bars - 1f)
            val color = blend(Color(0xFF57D9F6), Color(0xFF8667F5), t)
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.80f else 0.30f),
                topLeft = Offset(index * slot + slot * 0.31f, center - height / 2f),
                size = Size(slot * 0.30f, height),
                cornerRadius = CornerRadius(slot * 0.18f),
            )
        }
    }
}

@Composable
private fun QuickActionsRow() {
    val context = LocalContext.current
    var torchOn by remember { mutableStateOf(false) }
    val actions = listOf(
        QuickAction("Linterna", Icons.Rounded.FlashlightOn, Color(0xFF2AC4E6)) {
            runCatching {
                val manager = context.getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return@runCatching
                torchOn = !torchOn
                manager.setTorchMode(cameraId, torchOn)
            }
        },
        QuickAction("YouTube", Icons.Rounded.SmartDisplay, Color(0xFFF04444)) {
            val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) context.startActivity(launch)
            else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com"))) }
        },
        QuickAction("WhatsApp", Icons.Rounded.Chat, Color(0xFF2FCB77)) {
            context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let(context::startActivity)
        },
        QuickAction("Alarmas", Icons.Rounded.Alarm, Color(0xFF8B6BF4)) {
            runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = action.onClick),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, Color(0xFFE9EEF4)),
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 11.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(shape = CircleShape, color = action.tint.copy(alpha = 0.10f)) {
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                            Icon(action.icon, action.label, tint = action.tint, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        action.label,
                        color = Color(0xFF4B5963),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmbientWakeBar(
    enabled: Boolean,
    inputState: InputState,
    inputStatus: String,
    voiceReady: Boolean,
    state: NikoVisualState,
) {
    val accent = accentFor(state)
    val title = when {
        !enabled -> "Activación por voz en pausa"
        inputState == InputState.PREPARING -> "Preparando el oído de Niko"
        inputState != InputState.READY -> "Revisá la escucha en Ajustes"
        state == NikoVisualState.LISTENING -> "Hablá, no hace falta tocar nada"
        else -> "Decí Niko para empezar"
    }
    val detail = when {
        inputStatus.isNotBlank() -> inputStatus
        !voiceReady -> "La respuesta puede mostrarse en pantalla aunque la voz local no esté preparada."
        else -> "Escucha local · sin botón"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF111820),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.size(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    color = Color(0xFFAAB7C0),
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GhostSettingsButton()
        }
    }
}

@Composable
private fun GhostSettingsButton() {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.size(36.dp).clickable { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Settings, "Ajustes", tint = Color.White.copy(alpha = 0.86f), modifier = Modifier.size(18.dp))
        }
    }
}

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

private fun accentFor(state: NikoVisualState): Color = when (state) {
    NikoVisualState.IDLE -> Color(0xFF57D9F6)
    NikoVisualState.LISTENING -> Color(0xFF3ED3D8)
    NikoVisualState.THINKING -> Color(0xFF8A63F6)
    NikoVisualState.SPEAKING -> Color(0xFF5B82FF)
}

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
