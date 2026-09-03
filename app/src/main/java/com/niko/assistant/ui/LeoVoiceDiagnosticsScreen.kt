package com.niko.assistant.ui

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.voice.LeoVoiceDiagnostics
import com.niko.assistant.voice.LeoVoiceScenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LeoVoiceDiagnosticsScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(LeoVoiceDiagnostics.snapshot()) }
    var selectedScenario by remember { mutableStateOf(snapshot.scenario) }

    fun restartVoiceEngine() {
        val intent = UpgradeIdentity.assistantService(context)
        context.stopService(intent)
        scope.launch {
            delay(650L)
            runCatching { ContextCompat.startForegroundService(context, UpgradeIdentity.assistantService(context)) }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            LeoVoiceDiagnostics.expireExpectedTrialIfNeeded()
            snapshot = LeoVoiceDiagnostics.snapshot()
            delay(180L)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Diagnóstico de voz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Honor X6c · medición local sin guardar audio", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onHome) { Text("LEO") }
        }

        DiagnosticCard("Escucha en vivo") {
            MetricLine("Wake word", snapshot.wakeState)
            MetricLine("Nivel de audio", "${snapshot.audioLevelDbfs.oneDecimal()} dBFS")
            MetricLine("Ruido estimado", "${snapshot.noiseFloorDbfs.oneDecimal()} dBFS")
            MetricLine("SNR instantáneo", "${snapshot.snrDb.oneDecimal()} dB")
            MetricLine("Latencia wake", if (snapshot.wakeLatencyMs > 0) "${snapshot.wakeLatencyMs} ms" else "Sin muestra")
            MetricLine("Motor ASR", snapshot.transcriptionEngine)
            MetricLine("Latencia ASR", if (snapshot.transcriptionLatencyMs > 0) "${snapshot.transcriptionLatencyMs} ms" else "Sin muestra")
            MetricLine(
                "Perfil de voz",
                if (!snapshot.ownerProfileEnabled) "No exigido" else "${(snapshot.ownerScore * 100).roundToInt()}% · ${if (snapshot.ownerAccepted) "aceptado" else "rechazado"}",
            )
            Text(
                snapshot.lastTranscript.ifBlank { "Todavía no hay una transcripción completa." },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }

        DiagnosticCard("Prueba real de 100 llamadas") {
            val metrics = snapshot.metrics
            MetricLine("Progreso", "${metrics.completedCalls}/${metrics.targetCalls}")
            MetricLine("TP · activaciones correctas", metrics.truePositives.toString())
            MetricLine("FN · LEO no detectado", metrics.falseNegatives.toString())
            MetricLine("FP · activaciones falsas", metrics.falsePositives.toString())
            MetricLine("Recall", "${metrics.recallPercent()}%")
            MetricLine("Precisión", "${(metrics.precision * 100).roundToInt()}%")
            MetricLine("FP por hora", "%.2f".format(metrics.falsePositivesPerHour))

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeoVoiceScenario.entries.forEach { scenario ->
                    val selected = selectedScenario == scenario
                    OutlinedButton(onClick = {
                        selectedScenario = scenario
                        LeoVoiceDiagnostics.selectScenario(scenario)
                    }) { Text(if (selected) "✓ ${scenario.label}" else scenario.label) }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { LeoVoiceDiagnostics.startHundredCallSession() }) { Text("Iniciar 100") }
                OutlinedButton(
                    onClick = { LeoVoiceDiagnostics.prepareExpectedWake() },
                    enabled = snapshot.sessionActive && snapshot.expectedWakeUntilElapsed <= 0L,
                ) { Text("Preparar llamada") }
            }

            if (snapshot.expectedWakeUntilElapsed > 0L) {
                val remaining = (snapshot.expectedWakeUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                Text("Decí LEO ahora · ${(remaining + 999L) / 1000L} s", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { LeoVoiceDiagnostics.markExpectedWakeMissed() }) { Text("Marcar como no detectada") }
            }
        }

        DiagnosticCard("Falsos positivos") {
            Text("Para medir FP, activá esta vigilancia y dejá el teléfono funcionando sin decir LEO. Cada activación durante esa sesión cuenta como FP.")
            if (snapshot.falsePositiveWatchActive) {
                Button(onClick = { LeoVoiceDiagnostics.stopFalsePositiveWatch() }) { Text("Detener vigilancia") }
            } else {
                OutlinedButton(onClick = { LeoVoiceDiagnostics.startFalsePositiveWatch() }) { Text("Iniciar vigilancia") }
            }
        }

        DiagnosticCard("Afinación basada en datos") {
            val current = snapshot.tuning
            val recommended = snapshot.recommendation
            MetricLine("KWS score", "${current.keywordScore.twoDecimals()} → ${recommended.keywordScore.twoDecimals()}")
            MetricLine("KWS threshold", "${current.keywordThreshold.threeDecimals()} → ${recommended.keywordThreshold.threeDecimals()}")
            MetricLine("Voz mínima", "${current.minPassiveSpeechMs} → ${recommended.minPassiveSpeechMs} ms")
            MetricLine("Ventana probe", "${current.passiveProbeCooldownMs} → ${recommended.passiveProbeCooldownMs} ms")
            MetricLine("Ganancia voz baja", "${current.activeMaxGain.oneDecimal()}× → ${recommended.activeMaxGain.oneDecimal()}×")
            MetricLine("Pre-roll", "${current.preRollMs} → ${recommended.preRollMs} ms")
            Text("La recomendación no cambia nada hasta tener al menos 20 llamadas etiquetadas. Para cerrar el objetivo 95/100, completá las 100 en este Honor X6c.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { LeoVoiceDiagnostics.applyRecommendation(); restartVoiceEngine() }) { Text("Aplicar y reiniciar") }
                OutlinedButton(onClick = { LeoVoiceDiagnostics.resetTuning(); restartVoiceEngine() }) { Text("Restablecer") }
            }
        }

        DiagnosticCard("Recuperación del micrófono") {
            MetricLine("Interrupciones detectadas", snapshot.microphoneInterruptions.toString())
            MetricLine("Recuperaciones automáticas", snapshot.microphoneRecoveries.toString())
            Text("Probá llamada telefónica, cámara, otra app con micrófono, interruptor de privacidad, ahorro de batería, bloqueo y desbloqueo. El contador debe subir al interrumpirse y luego recuperarse sin reiniciar LEO.")
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun DiagnosticCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun Float.oneDecimal(): String = "%.1f".format(this)
private fun Float.twoDecimals(): String = "%.2f".format(this)
private fun Float.threeDecimals(): String = "%.3f".format(this)
