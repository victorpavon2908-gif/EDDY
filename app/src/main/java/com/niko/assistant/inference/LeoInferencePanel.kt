package com.niko.assistant.inference

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LeoInferencePanel(openUrl: ((String) -> Unit)? = null,
    settingsFactory: (android.content.Context) -> LeoInferenceSettings = { LeoInferenceSettings(it) },
    clientFactory: () -> LeoInferenceClient = { LeoInferenceClient() }) {
    val context = LocalContext.current
    val settings = remember { settingsFactory(context) }
    val scope = rememberCoroutineScope()
    val client = remember { clientFactory() }
    DisposableEffect(client) { onDispose { client.close() } }
    var endpoint by remember { mutableStateOf(settings.endpoint.ifBlank { LeoGoogleProvider.ENDPOINT }) }
    var model by remember { mutableStateOf(settings.model.ifBlank { LeoGoogleProvider.MODEL }) }
    var secret by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(settings.enabled) }
    var historyConsent by remember { mutableStateOf(settings.historyAllowed) }
    var researchConsent by remember { mutableStateOf(settings.researchAllowed) }
    var preferRemote by remember { mutableStateOf(if (settings.enabled) settings.preferRemote else true) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var busy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(!LeoGoogleProvider.isGoogle(endpoint)) }
    var status by remember { mutableStateOf("") }
    val google = LeoGoogleProvider.isGoogle(endpoint)
    val reusableKey = enabled && settings.canReuseCredential(endpoint)
    fun open(url: String) {
        try {
            if (openUrl != null) openUrl(url) else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { status = "No pude abrir el navegador. Abrí $url desde tu navegador." }
    }
    Text("CONECTAR INTELIGENCIA", style = MaterialTheme.typography.titleMedium)
    Text(if (enabled) "Activo: ${settings.model}" else "Conexión opcional · actualmente desactivada")
    Row {
        OutlinedButton(onClick = {
            endpoint = LeoGoogleProvider.ENDPOINT; model = LeoGoogleProvider.MODEL
            secret = ""; consent = false; historyConsent = false; researchConsent = false
            preferRemote = true; advanced = false
            status = "Gemini seleccionado. Completá los pasos para conectarlo."
        }, enabled = !busy) { Text("Google Gemini") }
        TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text("Avanzado") }
    }
    if (google) {
        Text("1. Entrá con tu cuenta en Google AI Studio y creá o elegí tu clave.")
        Button(onClick = { open(LeoGoogleProvider.STUDIO) }, enabled = !busy) { Text("ABRIR GOOGLE AI STUDIO") }
        Text("2. Copiá tu clave y pegala aquí. El inicio de sesión ocurre en Google; LEO no recibe tu contraseña ni lee otras apps.")
        Text("El nivel gratuito tiene cuotas y depende de tu proyecto y del modelo. Si tu proyecto tiene facturación, las llamadas pueden tener costo. Google puede usar el contenido del nivel gratuito para mejorar sus productos.", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { open(LeoGoogleProvider.USAGE) }) { Text("Ver mi uso") }
            TextButton(onClick = { open(LeoGoogleProvider.PRICING) }) { Text("Cuotas y precios") }
        }
    }
    if (advanced) {
        OutlinedTextField(endpoint, { endpoint = it; consent = false }, label = { Text("URL HTTPS de chat/completions") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(model, { model = it; consent = false }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
    } else if (google) {
        LeoGoogleProvider.conversationModels.forEach { option ->
            FilterChip(selected = model == option, onClick = { model = option; consent = false }, enabled = !busy,
                label = { Text(if (option == LeoGoogleProvider.MODEL) "Rápido · Gemini Flash-Lite" else "Más capacidad · Gemini Flash") })
        }
        Text("Modelo: $model", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(secret, { secret = it }, label = { Text(if (google) "Pegá tu clave de Gemini" else "Clave API del proveedor") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
    Text(if (reusableKey) "Clave guardada y cifrada. Dejá el campo vacío para conservarla." else "Tu clave se guarda cifrada en este teléfono.", style = MaterialTheme.typography.bodySmall)
    ProviderPermissionRow(consent, !busy, { consent = it }, "Autorizo enviar mis preguntas a este proveedor y hacer una prueba de conexión.")
    ProviderPermissionRow(preferRemote, !busy, { preferRemote = it }, "Usarlo como modelo principal de conversación. Si falla, continuar con las funciones locales.")
    ProviderPermissionRow(historyConsent, !busy, { historyConsent = it }, "Compartir hasta tres intercambios recientes para continuar la conversación. Pueden contener datos personales.")
    ProviderPermissionRow(researchConsent, !busy, { researchConsent = it }, "Permitir resumir los extractos de búsquedas con citas verificadas.")
    Text("Los cambios se aplican al comprobar y activar.", style = MaterialTheme.typography.bodySmall)
    Text("La base de recuerdos personales no se adjunta. «Solo local» evita el envío al modelo.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        busy = true
        scope.launch {
            try {
                val stored = if (secret.isBlank() && reusableKey) withContext(Dispatchers.IO) { settings.read() } else null
                val normalizedSecret = secret.trim().ifBlank { stored?.takeIf { it.endpoint == endpoint.trim() }?.key.orEmpty() }
                require(consent && LeoGoogleProvider.validCredential(endpoint, normalizedSecret))
                val result = LeoInferenceActivation.activate(
                    probe = { client.request(endpoint, model, normalizedSecret, LeoInferenceActivation.PROBE) },
                    persist = { withContext(Dispatchers.IO) {
                        coroutineContext.ensureActive()
                        settings.save(endpoint, model, normalizedSecret, consent, researchConsent, historyConsent, preferRemote)
                    } },
                )
                if (result.answer != null) {
                    secret = ""; enabled = settings.enabled
                    consent = enabled; researchConsent = settings.researchAllowed; historyConsent = settings.historyAllowed
                    status = "Conexión comprobada y activada. Ya podés conversar con LEO."
                } else status = result.description()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { status = "No pude activar la conexión. Revisá modelo, clave y almacenamiento seguro del teléfono." }
            finally { busy = false }
        }
    }, enabled = consent && !busy && model.isNotBlank() && (!google || secret.isNotBlank() || reusableKey)) {
        Text(if (busy) "COMPROBANDO…" else "COMPROBAR Y ACTIVAR")
    }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                val config = withContext(Dispatchers.IO) { settings.read() }
                val result = config?.let { client.request(it.endpoint, it.model, it.key, LeoInferenceActivation.PROBE) }
                status = if (config == null || !settings.isCurrent(config)) "La conexión guardada ya no está disponible."
                    else if (LeoInferenceActivation.validProbe(result?.answer)) "Conexión guardada comprobada."
                    else result?.description() ?: "No se obtuvo una respuesta válida."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { status = "No pude comprobar la conexión guardada." }
            finally { busy = false }
        }
    }, enabled = enabled && !busy) { Text("PROBAR CONEXIÓN GUARDADA") }
    OutlinedButton(onClick = {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { settings.clear() }
                enabled = false; consent = false; researchConsent = false; historyConsent = false; secret = ""
                status = "Proveedor desactivado y credencial eliminada de este teléfono."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { status = "No pude borrar la conexión. Intentá nuevamente." }
            finally { busy = false }
        }
    }, enabled = !busy) { Text("DESCONECTAR Y BORRAR CLAVE") }
    if (status.isNotBlank()) Text(status)
}

@Composable
private fun ProviderPermissionRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
        .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}
