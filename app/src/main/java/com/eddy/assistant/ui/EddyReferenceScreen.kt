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
 * Pantalla principal minimalista de EDDY.
 *
 * Diseño centrado en la identidad del asistente, sin encabezados ni rieles de capacidades.
 * La información secundaria solo aparece abajo para mantener la interfaz limpia y cercana
 * a la referencia visual aprobada.
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
            Color(0xFFFBFCFC),
            Color(0xFFF7FAF9),
            Color(0xFFF2F7F5),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EddyHero(
                state = visualState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )
        }

        MinimalStateText(
            state = visualState,
            active = autoListeningEnabled,
            voiceReady = voiceReady,
            webUsed = webUsed,
        )

        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(18.dp))
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
        Canvas(Modifier.size(8.dp)) {
            drawCircle(
                color = if (active) EddyMintDeep.copy(alpha = alpha) else Color(0xFF9CA6A2),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Color(0xFF111514),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            letterSpacing = 0.2.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        color = Color(0xFF717B77),
        style = MaterialTheme.typography.bodyMedium,
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
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RoundActionButton(
            icon = if (active) Icons.Rounded.Mic else Icons.Rounded.MicOff,
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
            .size(58.dp)
            .shadow(10.dp, CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE7ECEA)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFF111514),
                modifier = Modifier.size(27.dp),
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
        responseText.isNotBlank() && visualState != EddyVisualState.IDLE -> responseText
        heardText.isNotBlank() -> heardText
        else -> "Solo respondo cuando me llamás"
    }

    Surface(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5ECE9)),
        shadowElevation = 9.dp,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (webUsed) Icons.Rounded.Language else Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (webUsed) EddyBlue else EddyMintDeep,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        primary,
                        color = Color(0xFF171A19),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        secondary,
                        color = Color(0xFF68726E),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(sources.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 9.dp)
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
