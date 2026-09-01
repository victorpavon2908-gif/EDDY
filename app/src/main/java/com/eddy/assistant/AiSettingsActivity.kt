package com.eddy.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eddy.assistant.ai.EddyAiSettings
import com.eddy.assistant.ai.EddyGeminiClient
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.launch

class AiSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EddyTheme { GeminiSettingsScreen(onClose = { finish() }) } }
    }
}

@Composable
private fun GeminiSettingsScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(EddyAiSettings.apiKey(context)) }
    var model by remember { mutableStateOf(EddyAiSettings.model(context)) }
    var status by remember { mutableStateOf("Pegá aquí tu API key de Gemini. Se guarda solo en este teléfono.") }
    var testing by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("GEMINI DIRECTO", style = MaterialTheme.typography.headlineMedium)
            Text(
                "EDDY se conecta directamente con Gemini. Ya no necesitás una URL de Render para la conversación de IA. La memoria, voz y funciones locales siguen dentro de EDDY.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key de Gemini") },
                placeholder = { Text("Pegá tu clave aquí") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo") },
                placeholder = { Text(EddyAiSettings.DEFAULT_MODEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        EddyAiSettings.saveGemini(context, apiKey, model)
                        status = "Guardado en el teléfono. Probando Gemini…"
                        testing = true
                        scope.launch {
                            val client = EddyGeminiClient(context)
                            val ok = client.testConnection()
                            testing = false
                            status = if (ok) {
                                "Gemini conectado. EDDY ya puede conversar sin Render."
                            } else {
                                client.lastError ?: "No pude conectar con Gemini."
                            }
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
