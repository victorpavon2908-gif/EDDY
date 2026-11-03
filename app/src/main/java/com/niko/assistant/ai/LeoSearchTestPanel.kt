package com.niko.assistant.ai

import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.niko.assistant.modes.LeoMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The settings button uses the same search implementation as a spoken request. */
@Composable
internal fun LeoSearchTestPanel(mode: LeoMode,
    search: suspend (String, LeoMode, (LeoNativeWebSearch.FetchDiagnostic) -> Unit) -> NikoAiReply =
        { query, profile, report -> LeoNativeWebSearch.search(query, profile, report) }) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = {
        busy = true; feedback = "Consultando fuentes…"; details = ""; expanded = false
        scope.launch {
            val events = java.util.Collections.synchronizedList(mutableListOf<LeoNativeWebSearch.FetchDiagnostic>())
            try {
                val result = search("¿Qué es la energía solar?", mode) { events += it }
                feedback = when {
                    result.webUsed && result.sources.isNotEmpty() && result.passages.any { it.pageRead } ->
                        "Búsqueda disponible: ${result.sources.size} fuentes y lectura de páginas completada."
                    result.sources.isNotEmpty() -> "Encontré ${result.sources.size} fuentes, pero no pude leer suficiente contenido. Podés abrirlas o reintentar."
                    else -> result.text
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { feedback = "No se pudo completar la prueba. Revisá la conexión y reintentá." }
            finally {
                details = LeoWebDiagnostics.describe(synchronized(events) { events.toList() })
                busy = false
            }
        }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Comprobando…" else "Probar búsqueda") }
    if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodyMedium)
    if (details.isNotBlank()) {
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar diagnóstico" else "Ver diagnóstico de la prueba") }
        if (expanded) Text(details, style = MaterialTheme.typography.bodySmall)
    }
}
