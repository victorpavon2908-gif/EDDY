package com.niko.assistant

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import com.niko.assistant.localai.NikoVoiceProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.niko.assistant.ai.NikoPersonality
import com.niko.assistant.background.NikoRuntimeState
import com.niko.assistant.background.NikoVoiceSettings
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ui.theme.NikoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AiSettingsActivity : ComponentActivity() {
    private val runtimeStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let(NikoRuntimeState::acceptExternalState)
        }
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceListener() else {
            NikoVoiceSettings.setEnabled(this, false)
            NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "Falta el permiso de micrófono para escuchar LEO.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            runtimeStateReceiver,
            IntentFilter(NikoRuntimeState.ACTION_RUNTIME_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { NikoTheme { LeoSettingsScreen(onClose = { finish() }, onVoiceEnabled = ::setVoiceEnabled) } }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(runtimeStateReceiver) }
        super.onDestroy()
    }

    private fun setVoiceEnabled(enabled: Boolean) {
        NikoVoiceSettings.setEnabled(this, enabled)
        if (!enabled) stopService(UpgradeIdentity.assistantService(this))
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startVoiceListener()
    }

    private fun startVoiceListener() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                UpgradeIdentity.assistantService(this).apply { action = com.niko.assistant.background.NikoAssistantService.ACTION_START },
            )
        }
            .onFailure {
                NikoVoiceSettings.setEnabled(this, false)
                NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "No pude iniciar el micrófono. Volvé a abrir LEO.")
            }
    }
}

@Composable
private fun LeoSettingsScreen(onClose: () -> Unit, onVoiceEnabled: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val ownerVoice = remember { NikoVoiceProfile(context) }
    var enrolled by remember { mutableStateOf(ownerVoice.isEnrolled) }
    var ownerOnly by remember { mutableStateOf(ownerVoice.ownerOnly) }
    var registering by remember { mutableStateOf(ownerVoice.enrollmentActive) }
    var voiceSamples by remember { mutableStateOf(ownerVoice.enrollmentCount) }
    DisposableEffect(Unit) { onDispose { ownerVoice.cancelEnrollment() } }
    var voiceEnabled by remember { mutableStateOf(NikoVoiceSettings.enabled(context)) }
    var voiceStatus by remember { mutableStateOf(NikoRuntimeState.read(context).inputStatus) }
    var outputVoiceStatus by remember { mutableStateOf(NikoRuntimeState.read(context).voiceStatus) }
    var bubbleStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            voiceEnabled = NikoVoiceSettings.enabled(context)
            voiceStatus = NikoRuntimeState.read(context).inputStatus
            outputVoiceStatus = NikoRuntimeState.read(context).voiceStatus
            enrolled = ownerVoice.isEnrolled
            ownerOnly = ownerVoice.ownerOnly
            registering = ownerVoice.enrollmentActive
            voiceSamples = ownerVoice.enrollmentCount
            delay(500L)
        }
    }
    LaunchedEffect(Unit) { NikoAiSettings.retireCloudCredentials(context) }
    var initiative by remember { mutableStateOf(NikoAiSettings.companionInitiative(context)) }
    var personality by remember { mutableStateOf(NikoAiSettings.personality(context)) }
    var localFirst by remember { mutableStateOf(NikoAiSettings.localFirst(context)) }
    var autoResearch by remember { mutableStateOf(NikoAiSettings.autoResearch(context)) }
    var learning by remember { mutableStateOf(NikoAiSettings.adaptiveLearning(context)) }
    val semanticMemory = remember { com.niko.assistant.memory.embedding.LeoSemanticMemory.get(context) }
    var semanticStatus by remember { mutableStateOf(semanticMemory.status) }
    var preparingSemantic by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("La conversación y la voz local se preparan una vez con conexión.") }
    val modelManager = remember { NikoModelManager(context) }
    val offlineKnowledge = remember { com.niko.assistant.localai.LeoFrozenBrainManager(context) }
    var offlineAllowed by remember { mutableStateOf(com.niko.assistant.localai.LeoOfflineKnowledgeSettings.downloadAllowed(context)) }
    var offlineJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var offlineStatus by remember { mutableStateOf(if (offlineKnowledge.isInstalled()) "Paquete instalado y disponible sin Internet." else "Paquete opcional sin instalar.") }

    fun saveBehavior() = NikoAiSettings.saveBehavior(context, personality, localFirst, autoResearch, learning)
    fun prepareModel(spec: NikoModelSpec) {
        preparing = true
        modelStatus = "Preparando ${spec.id}…"
        scope.launch {
            try {
                val ready = withContext(Dispatchers.IO) {
                    modelManager.ensure(spec) { progress ->
                        scope.launch {
                            if (preparing) {
                                val total = if (progress.totalBytes > 0L) " / ${progress.totalBytes / 1_000_000} MB" else ""
                                modelStatus = "${progress.modelId}: ${progress.downloadedBytes / 1_000_000} MB$total"
                            }
                        }
                    }
                }
                modelStatus = if (ready) {
                    when {
                        spec == NikoModelCatalog.whisperAsr -> "Whisper preparado. Reiniciá la escucha: LEO lo usará como segunda pasada solo cuando Canary detecte una transcripción dudosa."
                        else -> "Preparado para usar sin Internet."
                    }
                } else "No se pudo preparar. Comprobá conexión y espacio disponible."
            } finally { preparing = false }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "LEO",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Configuración del asistente",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("ACTIVACIÓN POR VOZ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Escuchar la palabra LEO", modifier = Modifier.weight(1f))
                Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; onVoiceEnabled(it) })
            }
            Text("Decí LEO para empezar. Después de responder, Leo mantiene una ventana breve para que podás seguir hablando sin repetir su nombre. La detección sigue siendo local.", style = MaterialTheme.typography.bodySmall)
            Text(voiceStatus, style = MaterialTheme.typography.bodySmall)

            Text("CONVERSACIÓN ESPONTÁNEA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Dejar que LEO inicie una charla", modifier = Modifier.weight(1f))
                Switch(checked = initiative, onCheckedChange = {
                    initiative = it; NikoAiSettings.setCompanionInitiative(context, it)
                })
            }
            Text("Solo en la pantalla principal, con escucha activa y sin interrumpirte. Después de una respuesta tenés hasta 12 segundos para seguir sin repetir LEO. Máximo dos invitaciones sin respuesta, separadas por cinco minutos. Decí «desactiva conversación espontánea» para apagarlo.", style = MaterialTheme.typography.bodySmall)

            Text("BURBUJA FLOTANTE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Si moviste la burbuja fuera de lugar, podés devolverla a su posición inicial.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    val restored = runCatching {
                        context.startService(
                            UpgradeIdentity.assistantService(context).apply {
                                action = com.niko.assistant.background.NikoAssistantService.ACTION_RESET_BUBBLE
                            },
                        )
                        true
                    }.getOrDefault(false)
                    bubbleStatus = if (restored) {
                        "Posición restaurada. La burbuja volvió a su ubicación inicial."
                    } else {
                        "No pude restaurarla. Abrí LEO y volvé a intentarlo."
                    }
                },
            ) {
                Text("RESTAURAR POSICIÓN DE LA BURBUJA")
            }
            if (bubbleStatus.isNotBlank()) {
                Text(bubbleStatus, style = MaterialTheme.typography.bodySmall)
            }

            Text("MI VOZ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Se reduce la amplificación de voces lejanas. Registrá tu voz para rechazar órdenes que no coincidan; las voces superpuestas todavía pueden confundirse.", style = MaterialTheme.typography.bodySmall)
            if (registering) {
                Text("Registro $voiceSamples de 4. Hablá solo vos, cerca del teléfono: cuatro frases distintas de 2 a 6 segundos. Esperá la respuesta entre frases. Estas frases no ejecutan acciones.")
                Text("Por ejemplo: «Leo, quiero que reconozcás mi voz cuando te hablo».")
                OutlinedButton(onClick = { ownerVoice.cancelEnrollment(); registering = false }) { Text("CANCELAR REGISTRO") }
            } else {
                Text(if (enrolled) "Tu voz está registrada en este teléfono." else "Tu voz todavía no está registrada.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    ownerVoice.beginEnrollment()
                    registering = true
                    if (!voiceEnabled) { voiceEnabled = true; onVoiceEnabled(true) }
                }, enabled = !preparing && modelManager.isInstalled(NikoModelCatalog.speaker)) {
                    Text(if (enrolled) "VOLVER A REGISTRAR MI VOZ" else "REGISTRAR MI VOZ")
                }
            }
            if (!modelManager.isInstalled(NikoModelCatalog.speaker)) {
                OutlinedButton(onClick = { prepareModel(NikoModelCatalog.speaker) }, enabled = !preparing) { Text("PREPARAR RECONOCIMIENTO DE MI VOZ") }
            }
            if (enrolled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Priorizar mi voz", modifier = Modifier.weight(1f))
                    Switch(checked = ownerOnly, onCheckedChange = { ownerVoice.setOwnerOnly(it); ownerOnly = it })
                }
                OutlinedButton(onClick = { ownerVoice.reset(); enrolled = false; ownerOnly = false; registering = false }) { Text("BORRAR MI PERFIL DE VOZ") }
            }
            Text("Solo se guarda un perfil numérico local. No se guardan ni envían las grabaciones del registro.", style = MaterialTheme.typography.bodySmall)
            Text("RECONOCIMIENTO AVANZADO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Canary transcribe primero para responder rápido. Whisper multilingual INT8 puede hacer una segunda pasada local cuando la primera transcripción sale dudosa.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { prepareModel(NikoModelCatalog.whisperAsr) }, enabled = !preparing) { Text("PREPARAR WHISPER LOCAL") }
            Text("LEO · PERSONALIDAD Y APRENDIZAJE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Elegí cómo te responde. Los cambios se guardan al instante.")
            NikoPersonality.entries.forEach { option ->
                Row {
                    RadioButton(selected = personality == option, onClick = { personality = option; saveBehavior() })
                    Text(option.label, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Priorizar conversación local", modifier = Modifier.weight(1f))
                Switch(checked = localFirst, onCheckedChange = { localFirst = it; saveBehavior() })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Verificar dudas en Internet", modifier = Modifier.weight(1f))
                Switch(checked = autoResearch, onCheckedChange = { autoResearch = it; saveBehavior() })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entrenamiento local continuo", modifier = Modifier.weight(1f))
                Switch(checked = learning, onCheckedChange = { learning = it; saveBehavior() })
            }
            Text("Cada actualización del clasificador se compara con ocho casos de regresión antes de guardarse. Decí «eso está mal», «eso me sirvió» o «qué aprendiste». Las respuestas web no se convierten automáticamente en hechos verificados. Borrar memoria elimina también este aprendizaje; no se reentrena el modelo generativo completo.", style = MaterialTheme.typography.bodySmall)
            Text("LEO Neural Core coordina las respuestas con un modelo pequeño para conversación breve. El clasificador aprende intenciones; el modelo de lenguaje no se reentrena con cada pregunta.", style = MaterialTheme.typography.bodySmall)
            com.niko.assistant.inference.local.LeoLocalModelPanel()
            Text("Conocimiento offline opcional", style = MaterialTheme.typography.titleMedium)
            Text("Enciclopedia local: ocupa unos 500 MB una vez instalada. Al descargarla autorizás que se reanude cuando vuelvas a abrir LEO. El botón consulta nuevas versiones y descarga solo si cambió el contenido. Podés detenerla y conservar lo ya instalado. Tus recuerdos se mantienen.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                com.niko.assistant.localai.LeoOfflineKnowledgeSettings.allowDownload(context, true)
                offlineAllowed = true
                offlineStatus = "Preparando descarga…"
                offlineJob = scope.launch {
                    try {
                        offlineKnowledge.ensureInstalled(checkForUpdates = true) { progress ->
                            scope.launch { offlineStatus = progress.message + if (progress.totalBytes > 0) " · ${progress.downloadedBytes / 1_000_000}/${progress.totalBytes / 1_000_000} MB" else "" }
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        offlineStatus = "Descarga detenida. Lo descargado permite reanudar más adelante."
                        throw cancelled
                    } catch (_: Exception) {
                        offlineStatus = "No pude completar la descarga. Revisá conexión y almacenamiento y reintentá."
                    } finally { offlineJob = null }
                }
            }, enabled = offlineJob == null) { Text("ACTUALIZAR CEREBRO DE LEO") }
            if (offlineAllowed) {
                OutlinedButton(onClick = {
                    com.niko.assistant.localai.LeoOfflineKnowledgeSettings.allowDownload(context, false)
                    offlineAllowed = false
                    offlineJob?.cancel()
                    offlineStatus = "Descargas automáticas desactivadas. El conocimiento instalado se conserva."
                }) { Text("DETENER Y NO REANUDAR AUTOMÁTICAMENTE") }
            }
            Text(offlineStatus, style = MaterialTheme.typography.bodySmall)
            Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            Text("Memoria semántica", style = MaterialTheme.typography.titleMedium)
            Text("Modelo multilingüe entrenado · descarga única de 138 MB. Tus recuerdos se procesan en el teléfono.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                preparingSemantic = true
                scope.launch {
                    try {
                        semanticMemory.install { downloaded, total ->
                            scope.launch { semanticStatus = "Preparando memoria: ${downloaded / 1_000_000} / ${total / 1_000_000} MB" }
                        }
                        semanticStatus = semanticMemory.status
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                    } catch (error: Exception) { semanticStatus = "No se completó: ${error.message}. Podés reintentar." }
                    finally { preparingSemantic = false }
                }
            }, enabled = !preparingSemantic) { Text(if (preparingSemantic) "PREPARANDO…" else "PREPARAR MEMORIA SEMÁNTICA") }
            Text(semanticStatus, style = MaterialTheme.typography.bodySmall)
            Text(outputVoiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("INVESTIGACIÓN WEB", style = MaterialTheme.typography.headlineMedium)
            Text("LEO busca y lee fuentes públicas sin clave de API. Las preguntas de conocimiento consultan Internet; la memoria personal y las acciones siguen en el teléfono.", style = MaterialTheme.typography.bodyMedium)
            Text("Las respuestas web son extractivas y citan fuentes; pedir más profundidad amplía la evidencia, no garantiza exactitud. Si los buscadores fallan, podés proporcionar un enlace público. Los ejemplos de código no se ejecutan automáticamente.", style = MaterialTheme.typography.bodySmall)
            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Text("Memoria y aprendizaje se guardan en el teléfono. Las consultas y enlaces de investigación se envían a los sitios públicos correspondientes. La voz principal usa el reconocedor de Android, cuyo procesamiento depende del proveedor; la escucha avanzada usa modelos locales. Decí «borrá tu memoria» para iniciar el borrado con confirmación.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClose) { Text("CERRAR") }
        }
    }
}
