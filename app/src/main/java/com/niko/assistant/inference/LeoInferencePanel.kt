package com.niko.assistant.inference

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LeoInferencePanel() {
    val context = LocalContext.current
    val settings = remember { LeoInferenceSettings(context) }
    val scope = rememberCoroutineScope()
    val client = remember { LeoInferenceClient() }
    DisposableEffect(client) { onDispose { client.close() } }
    var endpoint by remember { mutableStateOf(settings.endpoint) }
    var model by remember { mutableStateOf(settings.model) }
    var secret by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var researchConsent by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Text("MODELO DE RAZONAMIENTO Y CÓDIGO", style = MaterialTheme.typography.titleMedium)
    Text("Opcional. Requiere un servidor compatible que vos elijás. Puede ser propio o de pago; LEO no incluye una suscripción. En conversación se envía la pregunta actual, que puede contener datos personales o código. La investigación puede incluir extractos si lo autorizás abajo. No se adjuntan tus recuerdos ni el historial.")
    Text(if (enabled) "Proveedor activado" else "Proveedor desactivado: se usan las funciones locales y la búsqueda existente")
    OutlinedTextField(endpoint, { endpoint = it; consent = false }, label = { Text("URL HTTPS completa de chat/completions") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
    OutlinedTextField(model, { model = it; consent = false }, label = { Text("Nombre exacto del modelo instalado en el servidor") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
    OutlinedTextField(secret, { secret = it }, label = { Text("Clave API; vacía para un servidor sin clave") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !busy)
    Text("Al guardar se reemplaza la clave anterior. La clave queda cifrada con Android Keystore.")
    Row {
        Checkbox(consent, { consent = it }, enabled = !busy)
        Text("Autorizo enviar mis preguntas actuales al servidor indicado para razonamiento y código.")
    }
    Row {
        Checkbox(researchConsent, { researchConsent = it }, enabled = !busy)
        Text("También autorizo enviar los extractos de páginas recuperadas para sintetizar investigaciones. No se envían recuerdos privados.")
    }
    Button(onClick = {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { settings.save(endpoint, model, secret, consent, researchConsent) }
                secret = ""; enabled = settings.enabled; consent = false; researchConsent = false
                status = "Configuración guardada. Todavía no se comprobó una respuesta del servidor."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { status = "No se pudo guardar. Revisá URL HTTPS, modelo y disponibilidad de Keystore." }
            finally { busy = false }
        }
    }, enabled = consent && !busy) { Text("GUARDAR Y ACTIVAR") }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                val config = withContext(Dispatchers.IO) { settings.read() }
                val answer = config?.let { client.reply(it.endpoint, it.model, it.key, "Respondé solamente: LEO conectado") }
                status = if (answer == null || config == null || !settings.isCurrent(config)) "No se obtuvo una respuesta válida. Revisá servidor, modelo y clave." else "Respuesta real del servidor: ${answer.take(180)}"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            finally { busy = false }
        }
    }, enabled = enabled && !busy) { Text("PROBAR CONEXIÓN") }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { settings.clear() }
                enabled = false; consent = false; researchConsent = false; secret = ""; status = "Proveedor desactivado y credencial eliminada."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { status = "No pude borrar la configuración. Intentá nuevamente." } finally { busy = false }
        }
    }, enabled = !busy) { Text("DESACTIVAR Y BORRAR CREDENCIAL") }
    if (status.isNotBlank()) Text(status)
}
