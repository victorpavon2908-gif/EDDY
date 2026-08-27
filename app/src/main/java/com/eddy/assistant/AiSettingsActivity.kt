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
import androidx.compose.ui.unit.dp
import com.eddy.assistant.ai.EddyAiClient
import com.eddy.assistant.ai.EddyAiSettings
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.launch

class AiSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EddyTheme {
                BackendWebSettingsScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun BackendWebSettingsScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(EddyAiSettings.baseUrl(context)) }
    var status by remember { mutableStateOf("Pegá la URL pública del backend de EDDY, sin /search.") }
    var testing by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("BACKEND + WEB", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Esta conexión permite que EDDY busque información en Internet desde su propio backend y muestre las fuentes dentro de la app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL del backend de EDDY") },
                placeholder = { Text("https://eddy-backend-xxxx.onrender.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
            )

            Text(status, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        EddyAiSettings.saveBaseUrl(context, url)
                        status = "Guardado. Probando conexión…"
                        testing = true
                        scope.launch {
                            val ok = EddyAiClient(context).healthCheck()
                            testing = false
                            status = if (ok) {
                                "Conectado. La búsqueda web de EDDY está lista."
                            } else {
                                "No pude conectar. Revisá la URL y que el backend esté desplegado."
                            }
                        }
                    },
                    enabled = !testing && url.isNotBlank(),
                    shape = CutCornerShape(topStart = 3.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 3.dp),
                ) {
                    Text(if (testing) "PROBANDO" else "GUARDAR Y PROBAR")
                }

                OutlinedButton(onClick = onClose) {
                    Text("CERRAR")
                }
            }
        }
    }
}
