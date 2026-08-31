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

/**
 * Interfaz principal construida con Jetpack Compose + Material 3.
 * Mantiene exactamente la jerarquía visual aprobada: EDDY enorme, estado central y dock inferior.
 */
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
    val background = Brush.verticalGradient(
        listOf(
            Color(0xFFFEFEFE),
            Color(0xFFFBFCFC),
            Color(0xFFF6FAF8),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        // Hero grande y protagonista como en el mockup aprobado.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            EddyHero(
                state = visualState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        MinimalStateText(
            state = visualState,
            active = autoListeningEnabled,
            voiceReady = voiceReady,
            webUsed = webUsed,
        )

        Spacer(Modifier.height(30.dp))

        BottomControlDock(
            visualState = visualState,
            active = autoListeningEnabled,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = webSources,
            onToggleAssistant = onToggleAssistant,
            onOpenSettings = {
                context.startActivity(Intent(context, AiSettingsActivity::class.java))
            },
        )

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MinimalStateText(
    state: EddyVisualState,
    active: Boolean,
    voiceReady: Boolean,
    webUsed: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "minimalStatePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha",
    )

    val title = when {
        !active -> "EN PAUSA"
        !voiceReady -> "PREPARANDO VOZ..."
        state == EddyVisualState.THINKING && webUsed -> "BUSCANDO..."
        state == EddyVisualState.THINKING -> "PENSANDO..."
        state == EddyVisualState.SPEAKING -> "HABLANDO..."
        state == EddyVisualState.LISTENING -> "TE ESCUCHO..."
        else -> "ESCUCHANDO..."
    }
    val subtitle = when {
        !active -> "Tocá el micrófono para activar EDDY"
        !voiceReady -> "Estoy preparando mi voz"
        state == EddyVisualState.LISTENING -> "Decime qué necesitás"
        state == EddyVisualState.THINKING -> "Un momento"
        state == EddyVisualState.SPEAKING -> "Ahora hablo yo"
        else -> "Di “EDDY” para hablar conmigo"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) {
            drawCircle(if (active) EddyMintDeep.copy(alpha = alpha) else Color(0xFF9CA6A2))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color(0xFF101312),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            letterSpacing = 0.1.sp,
        )
    }
    Spacer(Modifier.height(9.dp))
    Text(
        subtitle,
        color = Color(0xFF6F7774),
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BottomControlDock(
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundActionButton(
            icon = if (active) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            contentDescription = if (active) "Pausar EDDY" else "Activar EDDY",
            onClick = { onToggleAssistant?.invoke() },
        )

        ConversationStatusCard(
            modifier = Modifier.weight(1f),
            visualState = visualState,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = sources,
        )

        RoundActionButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Configuración de EDDY",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .shadow(12.dp, CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE8EEEB)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF111313),
                modifier = Modifier.size(29.dp),
            )
        }
    }
}

@Composable
private fun ConversationStatusCard(
    modifier: Modifier = Modifier,
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
        visualState == EddyVisualState.LISTENING && heardText.isNotBlank() -> heardText
        visualState == EddyVisualState.THINKING && responseText.isNotBlank() -> responseText
        visualState == EddyVisualState.SPEAKING && responseText.isNotBlank() -> responseText
        else -> "Solo respondo cuando me llamás"
    }

    Surface(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(25.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5ECE9)),
        shadowElevation = 10.dp,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (webUsed) Icons.Rounded.Language else Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (webUsed) EddyBlue else EddyMintDeep,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        primary,
                        color = Color(0xFF171A19),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.7.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        secondary,
                        color = Color(0xFF68716E),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(sources.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sources.take(4).forEachIndexed { index, source ->
                        Surface(
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3F7F5),
                        ) {
                            Text(
                                "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF53605B),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
