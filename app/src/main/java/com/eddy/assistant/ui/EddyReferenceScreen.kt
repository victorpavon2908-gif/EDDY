package com.eddy.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
        !autoListeningEnabled -> "Pausado"
        !voiceReady -> "Preparando voz"
        visualState == EddyVisualState.LISTENING -> "Escuchando"
        visualState == EddyVisualState.THINKING && webUsed -> "Investigando"
        visualState == EddyVisualState.THINKING -> "Pensando"
        visualState == EddyVisualState.SPEAKING -> "Hablando"
        else -> "Listo"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9FCFB),
                        EddyCloud,
                        Color(0xFFEDF5F2),
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AmbientGlow(
            state = visualState,
            enabled = autoListeningEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            EddyTopBar(
                statusText = statusText,
                autoListeningEnabled = autoListeningEnabled,
                webUsed = webUsed,
                onToggleAssistant = onToggleAssistant,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                EddyHero(
                    state = visualState,
                    modifier = Modifier.fillMaxSize(),
                )

                StateCapsule(
                    visualState = visualState,
                    webUsed = webUsed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }

            ConversationPanel(
                heardText = heardText,
                responseText = responseText,
                webUsed = webUsed,
                sources = webSources,
            )
        }
    }
}

@Composable
private fun AmbientGlow(
    state: EddyVisualState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eddyAmbient")
    val glow by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = when (state) {
            EddyVisualState.LISTENING -> 0.34f
            EddyVisualState.THINKING -> 0.28f
            EddyVisualState.SPEAKING -> 0.36f
            EddyVisualState.IDLE -> 0.20f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    EddyVisualState.SPEAKING -> 560
                    EddyVisualState.LISTENING -> 900
                    EddyVisualState.THINKING -> 1200
                    EddyVisualState.IDLE -> 2200
                }
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientGlow",
    )

    Canvas(modifier = modifier) {
        val alpha = if (enabled) glow else 0.08f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EddyMint.copy(alpha = alpha),
                    EddyMintSoft.copy(alpha = alpha * 0.45f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.minDimension * 0.68f,
            ),
            radius = size.minDimension * 0.68f,
            center = Offset(size.width * 0.5f, size.height * 0.42f),
        )
    }
}

@Composable
private fun EddyTopBar(
    statusText: String,
    autoListeningEnabled: Boolean,
    webUsed: Boolean,
    onToggleAssistant: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = EddyBlack,
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "E",
                            color = EddyMint,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                Spacer(Modifier.width(11.dp))

                Column {
                    Text(
                        text = "EDDY",
                        color = EddyBlack,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 4.4.sp,
                    )
                    Text(
                        text = "ASISTENTE PERSONAL",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.15.sp,
                    )
                }
            }
        }

        StatusPill(
            status = statusText,
            active = autoListeningEnabled,
            webUsed = webUsed,
        )

        if (onToggleAssistant != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleAssistant),
                shape = CircleShape,
                color = if (autoListeningEnabled) Color.White.copy(alpha = 0.82f) else EddyMintSoft,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (autoListeningEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (autoListeningEnabled) "Pausar EDDY" else "Activar EDDY",
                        tint = EddyBlack,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    status: String,
    active: Boolean,
    webUsed: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            webUsed -> Color(0xFFE7F0FF)
            active -> EddyMintSoft
            else -> Color(0xFFE9ECEB)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                webUsed -> EddyBlue.copy(alpha = 0.40f)
                active -> EddyMint.copy(alpha = 0.45f)
                else -> EddySoftGray
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(7.dp)) {
                drawCircle(
                    color = when {
                        webUsed -> EddyBlue
                        active -> EddyMintDeep
                        else -> Color(0xFF94A09C)
                    }
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.uppercase(),
                color = EddyGraphite,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun StateCapsule(
    visualState: EddyVisualState,
    webUsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when {
        webUsed && visualState == EddyVisualState.THINKING -> "Buscando y verificando fuentes"
        webUsed -> "Respuesta respaldada por la web"
        visualState == EddyVisualState.LISTENING -> "Te estoy escuchando"
        visualState == EddyVisualState.THINKING -> "Procesando tu solicitud"
        visualState == EddyVisualState.SPEAKING -> "Hablando con vos"
        else -> "Decí “EDDY” para llamarme"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.80f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (webUsed) Icons.Rounded.Language else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (webUsed) EddyBlue else EddyMintDeep,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                color = EddyGraphite,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ConversationPanel(
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
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color(0x180E1714),
                spotColor = Color(0x180E1714),
            )
            .animateContentSize(),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFFFCFEFD).copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            AnimatedVisibility(visible = heardText.isNotBlank()) {
                Column {
                    Text(
                        text = "VOS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = heardText,
                        color = EddyGraphite,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = EddyBlack,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MiniEddyMark(modifier = Modifier.size(25.dp))
                    }
                }

                Spacer(Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EDDY",
                            color = EddyBlack,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp,
                        )
                        if (webUsed) {
                            Spacer(Modifier.width(7.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFE8F1FF),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = EddyBlue,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "WEB",
                                        color = Color(0xFF315F9C),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = responseText.ifBlank { "Listo para ayudarte. Decime qué ocupás." },
                        color = EddyBlack,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (sources.isNotEmpty()) 4 else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(visible = sources.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = "FUENTES VERIFICADAS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sources.take(6).forEachIndexed { index, source ->
                            SourceChip(
                                index = index + 1,
                                source = source,
                                onClick = { runCatching { uriHandler.openUri(source.url) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    index: Int,
    source: EddyWebSource,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F6F4),
        border = androidx.compose.foundation.BorderStroke(1.dp, EddySoftGray),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(21.dp),
                shape = CircleShape,
                color = EddyBlack,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = index.toString(),
                        color = EddyMint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = source.title,
                color = EddyGraphite,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(128.dp),
            )
        }
    }
}
