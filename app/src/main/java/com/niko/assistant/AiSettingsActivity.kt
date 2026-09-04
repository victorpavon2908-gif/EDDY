package com.niko.assistant

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.niko.assistant.ai.NikoGroqClient
import com.niko.assistant.ui.theme.NikoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AiSettingsActivity : ComponentActivity() {
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceListener() else {
            NikoVoiceSettings.setEnabled(this, false)
            NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "Falta el permiso de micrófono para escuchar LEO.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NikoTheme { GroqSettingsScreen(onClose = { finish() }, onVoiceEnabled = ::setVoiceEnabled) } }
    }

    private fun setVoiceEnabled(enabled: Boolean) {
        NikoVoiceSettings.setEnabled(this, enabled)
        if (!enabled) stopService(UpgradeIdentity.assistantService(this))
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startVoiceListener()
    }

    private fun startVoiceListener() {
        runCatching { ContextCompat.startForegroundService(this, UpgradeIdentity.assistantService(this)) }
            .onFailure {
                NikoVoiceSettings.setEnabled(this, false)
                NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "No pude iniciar el micrófono. Volvé a abrir LEO.")
            }
    }
}

@Composable
private fun GroqSettingsScreen(onClose: () -> Unit, onVoiceEnabled: (Boolean) -> Unit) {
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
    var apiKey by remember { mutableStateOf(NikoAiSettings.apiKey(context)) }
    var model by remember { mutableStateOf(NikoAiSettings.model(context)) }
    var status by remember { mutableStateOf("Pegá aquí tu API key de GroqCloud. Se guarda solo en este teléfono.") }
    var testing by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf(NikoAiSettings.personality(context)) }
    var localFirst by remember { mutableStateOf(NikoAiSettings.localFirst(context)) }
    var autoResearch by remember { mutableStateOf(NikoAiSettings.autoResearch(context)) }
    var learning by remember { mutableStateOf(NikoAiSettings.adaptiveLearning(context)) }
    var preparing by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("La conversación y la voz local se preparan una vez con conexión.") }
    val modelManager = remember { NikoModelManager(context) }
    val deviceProfile = remember { NikoDeviceProfile.detect(context) }
    val conversationModel = remember(deviceProfile) { NikoModelCatalog.recommendedConversationModel(deviceProfile) }
    val conversationLabel = if (conversationModel == NikoModelCatalog.localLlmQuality) {
        "Qwen2.5 1.5B INT8 · calidad local · aprox. 1.6 GB"
    } else {
        "Qwen2.5 0.5B INT8 · modo local ligero"
    }

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
                        spec == NikoModelCatalog.spanishVoice -> "Voz preparada. Se usará al volver a iniciar la escucha desde Ajustes."
                        spec == NikoModelCatalog.whisperAsr -> "Whisper preparado. Reiniciá la escucha: LEO lo usará como segunda pasada solo cuando Canary detecte una transcripción dudosa."
                        spec in NikoModelCatalog.conversationModels -> "Cerebro local preparado. LEO ya puede conversar e interpretar órdenes libres sin Internet."
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
            Text("ACTIVACIÓN POR VOZ", style = MaterialTheme.typography.headlineSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Escuchar la palabra LEO", modifier = Modifier.weight(1f))
                Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; onVoiceEnabled(it) })
            }
            Text("Decí LEO para empezar. Después de responder, Leo mantiene una ventana breve para que podás seguir hablando sin repetir su nombre. La detección sigue siendo local.", style = MaterialTheme.typography.bodySmall)
            Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("MI VOZ", style = MaterialTheme.typography.headlineSmall)
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
            Text("RECONOCIMIENTO AVANZADO", style = MaterialTheme.typography.headlineSmall)
            Text("Canary transcribe primero para responder rápido. Whisper multilingual INT8 puede hacer una segunda pasada local cuando la primera transcripción sale dudosa.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { prepareModel(NikoModelCatalog.whisperAsr) }, enabled = !preparing) { Text("PREPARAR WHISPER LOCAL") }
            Text("LEO · PERSONALIDAD Y APRENDIZAJE", style = MaterialTheme.typography.headlineSmall)
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
            Text("Entrena una red neuronal pequeña con interacciones clasificables y aprende comandos a partir de tus correcciones. Todo queda en el teléfono; no reentrena el modelo generativo completo. Decí borrar tu memoria para eliminar también ese aprendizaje.", style = MaterialTheme.typography.bodySmall)
            Text(conversationLabel, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { prepareModel(conversationModel) }, enabled = !preparing) { Text("PREPARAR CONVERSACIÓN LOCAL") }
            OutlinedButton(onClick = { prepareModel(NikoModelCatalog.spanishVoice) }, enabled = !preparing) { Text("PREPARAR VOZ LOCAL") }
            Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            Text(outputVoiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("GROQCLOUD", style = MaterialTheme.typography.headlineMedium)
            Text(
                "LEO usa GroqCloud para conversar y Groq Compound para consultar la web. La memoria y la voz local siguen en el teléfono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key de GroqCloud") },
                placeholder = { Text("Pegá tu clave aquí") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo de conversación") },
                placeholder = { Text(NikoAiSettings.DEFAULT_MODEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Las búsquedas se hacen directamente en la web y no necesitan una clave de Groq. Groq es opcional para conversar.", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        NikoAiSettings.saveGroq(context, apiKey, model)
                        status = "Guardado en el teléfono. Probando GroqCloud…"
                        testing = true
                        scope.launch {
                            val client = NikoGroqClient(context)
                            try {
                                val ok = client.testConnection()
                                status = if (ok) "Conectado con ${client.lastModelUsed}. LEO ya puede conversar." else client.lastError ?: "No pude conectar con GroqCloud."
                            } finally { testing = false }
                        }
                    },
                    enabled = !testing && apiKey.isNotBlank(),
                    shape = CutCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 3.dp),
                ) { Text(if (testing) "PROBANDO" else "GUARDAR Y PROBAR") }
                OutlinedButton(onClick = onClose) { Text("CERRAR") }
            }
            Text(
                "Nota del prototipo: una clave guardada en una app cliente puede extraerse de un dispositivo comprometido. No publiques un APK con una clave preincluida.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
