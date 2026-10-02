package com.niko.assistant.ui.generated

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.niko.assistant.ui.LeoToolSurface
import com.niko.assistant.ui.NikoUiMode
import java.math.BigDecimal
import java.math.MathContext
import kotlinx.coroutines.delay

@Composable
internal fun GeneratedToolScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val spec = remember { GeneratedToolStore.read(context) }

    LeoToolSurface(
        mode = NikoUiMode.GENERATED,
        title = spec?.title ?: "Herramienta generada",
        onHome = onHome,
    ) {
        if (spec == null) {
            Text(
                "No hay una herramienta generada disponible. Pedile a LEO que se convierta en algo nuevo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LeoToolSurface
        }

        if (spec.subtitle.isNotBlank()) {
            Text(
                spec.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        spec.components.forEach { component ->
            when (component.type) {
                "text" -> GeneratedText(component)
                "text_input" -> GeneratedTextInput(component)
                "number_input" -> GeneratedNumberInput(component)
                "counter" -> GeneratedCounter(component)
                "toggle" -> GeneratedToggle(component)
                "checklist" -> GeneratedChecklist(component)
                "list" -> GeneratedList(component)
                "timer" -> GeneratedTimer(component)
                "calculator" -> GeneratedCalculator(component)
                "metric" -> GeneratedMetric(component)
                else -> GeneratedAdvancedComponent(component)
            }
        }
    }
}

@Composable
private fun GeneratedText(component: GeneratedToolComponent) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (component.text.isNotBlank()) {
                Text(component.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GeneratedTextInput(component: GeneratedToolComponent) {
    var value by rememberSaveable(component.id) { mutableStateOf(component.text) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(2_000) },
        label = { Text(component.label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 6,
    )
}

@Composable
private fun GeneratedNumberInput(component: GeneratedToolComponent) {
    var value by rememberSaveable(component.id) {
        mutableStateOf(
            if (component.initial == 0.0) "" else component.initial.toString(),
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            value = next.filter { it.isDigit() || it in ".-," }.take(32)
        },
        label = { Text(component.label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun GeneratedCounter(component: GeneratedToolComponent) {
    var count by rememberSaveable(component.id) { mutableLongStateOf(component.initial.toLong()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium)
            Text(count.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { count-- }) { Text("−1") }
                OutlinedButton(onClick = { count = component.initial.toLong() }) { Text("Reiniciar") }
                Button(onClick = { count++ }) { Text("+1") }
            }
        }
    }
}

@Composable
private fun GeneratedToggle(component: GeneratedToolComponent) {
    var checked by rememberSaveable(component.id) { mutableStateOf(component.initial > 0.0) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(component.label, fontWeight = FontWeight.SemiBold)
                if (component.text.isNotBlank()) {
                    Text(component.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = { checked = it })
        }
    }
}

@Composable
private fun GeneratedChecklist(component: GeneratedToolComponent) {
    val values = remember(component.id) {
        mutableStateMapOf<String, Boolean>().apply {
            component.items.forEach { put(it, false) }
        }
    }
    var draft by rememberSaveable(component.id + "_draft") { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            values.forEach { (item, checked) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { values[item] = it })
                    Text(item, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    label = { Text("Nuevo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val clean = draft.trim()
                        if (clean.isNotBlank() && clean !in values) values[clean] = false
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Agregar") }
            }
        }
    }
}

@Composable
private fun GeneratedList(component: GeneratedToolComponent) {
    val items = remember(component.id) { mutableStateListOf<String>().apply { addAll(component.items) } }
    var draft by rememberSaveable(component.id + "_draft") { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            items.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("• $item", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { if (index in items.indices) items.removeAt(index) }) {
                        Text("Quitar")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    label = { Text("Agregar elemento") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val clean = draft.trim()
                        if (clean.isNotBlank()) items += clean
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Agregar") }
            }
        }
    }
}

@Composable
private fun GeneratedTimer(component: GeneratedToolComponent) {
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var startedAt by rememberSaveable(component.id + "_start") { mutableLongStateOf(0L) }
    var accumulated by rememberSaveable(component.id + "_acc") { mutableLongStateOf(0L) }
    var now by rememberSaveable(component.id + "_now") { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(100L)
        }
    }

    val elapsed = accumulated + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium)
            Text(formatMillis(elapsed), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    running = false
                    accumulated = 0L
                    startedAt = 0L
                }) { Text("Reiniciar") }
                Button(onClick = {
                    if (running) {
                        accumulated += SystemClock.elapsedRealtime() - startedAt
                        running = false
                    } else {
                        startedAt = SystemClock.elapsedRealtime()
                        now = startedAt
                        running = true
                    }
                }) { Text(if (running) "Pausar" else "Iniciar") }
            }
        }
    }
}

@Composable
private fun GeneratedCalculator(component: GeneratedToolComponent) {
    var expression by rememberSaveable(component.id + "_expr") { mutableStateOf("") }
    var result by rememberSaveable(component.id + "_result") { mutableStateOf("0") }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = expression,
                onValueChange = { expression = it.take(120) },
                label = { Text("Expresión") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { result = SafeGeneratedMath.evaluate(expression) ?: "Error" }) { Text("Calcular") }
                Text(result, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GeneratedMetric(component: GeneratedToolComponent) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(component.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                component.text.ifBlank { component.initial.toString() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatMillis(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private object SafeGeneratedMath {
    private val mathContext = MathContext.DECIMAL64

    fun evaluate(raw: String): String? = runCatching {
        require(raw.length <= 120)
        require(raw.all { it.isDigit() || it.isWhitespace() || it in "+-*/().," })
        val parser = Parser(raw.replace(',', '.'))
        val result = parser.expression()
        parser.spaces()
        check(parser.end())
        result.stripTrailingZeros().toPlainString()
    }.getOrNull()

    private class Parser(private val source: String) {
        private var index = 0

        fun end(): Boolean = index >= source.length
        fun spaces() { while (!end() && source[index].isWhitespace()) index++ }

        fun expression(): BigDecimal {
            var value = term()
            while (true) {
                spaces()
                value = when {
                    take('+') -> value.add(term(), mathContext)
                    take('-') -> value.subtract(term(), mathContext)
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                spaces()
                value = when {
                    take('*') -> value.multiply(factor(), mathContext)
                    take('/') -> value.divide(factor(), mathContext)
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            spaces()
            if (take('+')) return factor()
            if (take('-')) return factor().negate(mathContext)
            if (take('(')) {
                val value = expression()
                check(take(')'))
                return value
            }
            val start = index
            while (!end() && (source[index].isDigit() || source[index] == '.')) index++
            check(index > start)
            return source.substring(start, index).toBigDecimal(mathContext)
        }

        private fun take(char: Char): Boolean {
            spaces()
            if (!end() && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
