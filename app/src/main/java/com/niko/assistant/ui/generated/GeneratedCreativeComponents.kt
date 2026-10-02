package com.niko.assistant.ui.generated

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
internal fun GeneratedCreativeComponent(component: GeneratedToolComponent) {
    when (component.type) {
        "randomizer" -> RandomizerBlock(component)
        "dice" -> DiceBlock(component)
        "flashcards" -> FlashcardsBlock(component)
        "quiz" -> QuizBlock(component)
        "drawing_pad" -> DrawingPadBlock(component)
    }
}

@Composable
private fun RandomizerBlock(component: GeneratedToolComponent) {
    val options = component.items.ifEmpty { listOf("Opción 1", "Opción 2", "Opción 3") }
    var selected by rememberSaveable(component.id) { mutableStateOf(options.first()) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(selected, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(
                onClick = { selected = options[Random.nextInt(options.size)] },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ELEGIR AL AZAR")
            }
        }
    }
}

@Composable
private fun DiceBlock(component: GeneratedToolComponent) {
    val sides = component.max.toInt().coerceIn(2, 100)
    var value by rememberSaveable(component.id) { mutableIntStateOf(1) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Button(
                onClick = { value = Random.nextInt(1, sides + 1) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("LANZAR DADO")
            }
        }
    }
}

@Composable
private fun FlashcardsBlock(component: GeneratedToolComponent) {
    val cards = component.items.ifEmpty { listOf("Pregunta|Respuesta") }
    var index by rememberSaveable(component.id + "_index") { mutableIntStateOf(0) }
    var reveal by rememberSaveable(component.id + "_reveal") { mutableStateOf(false) }

    val current = cards[index.coerceIn(0, cards.lastIndex)].split("|", limit = 2)
    val front = current.getOrNull(0).orEmpty().trim()
    val back = current.getOrNull(1).orEmpty().trim().ifBlank { "Sin respuesta" }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (reveal) back else front,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        index = if (index <= 0) cards.lastIndex else index - 1
                        reveal = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("ANTERIOR") }
                Button(
                    onClick = { reveal = !reveal },
                    modifier = Modifier.weight(1f),
                ) { Text(if (reveal) "PREGUNTA" else "RESPUESTA") }
                OutlinedButton(
                    onClick = {
                        index = if (index >= cards.lastIndex) 0 else index + 1
                        reveal = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("SIGUIENTE") }
            }
        }
    }
}

@Composable
private fun QuizBlock(component: GeneratedToolComponent) {
    val questions = component.items.ifEmpty { listOf("Pregunta|Respuesta") }
    var index by rememberSaveable(component.id + "_q") { mutableIntStateOf(0) }
    var showAnswer by rememberSaveable(component.id + "_a") { mutableStateOf(false) }
    var correct by rememberSaveable(component.id + "_ok") { mutableIntStateOf(0) }

    val current = questions[index.coerceIn(0, questions.lastIndex)].split("|", limit = 2)
    val question = current.getOrNull(0).orEmpty().trim()
    val answer = current.getOrNull(1).orEmpty().trim().ifBlank { "Sin respuesta configurada" }

    fun advance(markCorrect: Boolean) {
        if (markCorrect) correct += 1
        index = if (index >= questions.lastIndex) 0 else index + 1
        showAnswer = false
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Aciertos $correct")
            }
            Text(question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (showAnswer) {
                Text(answer, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { advance(false) }, modifier = Modifier.weight(1f)) {
                        Text("FALLÉ")
                    }
                    Button(onClick = { advance(true) }, modifier = Modifier.weight(1f)) {
                        Text("ACERTÉ")
                    }
                }
            } else {
                Button(onClick = { showAnswer = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("VER RESPUESTA")
                }
            }
        }
    }
}

@Composable
private fun DrawingPadBlock(component: GeneratedToolComponent) {
    var strokes by remember(component.id) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember(component.id) { mutableStateOf<List<Offset>>(emptyList()) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = {
                        strokes = emptyList()
                        current = emptyList()
                    },
                ) { Text("BORRAR") }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f),
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(component.id) {
                            detectDragGestures(
                                onDragStart = { start -> current = listOf(start) },
                                onDragEnd = {
                                    if (current.size > 1) strokes = strokes + listOf(current)
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                            )
                        },
                ) {
                    drawRect(Color(0xFFF7F9F8))
                    val all = strokes + listOf(current)
                    all.forEach { path ->
                        for (i in 1 until path.size) {
                            drawLine(
                                color = Color(0xFF26332F),
                                start = path[i - 1],
                                end = path[i],
                                strokeWidth = 7f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
            Text(
                component.text.ifBlank { "Dibujá con el dedo." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
