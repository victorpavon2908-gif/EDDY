package com.niko.assistant.inference.local

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

@Composable
fun LeoLocalModelPanel() {
    val app = LocalContext.current.applicationContext
    var selected by remember { mutableStateOf(LeoLocalModelStore(app).spec) }
    val store = remember(selected) { LeoLocalModelStore(app, selected) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf(if (store.enabled) "Activado: ${store.spec.id}" else "Modelo general local desactivado") }
    var deleteConfirm by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Text("CONOCIMIENTO Y CÓDIGO SIN CONEXIÓN", style = MaterialTheme.typography.titleMedium)
    Text("Modelos oficiales Apache 2.0. Requieren Android de 64 bits y RAM disponible suficiente. El modelo grande pesa 4,28 GB, no 5 GB. Conservar ambos ocupa 6,11 GB; el límite total de LEO es 10 GB. Puede responder incorrectamente. Las preguntas y el contexto permanecen en el teléfono. No ejecuta código ni acciones por su cuenta.")
    LeoLocalModelCatalog.all.forEach { option ->
        OutlinedButton(onClick = { selected = option }, enabled = job == null) {
            Text((if (selected == option) "✓ " else "") + option.label)
        }
    }
    Text(status)
    Button(onClick = { confirm = true }, enabled = job == null) { Text("ACTUALIZAR CEREBRO DE LEO") }
    OutlinedButton(onClick = { job?.cancel() }, enabled = job != null) { Text("CANCELAR DESCARGA O PRUEBA") }
    OutlinedButton(onClick = {
        scope.launch { withContext(Dispatchers.IO) { store.disable() }; status = "Desactivado. LEO vuelve al cerebro anterior; el archivo se conserva." }
    }, enabled = job == null) { Text("VOLVER AL CEREBRO ANTERIOR") }
    OutlinedButton(onClick = { deleteConfirm = true }, enabled = job == null) { Text("ELIMINAR DESCARGA DEL MODELO") }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false },
        title = { Text("Liberar espacio del modelo") },
        text = { Text("Se eliminarán ${store.spec.label} y su descarga parcial. Tus recuerdos, preferencias y los componentes de voz se conservan. Podés descargarlo nuevamente después.") },
        confirmButton = { TextButton(onClick = {
            deleteConfirm = false
            job = scope.launch {
                try { store.removeDownload(); status = "Descarga eliminada. Los recuerdos se conservan y LEO usa el cerebro anterior." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { status = failure.message ?: "No pude eliminar la descarga." }
                finally { job = null }
            }
        }) { Text("ELIMINAR MODELO") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("CANCELAR") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Descargar modelo local") },
        text = { Text("Versión disponible: ${store.spec.id}. Se descargarán hasta ${store.spec.bytes / 1_000_000_000.0} GB desde Hugging Face. Usá Wi-Fi si querés evitar consumir datos móviles. El modelo anterior se conserva. La activación solo ocurre después de verificar el archivo y una respuesta real.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            job = scope.launch {
                try { store.install { message -> withContext(Dispatchers.Main) { status = message } } }
                catch (cancelled: CancellationException) { status = "Cancelado. La descarga parcial se conserva para reanudar."; throw cancelled }
                catch (failure: Exception) { status = failure.message ?: "No se pudo activar. El cerebro anterior sigue disponible." }
                finally { job = null }
            }
        }) { Text("DESCARGAR Y VALIDAR") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("AHORA NO") } })
}
