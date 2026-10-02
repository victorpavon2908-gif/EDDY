package com.niko.assistant.ui.generated

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.niko.assistant.actions.ActionExecutor

@Composable
internal fun GeneratedAppComponent(component: GeneratedToolComponent) {
    when (component.type) {
        "search_box" -> GeneratedSearchBox(component)
        "tabs" -> GeneratedTabs(component)
        "feed" -> GeneratedFeed(component)
        "chat" -> GeneratedChat(component)
        "calendar" -> GeneratedCalendar(component)
        "kanban" -> GeneratedKanban(component)
        "gallery" -> GeneratedGallery(component)
        "profile" -> GeneratedProfile(component)
        "browser" -> GeneratedBrowser(component)
    }
}

@Composable
private fun GeneratedSearchBox(component: GeneratedToolComponent) {
    var query by rememberSaveable(component.id) { mutableStateOf("") }
    val visible = component.items.filter {
        query.isBlank() || it.contains(query, ignoreCase = true)
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                label = { Text(component.label.ifBlank { "Buscar" }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            visible.take(12).forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium)
            }
            if (component.items.isNotEmpty() && visible.isEmpty()) {
                Text("Sin coincidencias", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GeneratedTabs(component: GeneratedToolComponent) {
    val tabs = component.items.take(6).ifEmpty { listOf("Inicio", "Explorar", "Perfil") }
    var selected by rememberSaveable(component.id) { mutableIntStateOf(0) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { index, tab ->
                    if (selected == index) {
                        Button(onClick = { selected = index }, modifier = Modifier.weight(1f)) {
                            Text(tab, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(onClick = { selected = index }, modifier = Modifier.weight(1f)) {
                            Text(tab, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Text(
                component.text.ifBlank { "Sección: ${tabs[selected]}" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GeneratedFeed(component: GeneratedToolComponent) {
    val posts = component.items.ifEmpty {
        listOf(
            "Bienvenido|Tu espacio está listo.",
            "LEO|Esta interfaz fue generada para vos.",
        )
    }
    val likes = remember(component.id) {
        mutableStateListOf<Int>().apply { repeat(posts.size) { add(0) } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(component.label.ifBlank { "Feed" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        posts.forEachIndexed { index, raw ->
            val parts = raw.split("|", limit = 2)
            val author = parts.getOrNull(0).orEmpty().ifBlank { "Usuario" }
            val body = parts.getOrNull(1).orEmpty().ifBlank { raw }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(author, fontWeight = FontWeight.Bold)
                    Text(body)
                    OutlinedButton(onClick = { likes[index] = likes[index] + 1 }) {
                        Text("Me gusta ${likes[index]}")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedChat(component: GeneratedToolComponent) {
    val messages = remember(component.id) {
        mutableStateListOf<String>().apply { addAll(component.items.take(20)) }
    }
    var draft by rememberSaveable(component.id + "_draft") { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label.ifBlank { "Chat" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            messages.takeLast(12).forEachIndexed { index, message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(10.dp),
                ) {
                    Text(message)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(300) },
                    label = { Text("Mensaje") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                Button(
                    onClick = {
                        val clean = draft.trim()
                        if (clean.isNotBlank()) messages += clean
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun GeneratedCalendar(component: GeneratedToolComponent) {
    var selectedDay by rememberSaveable(component.id) { mutableIntStateOf(1) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label.ifBlank { "Calendario" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            (1..31).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (selectedDay == day) {
                            Button(onClick = { selectedDay = day }, modifier = Modifier.weight(1f)) {
                                Text(day.toString())
                            }
                        } else {
                            OutlinedButton(onClick = { selectedDay = day }, modifier = Modifier.weight(1f)) {
                                Text(day.toString())
                            }
                        }
                    }
                    repeat(7 - week.size) {
                        Box(Modifier.weight(1f))
                    }
                }
            }
            Text("Día seleccionado: $selectedDay", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GeneratedKanban(component: GeneratedToolComponent) {
    val columns = listOf("Pendiente", "En curso", "Hecho")
    val tasks = remember(component.id) {
        mutableStateListOf<Pair<String, String>>().apply {
            component.items.forEach { raw ->
                val parts = raw.split("|", limit = 2)
                add((parts.getOrNull(0)?.takeIf { it in columns } ?: columns.first()) to parts.getOrElse(1) { raw })
            }
        }
    }
    var draft by rememberSaveable(component.id + "_draft") { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(component.label.ifBlank { "Tablero" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        columns.forEach { column ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(column, fontWeight = FontWeight.Bold)
                    tasks.filter { it.first == column }.forEach { (_, task) ->
                        Text("• $task")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(120) },
                label = { Text("Nueva tarea") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    val clean = draft.trim()
                    if (clean.isNotBlank()) tasks += columns.first() to clean
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Text("Agregar") }
        }
    }
}

@Composable
private fun GeneratedGallery(component: GeneratedToolComponent) {
    val items = component.items.take(12).ifEmpty {
        listOf("Imagen 1", "Imagen 2", "Imagen 3", "Imagen 4", "Imagen 5", "Imagen 6")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(component.label.ifBlank { "Galería" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item, modifier = Modifier.padding(8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GeneratedProfile(component: GeneratedToolComponent) {
    val lines = component.items
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(component.label.take(1).uppercase().ifBlank { "L" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Text(component.label.ifBlank { "Perfil" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (component.text.isNotBlank()) Text(component.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lines.take(8).forEach { Text(it) }
        }
    }
}

@Composable
private fun GeneratedBrowser(component: GeneratedToolComponent) {
    val context = LocalContext.current
    val executor = remember { ActionExecutor(context.applicationContext) }
    var query by rememberSaveable(component.id) { mutableStateOf(component.payload.ifBlank { component.text }) }
    var status by rememberSaveable(component.id + "_status") { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.label.ifBlank { "Navegador" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(200) },
                label = { Text("Buscar en Internet") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { status = executor.searchWeb(query).spokenMessage },
                modifier = Modifier.fillMaxWidth(),
                enabled = query.isNotBlank(),
            ) { Text("BUSCAR") }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
