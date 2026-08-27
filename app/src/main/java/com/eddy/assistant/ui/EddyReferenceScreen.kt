package com.eddy.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        !voiceReady -> "VOZ"
        visualState == EddyVisualState.LISTENING -> "ESCUCHANDO"
        visualState == EddyVisualState.THINKING && webUsed -> "INVESTIGANDO"
        visualState == EddyVisualState.THINKING -> "PENSANDO"
        visualState == EddyVisualState.SPEAKING -> "HABLANDO"
        else -> "LISTO"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF9FBFA), Color(0xFFF0F5F3), Color(0xFFEAF1EE)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        CompactHeader(
            statusText = statusText,
            active = autoListeningEnabled,
            webUsed = webUsed,
            onToggleAssistant = onToggleAssistant,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            EddyHero(
                state = visualState,
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
                shape = CutCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 3.dp),
                color = Color(0xEFFFFFFF),
                border = BorderStroke(1.dp, EddySoftGray),
            ) {
                Text(
                    text = stateLine(visualState, autoListeningEnabled, webUsed),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = EddyGraphite,
                )
            }
        }

        CompactConversation(
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = webSources,
        )
    }
}

@Composable
private fun CompactHeader(
    statusText: String,
    active: Boolean,
    webUsed: Boolean,
    onToggleAssistant: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CutCornerShape(topStart = 2.dp, topEnd = 11.dp, bottomStart = 11.dp, bottomEnd = 2.dp),
            color = EddyBlack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("E", color = EddyMint, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "EDDY",
                color = EddyBlack,
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 3.4.sp,
            )
            Text(
                "PERSONAL INTELLIGENCE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.1.sp,
            )
        }

        Surface(
            shape = CutCornerShape(topStart = 2.dp, topEnd = 9.dp, bottomStart = 9.dp, bottomEnd = 2.dp),
            color = if (webUsed) Color(0xFFE6F0FF) else if (active) EddyMintSoft else Color(0xFFE7ECEA),
            border = BorderStroke(1.dp, if (webUsed) EddyBlue.copy(alpha = 0.5f) else EddySoftGray),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(6.dp)) {
                    drawCircle(if (webUsed) EddyBlue else if (active) EddyMintDeep else Color(0xFF8C9894))
                }
                Spacer(Modifier.width(6.dp))
                Text(statusText, style = MaterialTheme.typography.labelMedium, color = EddyGraphite)
            }
        }

        if (onToggleAssistant != null) {
            Spacer(Modifier.width(7.dp))
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(onClick = onToggleAssistant),
                shape = CutCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp),
                color = EddyBlack,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active) "Pausar EDDY" else "Activar EDDY",
                        tint = EddyMint,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactConversation(
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
        shape = CutCornerShape(topStart = 5.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 5.dp),
        color = Color(0xFFFBFDFC),
        border = BorderStroke(1.dp, Color(0xFFCFDAD6)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AnimatedVisibility(heardText.isNotBlank()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VOS", style = MaterialTheme.typography.labelMedium, color = EddyMintDeep)
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
                Text("EDDY", style = MaterialTheme.typography.labelLarge, color = EddyBlack, letterSpacing = 1.2.sp)
                if (webUsed) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Language, null, tint = EddyBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("WEB", style = MaterialTheme.typography.labelMedium, color = Color(0xFF315F9C))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                responseText.ifBlank { "Decí EDDY y después tu orden." },
                color = EddyBlack,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (sources.isEmpty()) 5 else 4,
                overflow = TextOverflow.Ellipsis,
            )

            AnimatedVisibility(sources.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text("FUENTES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        sources.take(6).forEachIndexed { index, source ->
                            Surface(
                                modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                                shape = CutCornerShape(topStart = 2.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 2.dp),
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
    webUsed && state == EddyVisualState.THINKING -> "BUSCANDO · CONTRASTANDO · RESPONDIENDO"
    state == EddyVisualState.LISTENING -> "TE ESCUCHO · DECIME"
    state == EddyVisualState.THINKING -> "PROCESANDO TU ORDEN"
    state == EddyVisualState.SPEAKING -> "HABLANDO CON VOS"
    else -> "DECÍ EDDY PARA ACTIVARME"
}
