package com.niko.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled manual is also published in docs; no browser or network is required. */
@Composable
fun LeoUserGuideDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var manual by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        manual = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("manual_usuario_leo.md").bufferedReader().use { it.readText() } }.getOrNull()
        }
        failed = manual == null
    }
    val sections = remember(manual, query) {
        manual.orEmpty().split(Regex("(?m)(?=^## )"))
            .filter { it.isNotBlank() && it.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Guía de EDDY / LEO", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Cerrar") }
                }
                Text("Primeros pasos, funciones y solución de problemas. Disponible sin Internet.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Buscar: voz, internet, notas…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                when {
                    failed -> Text("No pude abrir la guía. Cerrá y volvé a intentar.")
                    manual == null -> CircularProgressIndicator()
                    sections.isEmpty() -> Text("No hay coincidencias. Probá otra palabra o borrá la búsqueda.")
                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(sections) { section ->
                            val lines = section.trim().lines()
                            val title = lines.first().trimStart('#', ' ')
                            val body = lines.drop(1).joinToString("\n").replace("**", "").trim()
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(body, style = MaterialTheme.typography.bodyLarge)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
