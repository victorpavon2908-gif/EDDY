package com.eddy.assistant.ui

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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddy.assistant.ai.EddyWebSource

@Composable
internal fun EddyReferenceScreen(
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    voiceReady: Boolean,
    autoListeningEnabled: Boolean,
    webUsed: Boolean = false,
    webSources: List<EddyWebSource> = emptyList(),
    onToggleAssistant: (() -> Unit)? = null,
) {
    val statusText = when {
        !autoListeningEnabled -> "PAUSADO"
        !voiceReady -> "NÚCLEO"
        visualState == EddyVisualState.LISTENING -> "CON VOS"
        visualState == EddyVisualState.THINKING && webUsed -> "WEB"
        visualState == EddyVisualState.THINKING -> "PENSANDO"
        visualState == EddyVisualState.SPEAKING -> "HABLANDO"
        else -> "ATENTO"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF9FCFB),
                        Color(0xFFF1F7F4),
                        Color(0xFFE8F1ED),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ExpressiveHeader(
            statusText = statusText,
            active = autoListeningEnabled,
            webUsed = webUsed,
            onToggleAssistant = onToggleAssistant,
        )

        LocalCapabilityRail(webUsed = webUsed)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            EddyHero(
                state = visualState,
                modifier = Modifier.fillMaxSize(),
            )

            StateCapsule(
                text = stateLine(visualState, autoListeningEnabled, webUsed),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }

        ConversationDeck(
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = webSources,
        )
    }
}

@Composable
private fun ExpressiveHeader(
    statusText: String,
    active: Boolean,
    webUsed: Boolean,
    onToggleAssistant: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CutCornerShape(topStart = 3.dp, topEnd = 13.dp, bottomStart = 13.dp, bottomEnd = 3.dp),
            color = EddyBlack,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("E", color = EddyMint, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
        }

        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "EDDY",
                color = EddyBlack,
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 3.8.sp,
            )
            Text(
                "LOCAL PERSONAL INTELLIGENCE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.05.sp,
            )
        }

        Surface(
            shape = CutCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp),
            color = if (webUsed) Color(0xFFE4EFFF) else if (active) EddyMintSoft else Color(0xFFE7ECEA),
            border = BorderStroke(1.dp, if (webUsed) EddyBlue.copy(alpha = 0.48f) else EddySoftGray),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActivityDot(active = active, webUsed = webUsed)
                Spacer(Modifier.width(6.dp))
                Text(statusText, style = MaterialTheme.typography.labelMedium, color = EddyGraphite)
            }
        }

        if (onToggleAssistant != null) {
            Spacer(Modifier.width(7.dp))
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onToggleAssistant),
                shape = CutCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp),
                color = EddyBlack,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active) "Pausar EDDY" else "Activar EDDY",
                        tint = EddyMint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityDot(active: Boolean, webUsed: Boolean) {
    val transition = rememberInfiniteTransition(label = "eddyHeaderPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "headerAlpha",
    )
    Canvas(Modifier.size(7.dp)) {
        drawCircle(
            color = when {
                webUsed -> EddyBlue
                active -> EddyMintDeep
                else -> Color(0xFF8C9894)
            }.copy(alpha = if (active) alpha else 1f),
        )
    }
}

@Composable
private fun LocalCapabilityRail(webUsed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CapabilityChip("LOCAL CORE", EddyMintDeep)
        CapabilityChip("VOICE ID", Color(0xFF546C63))
        CapabilityChip("PRIVATE AUDIO", Color(0xFF546C63))
        CapabilityChip(if (webUsed) "WEB ACTIVE" else "WEB ON DEMAND", if (webUsed) EddyBlue else Color(0xFF546C63))
    }
}

@Composable
private fun CapabilityChip(text: String, accent: Color) {
    Surface(
        shape = CutCornerShape(topStart = 2.dp, topEnd = 7.dp, bottomStart = 7.dp, bottomEnd = 2.dp),
        color = Color.White.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(4.dp)) { drawCircle(accent) }
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = EddyGraphite, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun StateCapsule(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(topStart = 3.dp, topEnd = 13.dp, bottomStart = 13.dp, bottomEnd = 3.dp),
        color = Color(0xEEFFFFFF),
        border = BorderStroke(1.dp, EddySoftGray),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = EddyGraphite,
            letterSpacing = 0.45.sp,
        )
    }
}

@Composable
private fun ConversationDeck(
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    sources: List<EddyWebSource>,
) {
    val uriHandler = LocalUriHandler.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .animateContentSize(),
        shape = CutCornerShape(topStart = 6.dp, topEnd = 27.dp, bottomStart = 27.dp, bottomEnd = 6.dp),
        color = Color(0xFFFCFEFD),
        border = BorderStroke(1.dp, Color(0xFFC9D8D2)),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
            AnimatedVisibility(heardText.isNotBlank()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VOS", style = MaterialTheme.typography.labelMedium, color = EddyMintDeep, letterSpacing = 1.sp)
                        Spacer(Modifier.width(8.dp))
                        Canvas(Modifier.weight(1f).height(1.dp)) {
                            drawLine(EddySoftGray, Offset.Zero, Offset(size.width, 0f), 1f)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        heardText,
                        color = EddyGraphite,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(11.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EDDY", style = MaterialTheme.typography.labelLarge, color = EddyBlack, letterSpacing = 1.4.sp)
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CutCornerShape(topStart = 1.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 1.dp),
                    color = EddyMintSoft.copy(alpha = 0.65f),
                ) {
                    Text(
                        "LOCAL",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EddyMintDeep,
                    )
                }
                if (webUsed) {
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Rounded.Language, null, tint = EddyBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("WEB", style = MaterialTheme.typography.labelMedium, color = Color(0xFF315F9C))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                responseText.ifBlank { "Estoy atento en local. Decí EDDY cuando querás hablar conmigo." },
                color = EddyBlack,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (sources.isEmpty()) 5 else 4,
                overflow = TextOverflow.Ellipsis,
            )

            AnimatedVisibility(sources.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text("FUENTES VERIFICABLES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        sources.take(6).forEachIndexed { index, source ->
                            Surface(
                                modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                                shape = CutCornerShape(topStart = 2.dp, topEnd = 9.dp, bottomStart = 9.dp, bottomEnd = 2.dp),
                                color = Color(0xFFF1F6F4),
                                border = BorderStroke(1.dp, EddySoftGray),
                            ) {
                                Text(
                                    "${index + 1}  ${source.title}",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EddyGraphite,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun stateLine(state: EddyVisualState, active: Boolean, webUsed: Boolean): String = when {
    !active -> "EDDY EN PAUSA"
    webUsed && state == EddyVisualState.THINKING -> "BUSCANDO · CONTRASTANDO · SINTETIZANDO"
    state == EddyVisualState.LISTENING -> "TE ESCUCHO · SOLO ESTA CONVERSACIÓN"
    state == EddyVisualState.THINKING -> "PENSANDO LOCALMENTE"
    state == EddyVisualState.SPEAKING -> "AHORA HABLO YO"
    else -> "ATENTO · SOLO EDDY ME ACTIVA"
}
