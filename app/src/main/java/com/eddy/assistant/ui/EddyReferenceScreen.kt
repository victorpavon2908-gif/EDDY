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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
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
import com.eddy.assistant.background.EddyRuntimeState.InputState
import com.eddy.assistant.AiSettingsActivity
import com.eddy.assistant.ai.EddyWebSource

/**
 * Pantalla PRO: identidad grande, aire visual y controles mínimos. No hay header, chips ni
 * bloques técnicos. El estado operativo se expresa con color, animación y una sola línea.
 */
@Composable
internal fun EddyReferenceScreen(
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    voiceReady: Boolean,
    autoListeningEnabled: Boolean,
    inputState: InputState,
    webUsed: Boolean = false,
    webSources: List<EddyWebSource> = emptyList(),
    inputStatus: String = "",
    webSearching: Boolean = false,
) {
    val context = LocalContext.current
    val displayState = if (visualState == EddyVisualState.LISTENING && inputState != InputState.READY) EddyVisualState.IDLE else visualState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFCFEFD),
                        Color(0xFFF4FAF7),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // El halo tiene un campo de luz independiente del personaje para que el centro
            // se sienta profundo sin llenar la pantalla de tarjetas o efectos pesados.
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x163EDDB1),
                                Color(0x083EDDB1),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                EddyHero(
                    state = displayState,
                    modifier = Modifier.size(330.dp),
                )
            }
        }

        ProStateLine(
            state = displayState,
            active = autoListeningEnabled,
            inputState = inputState,
            webUsed = webSearching,
        )
        Text(inputStatus, color = Color(0xFF737C78), fontSize = 11.sp, textAlign = TextAlign.Center)
        if (!voiceReady && autoListeningEnabled) Text("Si no suena la voz, la respuesta queda aquí.", color = Color(0xFF737C78), fontSize = 11.sp)

        Spacer(Modifier.height(14.dp))

        ProDock(
            visualState = displayState,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = webSources,
            onSettings = {
                context.startActivity(Intent(context, AiSettingsActivity::class.java))
            },
        )

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ProStateLine(
    state: EddyVisualState,
    active: Boolean,
    inputState: InputState,
    webUsed: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "proStatusPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "proStatusAlpha",
    )

    val title = when {
        !active -> "EN PAUSA"
        state == EddyVisualState.THINKING && webUsed -> "BUSCANDO EN INTERNET"
        state == EddyVisualState.THINKING -> "PENSANDO"
        state == EddyVisualState.SPEAKING -> "HABLANDO"
        inputState == InputState.PREPARING -> "PREPARANDO ESCUCHA"
        inputState != InputState.READY -> "ESCUCHA NO DISPONIBLE"
        state == EddyVisualState.LISTENING -> "TE ESCUCHO"
        else -> "DECÍ EDDY"
    }
    val subtitle = when {
        !active -> "Podés habilitar la activación por voz en Ajustes"
        inputState == InputState.PREPARING -> "La primera preparación puede tardar unos minutos"
        inputState != InputState.READY -> "Revisá el estado del micrófono"
        state == EddyVisualState.LISTENING -> "Decime qué necesitás"
        state == EddyVisualState.THINKING && webUsed -> "Consultando fuentes"
        state == EddyVisualState.THINKING -> "Procesando tu petición"
        state == EddyVisualState.SPEAKING -> "Ahora hablo yo"
        else -> "Decí “EDDY” para hablar conmigo"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(
                color = when {
                    !active || inputState != InputState.READY -> Color(0xFF9DA6A2)
                    webUsed && state == EddyVisualState.THINKING -> EddyBlue.copy(alpha = alpha)
                    else -> EddyMintDeep.copy(alpha = alpha)
                },
            )
        }
        Spacer(Modifier.size(9.dp))
        Text(
            text = title,
            color = Color(0xFF0C100E),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = 0.6.sp,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = subtitle,
        color = Color(0xFF737C78),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProDock(
    visualState: EddyVisualState,
    heardText: String,
    responseText: String,
    webUsed: Boolean,
    sources: List<EddyWebSource>,
    onSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProConversationCard(
            modifier = Modifier.fillMaxWidth(),
            visualState = visualState,
            heardText = heardText,
            responseText = responseText,
            webUsed = webUsed,
            sources = sources,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            ProCircleButton(active = true, icon = Icons.Rounded.Settings, description = "Configuración", onClick = onSettings)
        }
    }
}

@Composable
private fun ProCircleButton(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .shadow(9.dp, CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE8EEEB)),
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) Color(0xFF0B0F0D) else Color(0xFF8D9692),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ProConversationCard(
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
        EddyVisualState.SPEAKING -> "EDDY"
        EddyVisualState.IDLE -> "EDDY"
    }
    val secondary = responseText.ifBlank { "Decí EDDY para hablar conmigo." }

    Surface(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE6ECE9)),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.heightIn(max = 210.dp).verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = if (webUsed) Color(0xFFEEF6FF) else Color(0xFFEAFBF5),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (webUsed) Icons.Rounded.Language else Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (webUsed) EddyBlue else EddyMintDeep,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primary,
                        color = Color(0xFF111513),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        letterSpacing = 1.25.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = secondary,
                        color = Color(0xFF6F7975),
                        fontSize = 11.5.sp,
                    )
                }
            }

            if (heardText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Vos: $heardText", color = Color(0xFF737C78), fontSize = 11.sp)
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
                            modifier = Modifier.clickable {
                                runCatching { uriHandler.openUri(source.url) }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF3F7F5),
                        ) {
                            Text(
                                text = "${index + 1} · ${source.title}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = Color(0xFF56615D),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
