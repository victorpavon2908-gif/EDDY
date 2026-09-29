package com.niko.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.niko.assistant.startup.LeoFirstRunSetup
import com.niko.assistant.startup.LeoFirstRunState

/** Pantalla exclusiva de la primera preparación. LEO no escucha mientras esté visible. */
@Composable
fun LeoFirstRunScreen(
    state: LeoFirstRunState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (state.phase == LeoFirstRunState.Phase.READY) "LEO ESTÁ LISTO" else "PREPARANDO LEO",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.phase == LeoFirstRunState.Phase.READY) {
                        "La instalación inicial terminó. A partir de ahora LEO arrancará normalmente sin repetir estas descargas."
                    } else {
                        "Esto se hace una sola vez. Primero se descargan, instalan y verifican todos los módulos locales; después arranca LEO."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(26.dp))
                Text(
                    text = state.currentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                if (state.phase !in setOf(LeoFirstRunState.Phase.FAILED, LeoFirstRunState.Phase.READY)) {
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { state.overallProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    val step = if (state.totalModels > 0 && state.currentIndex > 0) {
                        "Módulo ${state.currentIndex.coerceAtMost(state.totalModels)} de ${state.totalModels}"
                    } else {
                        "Verificando instalación"
                    }
                    val modelAmount = if (state.totalBytes > 0L) {
                        " · ${LeoFirstRunSetup.formatBytes(state.downloadedBytes)} / ${LeoFirstRunSetup.formatBytes(state.totalBytes)}"
                    } else {
                        ""
                    }
                    Text(
                        text = step + modelAmount,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                if (state.phase == LeoFirstRunState.Phase.FAILED) {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRetry) {
                        Text("REINTENTAR PREPARACIÓN")
                    }
                }
            }
        }
    }
}
