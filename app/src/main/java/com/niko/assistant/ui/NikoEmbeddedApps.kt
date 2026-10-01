package com.niko.assistant.ui

import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.ui.generated.GeneratedToolScreen

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
        NikoUiMode.TOOLBOX -> LeoToolbox(onHome)
        NikoUiMode.GENERATED -> GeneratedToolScreen(onHome)
        NikoUiMode.CAMERA -> LeoCameraScreen(false, onHome)
        NikoUiMode.VIDEO -> LeoCameraScreen(true, onHome)
        NikoUiMode.AUDIO_RECORDER -> LeoAudioRecorderScreen(onHome)
        NikoUiMode.MUSIC -> LeoMusicScreen(onHome)
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
private fun AppShell(
    mode: NikoUiMode,
    title: String = mode.title,
    onHome: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    LeoToolSurface(
        mode = mode,
        title = title,
        onHome = onHome,
        content = content,
    )
}

@Composable
private fun CalculatorApp(onHome: () -> Unit) {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    AppShell(NikoUiMode.CALCULATOR, "Calculadora", onHome) {
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(onClick = {
                        when (key) {
                            "C" -> { expression = ""; result = "0" }
                            "⌫" -> expression = expression.dropLast(1)
                            "=" -> result = EmbeddedMath.evaluate(expression) ?: "Error"
                            else -> expression += key
                        }
                    }, modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(18.dp)) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchApp(onHome: () -> Unit) {
    var running by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var accumulated by rememberSaveable { mutableLongStateOf(0L) }
    var now by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (running) { now = SystemClock.elapsedRealtime(); delay(50L) }
        }
    }
    val elapsed = accumulated + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    AppShell(NikoUiMode.STOPWATCH, "Cronómetro", onHome) {
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
    var minutesText by rememberSaveable { mutableStateOf("5") }
    var remaining by rememberSaveable { mutableLongStateOf(0L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var deadline by rememberSaveable { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (running) {
                remaining = CountdownClock.remaining(deadline, SystemClock.elapsedRealtime())
                if (remaining == 0L) { running = false; break }
                delay(200L)
            }
        }
    }
    AppShell(NikoUiMode.TIMER, "Temporizador", onHome) {
        OutlinedTextField(value = minutesText, onValueChange = { minutesText = it.filter(Char::isDigit).take(4) }, label = { Text("Minutos") }, modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            Text(formatCountdown(remaining), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { running = false; remaining = 0L }) { Text("Reiniciar") }
            Button(onClick = {
                if (running) {
                    remaining = CountdownClock.remaining(deadline, SystemClock.elapsedRealtime())
                    running = false
                } else {
                    if (remaining == 0L) remaining = (minutesText.toLongOrNull() ?: 0L).coerceIn(0, 1440) * 60_000L
                    deadline = SystemClock.elapsedRealtime() + remaining
                    running = remaining > 0L
                }
            }) { Text(if (running) "Pausar" else "Iniciar") }
        }
    }
}

@Composable
private fun ClockApp(onHome: () -> Unit) {
    var now by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1000L) }
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm:ss a", Locale.forLanguageTag("es-NI")).format(Date(now)) }
    val date = remember(now / 60_000L) { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-NI")).format(Date(now)) }
    AppShell(NikoUiMode.CLOCK, "Reloj", onHome) {
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
    var note by rememberSaveable { mutableStateOf(prefs.getString("quick_note", "").orEmpty()) }
    var savedNote by remember { mutableStateOf(prefs.getString("quick_note", "").orEmpty()) }
    val latestNote by rememberUpdatedState(note)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun saveNote(value: String) {
        prefs.edit().putString("quick_note", value).apply()
        savedNote = value
    }
    LaunchedEffect(note) {
        if (note != savedNote) { delay(500L); saveNote(note) }
    }
    DisposableEffect(lifecycle, prefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) saveNote(latestNote)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); saveNote(latestNote) }
    }
    AppShell(NikoUiMode.NOTES, "Notas", onHome) {
        OutlinedTextField(value = note, onValueChange = { note = it.take(20_000) }, label = { Text("Nota rápida") }, modifier = Modifier.fillMaxWidth().height(320.dp))
        Text(if (note == savedNote) "Guardado en este teléfono" else "Guardando…", style = MaterialTheme.typography.bodySmall)
        Text("${note.length} / 20000 caracteres", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { saveNote(note) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar ahora") }
    }
}

@Composable
private fun ConverterApp(onHome: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("1") }
    var mode by rememberSaveable { mutableStateOf("km-mi") }
    val value = input.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    val output = value?.let { number -> when (mode) {
        "km-mi" -> number * 0.621371
        "mi-km" -> number / 0.621371
        "c-f" -> number * 9.0 / 5.0 + 32.0
        else -> (number - 32.0) * 5.0 / 9.0
    } }?.takeIf { it.isFinite() }
    AppShell(NikoUiMode.CONVERTER, "Conversor", onHome) {
        OutlinedTextField(value = input, onValueChange = { input = it.take(40).filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == '-' } }, label = { Text("Valor") }, isError = input.isNotBlank() && output == null, supportingText = { if (input.isNotBlank() && output == null) Text("Escribí un número válido") }, modifier = Modifier.fillMaxWidth())
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("km-mi" to "km → mi", "mi-km" to "mi → km", "c-f" to "°C → °F", "f-c" to "°F → °C").forEach { (id, label) ->
                OutlinedButton(onClick = { mode = id }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) { Text(output?.let { "%.3f".format(it) } ?: "—", modifier = Modifier.padding(22.dp), style = MaterialTheme.typography.displaySmall) }
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
