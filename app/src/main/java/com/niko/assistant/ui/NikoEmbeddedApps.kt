package com.niko.assistant.ui

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NikoEmbeddedApp(mode: NikoUiMode, onHome: () -> Unit) {
    when (mode) {
        NikoUiMode.VOICE_DIAGNOSTICS -> LeoVoiceDiagnosticsScreen(onHome)
        NikoUiMode.CALCULATOR -> CalculatorApp(onHome)
        NikoUiMode.STOPWATCH -> StopwatchApp(onHome)
        NikoUiMode.TIMER -> TimerApp(onHome)
        NikoUiMode.CLOCK -> ClockApp(onHome)
        NikoUiMode.NOTES -> NotesApp(onHome)
        NikoUiMode.CONVERTER -> ConverterApp(onHome)
        NikoUiMode.ASSISTANT -> Unit
    }
}

@Composable
private fun AppShell(title: String, onHome: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onHome) { Text("LEO") }
        }
        content()
    }
}

@Composable
private fun CalculatorApp(onHome: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    AppShell("Calculadora", onHome) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.End) {
                Text(expression.ifBlank { " " }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(result, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }
        listOf(
            listOf("C", "(", ")", "÷"), listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "="),
        ).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Button(onClick = {
                        when (key) {
                            "C" -> { expression = ""; result = "0" }
                            "⌫" -> expression = expression.dropLast(1)
                            "=" -> result = EmbeddedMath.evaluate(expression) ?: "Error"
                            else -> expression += key
                        }
                    }, modifier = Modifier.height(58.dp), shape = RoundedCornerShape(18.dp)) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchApp(onHome: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var accumulated by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running) { now = SystemClock.elapsedRealtime(); delay(30L) }
    }
    val elapsed = accumulated + if (running) now - startedAt else 0L
    AppShell("Cronómetro", onHome) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Text(formatMillis(elapsed), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { running = false; accumulated = 0L; startedAt = 0L }, modifier = Modifier.height(58.dp)) { Text("Reiniciar") }
            Button(onClick = {
                if (running) { accumulated += SystemClock.elapsedRealtime() - startedAt; running = false }
                else { startedAt = SystemClock.elapsedRealtime(); now = startedAt; running = true }
            }, modifier = Modifier.height(58.dp)) { Text(if (running) "Pausar" else "Iniciar") }
        }
    }
}

@Composable
private fun TimerApp(onHome: () -> Unit) {
    var minutesText by remember { mutableStateOf("5") }
    var remaining by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) { delay(1000); remaining = (remaining - 1000).coerceAtLeast(0) }
        if (remaining == 0L) running = false
    }
    AppShell("Temporizador", onHome) {
        OutlinedTextField(value = minutesText, onValueChange = { minutesText = it.filter(Char::isDigit).take(4) }, label = { Text("Minutos") }, modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Text(formatCountdown(remaining), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { running = false; remaining = 0L }) { Text("Reiniciar") }
            Button(onClick = {
                if (remaining == 0L) remaining = (minutesText.toLongOrNull() ?: 0L).coerceIn(0, 1440) * 60_000L
                running = !running && remaining > 0L
            }) { Text(if (running) "Pausar" else "Iniciar") }
        }
    }
}

@Composable
private fun ClockApp(onHome: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500L) } }
    val time = remember(now) { SimpleDateFormat("h:mm:ss a", Locale.forLanguageTag("es-NI")).format(Date(now)) }
    val date = remember(now / 60_000L) { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-NI")).format(Date(now)) }
    AppShell("Reloj", onHome) {
        Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(date, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun NotesApp(onHome: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UpgradeIdentity.notesPreferences, Context.MODE_PRIVATE) }
    var note by remember { mutableStateOf(prefs.getString("quick_note", "").orEmpty()) }
    AppShell("Notas", onHome) {
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota rápida") }, modifier = Modifier.fillMaxWidth().height(320.dp))
        Button(onClick = { prefs.edit().putString("quick_note", note).apply() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar en LEO") }
    }
}

@Composable
private fun ConverterApp(onHome: () -> Unit) {
    var input by remember { mutableStateOf("1") }
    var mode by remember { mutableStateOf("km-mi") }
    val value = input.toDoubleOrNull() ?: 0.0
    val output = when (mode) {
        "km-mi" -> value * 0.621371
        "mi-km" -> value / 0.621371
        "c-f" -> value * 9.0 / 5.0 + 32.0
        else -> (value - 32.0) * 5.0 / 9.0
    }
    AppShell("Conversor", onHome) {
        OutlinedTextField(value = input, onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } }, label = { Text("Valor") }, modifier = Modifier.fillMaxWidth())
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("km-mi" to "km → mi", "mi-km" to "mi → km", "c-f" to "°C → °F", "f-c" to "°F → °C").forEach { (id, label) ->
                OutlinedButton(onClick = { mode = id }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) { Text("%.3f".format(output), modifier = Modifier.padding(22.dp), style = MaterialTheme.typography.displaySmall) }
    }
}

private fun formatMillis(milliseconds: Long): String {
    val totalHundredths = milliseconds / 10
    val hundredths = totalHundredths % 100
    val totalSeconds = totalHundredths / 100
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
}

private fun formatCountdown(milliseconds: Long): String {
    val total = milliseconds / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private object EmbeddedMath {
    private val mc = MathContext.DECIMAL64
    fun evaluate(raw: String): String? = runCatching {
        val parser = Parser(raw.replace('×', '*').replace('÷', '/').replace('−', '-'))
        val result = parser.expression(); parser.spaces(); check(parser.end())
        result.stripTrailingZeros().toPlainString()
    }.getOrNull()
    private class Parser(private val s: String) {
        var i = 0
        fun end() = i >= s.length
        fun spaces() { while (!end() && s[i].isWhitespace()) i++ }
        fun expression(): BigDecimal { var v = term(); while (true) { spaces(); v = when { take('+') -> v.add(term(), mc); take('-') -> v.subtract(term(), mc); else -> return v } } }
        private fun term(): BigDecimal { var v = factor(); while (true) { spaces(); v = when { take('*') -> v.multiply(factor(), mc); take('/') -> v.divide(factor(), mc); else -> return v } } }
        private fun factor(): BigDecimal { spaces(); if (take('+')) return factor(); if (take('-')) return factor().negate(mc); if (take('(')) { val v = expression(); check(take(')')); return v }; val start = i; while (!end() && (s[i].isDigit() || s[i] == '.')) i++; check(i > start); return s.substring(start, i).toBigDecimal(mc) }
        private fun take(c: Char): Boolean { spaces(); if (!end() && s[i] == c) { i++; return true }; return false }
    }
}
