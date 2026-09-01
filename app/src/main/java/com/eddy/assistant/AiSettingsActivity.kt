package com.eddy.assistant

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eddy.assistant.ai.EddyPersonality
import com.eddy.assistant.background.EddyAssistantService
import com.eddy.assistant.background.EddyRuntimeState
import com.eddy.assistant.background.EddyVoiceSettings
import com.eddy.assistant.localai.EddyModelManager
import com.eddy.assistant.localai.EddyModelCatalog
import com.eddy.assistant.localai.EddyModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eddy.assistant.ai.EddyAiSettings
import com.eddy.assistant.ai.EddyGroqClient
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class AiSettingsActivity : ComponentActivity() {
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceListener() else {
            EddyVoiceSettings.setEnabled(this, false)
            EddyRuntimeState.setInput(this, EddyRuntimeState.InputState.ERROR, "Falta el permiso de micrófono para escuchar EDDY.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EddyTheme { GroqSettingsScreen(onClose = { finish() }, onVoiceEnabled = ::setVoiceEnabled) } }
    }

    private fun setVoiceEnabled(enabled: Boolean) {
        EddyVoiceSettings.setEnabled(this, enabled)
        if (!enabled) stopService(Intent(this, EddyAssistantService::class.java))
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else startVoiceListener()
    }

    private fun startVoiceListener() {
        runCatching { ContextCompat.startForegroundService(this, Intent(this, EddyAssistantService::class.java)) }
            .onFailure {
                EddyVoiceSettings.setEnabled(this, false)
                EddyRuntimeState.setInput(this, EddyRuntimeState.InputState.ERROR, "No pude iniciar el micrófono. Volvé a abrir EDDY.")
            }
    }
}

@Composable
private fun GroqSettingsScreen(onClose: () -> Unit, onVoiceEnabled: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceEnabled by remember { mutableStateOf(EddyVoiceSettings.enabled(context)) }
    var voiceStatus by remember { mutableStateOf(EddyRuntimeState.read(context).inputStatus) }
    var outputVoiceStatus by remember { mutableStateOf(EddyRuntimeState.read(context).voiceStatus) }
    LaunchedEffect(Unit) {
        while (true) {
            voiceEnabled = EddyVoiceSettings.enabled(context)
            voiceStatus = EddyRuntimeState.read(context).inputStatus
            outputVoiceStatus = EddyRuntimeState.read(context).voiceStatus
            delay(500L)
        }
    }
    var apiKey by remember { mutableStateOf(EddyAiSettings.apiKey(context)) }
    var model by remember { mutableStateOf(EddyAiSettings.model(context)) }
    var status by remember { mutableStateOf("Pegá aquí tu API key de GroqCloud. Se guarda solo en este teléfono.") }
    var testing by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf(EddyAiSettings.personality(context)) }
    var localFirst by remember { mutableStateOf(EddyAiSettings.localFirst(context)) }
    var autoResearch by remember { mutableStateOf(EddyAiSettings.autoResearch(context)) }
    var learning by remember { mutableStateOf(EddyAiSettings.adaptiveLearning(context)) }
    var preparing by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("La conversación y la voz local se preparan una vez con conexión.") }
    val modelManager = remember { EddyModelManager(context) }
    fun saveBehavior() = EddyAiSettings.saveBehavior(context, personality, localFirst, autoResearch, learning)
    fun prepareModel(spec: EddyModelSpec) {
        preparing = true
        modelStatus = "Preparando ${spec.id}…"
        scope.launch {
            try {
                val ready = withContext(Dispatchers.IO) {
                    modelManager.ensure(spec) { progress ->
                        scope.launch { if (preparing) modelStatus = "${progress.modelId}: ${progress.downloadedBytes / 1_000_000} MB" }
                    }
                }
                modelStatus = if (ready) {
                    if (spec == EddyModelCatalog.spanishVoice) "Voz preparada. Se usará al volver a iniciar la escucha desde Ajustes." else "Preparado para usar sin Internet."
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
                Text("Escuchar la palabra EDDY", modifier = Modifier.weight(1f))
                Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; onVoiceEnabled(it) })
            }
            Text("Decí EDDY para empezar cada petición. La detección es local y funciona sin Internet una vez preparados los modelos. El micrófono permanece abierto mientras esta opción está activa.", style = MaterialTheme.typography.bodySmall)
            Text(voiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("EDDY · PERSONALIDAD Y APRENDIZAJE", style = MaterialTheme.typography.headlineSmall)
            Text("Elegí cómo te responde. Los cambios se guardan al instante.")
            EddyPersonality.entries.forEach { option ->
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
                Text("Aprender de órdenes reconocidas", modifier = Modifier.weight(1f))
                Switch(checked = learning, onCheckedChange = { learning = it; saveBehavior() })
            }
            Text("El aprendizaje adapta cómo clasifica tus pedidos; no reemplaza el modelo que sabe hablar. Decí borrar tu memoria para eliminar también ese aprendizaje.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { prepareModel(EddyModelCatalog.localLlm) }, enabled = !preparing) { Text("PREPARAR CONVERSACIÓN SIN INTERNET") }
            OutlinedButton(onClick = { prepareModel(EddyModelCatalog.spanishVoice) }, enabled = !preparing) { Text("PREPARAR VOZ LOCAL") }
            Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            Text(outputVoiceStatus, style = MaterialTheme.typography.bodySmall)
            Text("GROQCLOUD", style = MaterialTheme.typography.headlineMedium)
            Text(
                "EDDY usa GroqCloud para conversar y Groq Compound para consultar la web. Guardá la clave de tu cuenta de Groq; la memoria y la voz local siguen en el teléfono.",
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
                placeholder = { Text(EddyAiSettings.DEFAULT_MODEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Las búsquedas usan Groq Compound automáticamente. Su disponibilidad y límites dependen de tu cuenta.", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        EddyAiSettings.saveGroq(context, apiKey, model)
                        status = "Guardado en el teléfono. Probando GroqCloud…"
                        testing = true
                        scope.launch {
                            val client = EddyGroqClient(context)
                            try {
                                val ok = client.testConnection()
                                status = if (ok) "Conectado con ${client.lastModelUsed}. EDDY ya puede conversar." else client.lastError ?: "No pude conectar con GroqCloud."
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
