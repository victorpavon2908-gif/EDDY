package com.eddy.assistant.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddy.assistant.AiSettingsActivity
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
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFEFFFF),
                        Color(0xFFFBFDFC),
                        Color(0xFFF5FAF8),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.69f)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            EddyHero(
                state = visualState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusBlock(
                state = visualState,
                active = autoListeningEnabled,
                voiceReady = voiceReady,
                webUsed = webUsed,
            )
            Spacer(Modifier.height(28.dp))
            ControlDock(
                visualState = visualState,
                active = autoListeningEnabled,
                heardText = heardText,
                responseText = responseText,
                webUsed = webUsed,
                sources = webSources,
                onToggleAssistant = onToggleAssistant,
                onOpenSettings = { context.startActivity(Intent(context, AiSettingsActivity::class.java)) },
            )
        }
    }
}

@Composable
private fun StatusBlock(
    state: EddyVisualState,
    active: Boolean,
    voiceReady: Boolean,
    webUsed: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "eddyStatusPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "statusAlpha",
    )

    val title = when {
        !active -> "EN PAUSA"
        !voiceReady -> "PREPARANDO VOZ..."
        state == EddyVisualState.THINKING && webUsed -> "BUSCANDO EN INTERNET..."
        state == EddyVisualState.THINKING -> "PENSANDO..."
        state == EddyVisualState.SPEAKING -> "HABLANDO..."
        state == EddyVisualState.LISTENING -> "TE ESCUCHO..."
        else -> "ESCUCHANDO..."
    }
    val subtitle = when {
        !active -> "Tocá el micrófono para activar EDDY"
        !voiceReady -> "Preparando el sistema de voz"
        state == EddyVisualState.LISTENING -> "Decime qué necesitás"
        state == EddyVisualState.THINKING && webUsed -> "Consultando y contrastando fuentes"
        state == EddyVisualState.THINKING -> "Un momento"
        state == EddyVisualState.SPEAKING -> "Ahora hablo yo"
        else -> "Di “EDDY” para hablar conmigo"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) {
            drawCircle(
                if (webUsed && state == EddyVisualState.THINKING) EddyBlue.copy(alpha = alpha)
                else if (active) EddyMintDeep.copy(alpha = alpha)
                else Color(0xFFA7AFAC),
            )
        }
        Spacer(Modifier.size(9.dp))
        Text(
            title,
            color = Color(0xFF111514),
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            letterSpacing = 0.5.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        color = Color(0xFF7A827F),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ControlDock(
    visualState: EddyVisualState,
    active: Boolean,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    sources: List<EddyWebSource>,
    onToggleAssistant: (() -> Unit)?,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FloatingCircleButton(active = active, onClick = { onToggleAssistant?.invoke() })
        GlassStatusCard(
            modifier = Modifier.weight(1f),
            visualState = visualState,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = sources,
        )
        Surface(
            modifier = Modifier
                .size(58.dp)
                .shadow(12.dp, CircleShape)
                .clickable(onClick = onOpenSettings),
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE9EEEC)),
            tonalElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, "Configuración", tint = Color(0xFF111514), modifier = Modifier.size(27.dp))
            }
        }
    }
}

@Composable
private fun FloatingCircleButton(active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .shadow(12.dp, CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE9EEEC)),
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (active) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = if (active) "Pausar EDDY" else "Activar EDDY",
                tint = Color(0xFF111514),
                modifier = Modifier.size(27.dp),
            )
        }
    }
}

@Composable
private fun GlassStatusCard(
    modifier: Modifier,
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    sources: List<EddyWebSource>,
) {
    val uriHandler = LocalUriHandler.current
    val primary = when (visualState) {
        EddyVisualState.LISTENING -> "TE ESCUCHO"
        EddyVisualState.THINKING -> if (webUsed) "BUSCANDO" else "PENSANDO"
        EddyVisualState.SPEAKING -> "EDDY HABLA"
        EddyVisualState.IDLE -> "TE ESCUCHO"
    }
    val secondary = when {
        responseText.isNotBlank() && visualState != EddyVisualState.IDLE -> responseText
        heardText.isNotBlank() -> heardText
        else -> "Solo respondo cuando me llamás"
    }

    Surface(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(25.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(25.dp),
        color = Color(0xFCFFFFFF),
        border = BorderStroke(1.dp, Color(0xFFE7ECEA)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (webUsed) Icons.Rounded.Language else Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (webUsed) EddyBlue else EddyMintDeep,
                    modifier = Modifier.size(23.dp),
                )
                Spacer(Modifier.size(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        primary,
                        color = Color(0xFF151817),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        letterSpacing = 1.8.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        secondary,
                        color = Color(0xFF747C79),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                            shape = RoundedCornerShape(11.dp),
                            color = Color(0xFFF4F8F6),
                        ) {
                            Text(
                                "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF56615D),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
