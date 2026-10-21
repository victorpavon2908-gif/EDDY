package com.niko.assistant.inference.local

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import androidx.work.WorkManager
import androidx.work.WorkInfo

@Composable
fun LeoLocalModelPanel() {
    val app = LocalContext.current.applicationContext
    var selected by remember { mutableStateOf(LeoLocalModelStore(app).spec) }
    val store = remember(selected) { LeoLocalModelStore(app, selected) }
    val scope = rememberCoroutineScope()
    val workManager = remember { WorkManager.getInstance(app) }
    val work by remember { workManager.getWorkInfosForUniqueWorkFlow(LeoModelDownloadWorker.NAME) }.collectAsState(emptyList())
    val activeWork = work.firstOrNull { !it.state.isFinished }
    val downloading = activeWork != null
    val progress = activeWork?.progress?.getString(LeoModelDownloadWorker.STATUS)
        ?: activeWork?.let { "Descarga pendiente de conexión o recursos; se reanudará cuando Android lo permita." }
    val completedStatus = work.firstOrNull()?.let {
        if (it.state == WorkInfo.State.CANCELLED) "Descarga cancelada; parcial conservado para reanudar."
        else it.outputData.getString(LeoModelDownloadWorker.STATUS)
    }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf(if (store.enabled) "Activado: ${store.spec.id}" else "Modelo general local desactivado") }
    var deleteConfirm by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Text("CONOCIMIENTO Y CÓDIGO SIN CONEXIÓN", style = MaterialTheme.typography.titleMedium)
    Text("Modelos oficiales Apache 2.0. Requieren Android de 64 bits y RAM disponible suficiente. El modelo grande pesa 4,28 GB, no 5 GB. Conservar ambos ocupa 6,11 GB; el límite total de LEO es 10 GB. Puede responder incorrectamente. Las preguntas y el contexto permanecen en el teléfono. No ejecuta código ni acciones por su cuenta.")
    LeoLocalModelCatalog.all.forEach { option ->
        OutlinedButton(onClick = { selected = option }, enabled = job == null && !downloading) {
            Text((if (selected == option) "✓ " else "") + option.label)
        }
    }
    LaunchedEffect(completedStatus) { if (completedStatus != null) status = completedStatus }
    Text(progress ?: status)
    Button(onClick = { confirm = true }, enabled = job == null && !downloading) { Text("ACTUALIZAR CEREBRO DE LEO") }
    OutlinedButton(onClick = { workManager.cancelUniqueWork(LeoModelDownloadWorker.NAME) }, enabled = downloading) { Text("CANCELAR DESCARGA O PRUEBA") }
    OutlinedButton(onClick = {
        scope.launch { withContext(Dispatchers.IO) { store.disable() }; status = "Desactivado. LEO vuelve al cerebro anterior; el archivo se conserva." }
    }, enabled = job == null && !downloading) { Text("VOLVER AL CEREBRO ANTERIOR") }
    OutlinedButton(onClick = { deleteConfirm = true }, enabled = job == null && !downloading) { Text("ELIMINAR DESCARGA DEL MODELO") }
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
        text = { Text("Versión disponible: ${store.spec.id}. Se descargarán hasta ${store.spec.bytes / 1_000_000_000.0} GB desde Hugging Face. Puede continuar en segundo plano y reanudarse después de reiniciar la app. Usá Wi-Fi si querés evitar consumir datos móviles. El modelo anterior se conserva. La activación solo ocurre después de verificar el archivo y una respuesta real.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            LeoModelDownloadWorker.enqueue(app, selected)
            status = "Descarga autorizada. Podés salir de Ajustes; seguí el progreso en la notificación."

        }) { Text("DESCARGAR Y VALIDAR") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("AHORA NO") } })
}
