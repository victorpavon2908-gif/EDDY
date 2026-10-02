package com.niko.assistant.ui.generated

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.niko.assistant.actions.ActionExecutor
import com.niko.assistant.brain.SupportedApp
import com.niko.assistant.brain.SystemPanel
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun GeneratedAdvancedComponent(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    when (component.type) {
        "section" -> SectionBlock(component)
        "divider" -> HorizontalDivider()
        "spacer" -> Spacer(Modifier.height(component.initial.coerceIn(4.0, 48.0).toFloat().dp))
        "currency_input" -> NumericField(component, numericState, suffix = component.unit.ifBlank { "C$" })
        "percentage_input" -> NumericField(component, numericState, suffix = "%")
        "date_input" -> SimpleField(component, placeholder = "DD/MM/AAAA")
        "time_input" -> SimpleField(component, placeholder = "HH:MM")
        "slider" -> SliderBlock(component, numericState)
        "progress" -> ProgressBlock(component)
        "rating" -> RatingBlock(component, numericState)
        "multi_choice" -> MultiChoiceBlock(component)
        "single_choice" -> SingleChoiceBlock(component)
        "countdown" -> CountdownBlock(component)
        "goal" -> GoalBlock(component, numericState)
        "scoreboard" -> ScoreboardBlock(component, numericState)
        "key_value" -> KeyValueBlock(component)
        "table" -> TableBlock(component)
        "bar_chart" -> BarChartBlock(component)
        "formula" -> FormulaBlock(component, numericState)
        "action_button" -> ActionButtonBlock(component)
    }
}

@Composable
private fun SectionBlock(component: GeneratedToolComponent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(component.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (component.text.isNotBlank()) {
            Text(component.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SimpleField(component: GeneratedToolComponent, placeholder: String) {
    var value by rememberSaveable(component.id) { mutableStateOf(component.text) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(120) },
        label = { Text(component.label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun NumericField(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
    suffix: String,
) {
    var value by rememberSaveable(component.id) {
        mutableStateOf(if (component.initial == component.min && component.initial == 0.0) "" else compactNumber(component.initial))
    }
    LaunchedEffect(component.id) {
        if (component.initial != 0.0) numericState[component.id] = component.initial
    }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            value = next.filter { it.isDigit() || it in ".,-" }.take(32)
            value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                numericState[component.id] = it.coerceIn(component.min, component.max)
            }
        },
        label = { Text(component.label) },
        supportingText = {
            if (suffix.isNotBlank()) Text(suffix)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SliderBlock(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    var value by rememberSaveable(component.id) { mutableStateOf(component.initial.toFloat()) }
    LaunchedEffect(value) { numericState[component.id] = value.toDouble() }
    val range = component.min.toFloat()..component.max.toFloat()
    val interval = (component.max - component.min).coerceAtLeast(1.0)
    val steps = ((interval / component.step).roundToInt() - 1).coerceIn(0, 100)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(component.label, fontWeight = FontWeight.SemiBold)
                Text(compactNumber(value.toDouble()) + component.unitWithSpace())
            }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = { value = it },
                valueRange = range,
                steps = steps,
            )
        }
    }
}

@Composable
private fun ProgressBlock(component: GeneratedToolComponent) {
    val value = component.initial.coerceIn(component.min, component.max)
    val fraction = ((value - component.min) / (component.max - component.min).coerceAtLeast(0.0001)).toFloat()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(component.label, fontWeight = FontWeight.SemiBold)
                Text(compactNumber(value) + component.unitWithSpace())
            }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            if (component.text.isNotBlank()) {
                Text(component.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RatingBlock(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    var rating by rememberSaveable(component.id) { mutableStateOf(component.initial.roundToInt().coerceIn(0, 5)) }
    LaunchedEffect(rating) { numericState[component.id] = rating.toDouble() }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { index ->
                    OutlinedButton(onClick = { rating = index }) {
                        Text(if (index <= rating) "★" else "☆")
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiChoiceBlock(component: GeneratedToolComponent) {
    val values = remember(component.id) {
        mutableStateMapOf<String, Boolean>().apply {
            component.items.forEach { put(it, false) }
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            values.forEach { (item, checked) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { values[item] = it })
                    Text(item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceBlock(component: GeneratedToolComponent) {
    var selected by rememberSaveable(component.id) { mutableStateOf(component.items.firstOrNull().orEmpty()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            component.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == item, onClick = { selected = item })
                    Text(item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CountdownBlock(component: GeneratedToolComponent) {
    val configuredSeconds = component.initial.toLong().takeIf { it > 0L }
        ?: component.max.toLong().takeIf { it in 1L..86_400L }
        ?: 300L
    var remaining by rememberSaveable(component.id + "_remaining") { mutableLongStateOf(configuredSeconds * 1_000L) }
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var deadline by rememberSaveable(component.id + "_deadline") { mutableLongStateOf(0L) }

    LaunchedEffect(running) {
        while (running) {
            remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remaining == 0L) {
                running = false
                break
            }
            delay(200L)
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(component.label, style = MaterialTheme.typography.titleMedium)
            Text(formatCountdown(remaining), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    running = false
                    remaining = configuredSeconds * 1_000L
                }) { Text("Reiniciar") }
                Button(onClick = {
                    if (running) {
                        remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                        running = false
                    } else {
                        if (remaining <= 0L) remaining = configuredSeconds * 1_000L
                        deadline = SystemClock.elapsedRealtime() + remaining
                        running = true
                    }
                }) { Text(if (running) "Pausar" else "Iniciar") }
            }
        }
    }
}

@Composable
private fun GoalBlock(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    var current by rememberSaveable(component.id) { mutableStateOf(component.initial.coerceIn(component.min, component.max)) }
    LaunchedEffect(current) { numericState[component.id] = current }
    val fraction = ((current - component.min) / (component.max - component.min).coerceAtLeast(0.0001)).toFloat()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${compactNumber(current)} / ${compactNumber(component.max)}${component.unitWithSpace()}")
            }
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { current = (current - component.step).coerceAtLeast(component.min) }) { Text("−") }
                OutlinedButton(onClick = { current = component.min }) { Text("Reiniciar") }
                Button(onClick = { current = (current + component.step).coerceAtMost(component.max) }) { Text("+") }
            }
        }
    }
}

@Composable
private fun ScoreboardBlock(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    val leftName = component.items.getOrNull(0).orEmpty().ifBlank { "Equipo A" }
    val rightName = component.items.getOrNull(1).orEmpty().ifBlank { "Equipo B" }
    var left by rememberSaveable(component.id + "_left") { mutableLongStateOf(component.initial.toLong()) }
    var right by rememberSaveable(component.id + "_right") { mutableLongStateOf(0L) }
    LaunchedEffect(left, right) {
        numericState[component.id + "_left"] = left.toDouble()
        numericState[component.id + "_right"] = right.toDouble()
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreSide(leftName, left, onMinus = { left-- }, onPlus = { left++ }, modifier = Modifier.weight(1f))
                ScoreSide(rightName, right, onMinus = { right-- }, onPlus = { right++ }, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = { left = 0L; right = 0L }, modifier = Modifier.fillMaxWidth()) {
                Text("Reiniciar marcador")
            }
        }
    }
}

@Composable
private fun ScoreSide(
    name: String,
    score: Long,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, fontWeight = FontWeight.SemiBold)
        Text(score.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onMinus) { Text("−") }
            Button(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun KeyValueBlock(component: GeneratedToolComponent) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            component.items.forEach { raw ->
                val pair = raw.split("=", ":", limit = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pair.getOrNull(0).orEmpty().trim(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pair.getOrNull(1).orEmpty().trim(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TableBlock(component: GeneratedToolComponent) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            component.items.forEachIndexed { index, raw ->
                val cells = raw.split("|").take(4)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cells.forEach { cell ->
                        Text(
                            cell.trim(),
                            modifier = Modifier.weight(1f),
                            fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (index == 0) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BarChartBlock(component: GeneratedToolComponent) {
    val parsed = component.items.mapNotNull { raw ->
        val parts = raw.split("=", ":", limit = 2)
        val value = parts.getOrNull(1)?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        parts.getOrNull(0).orEmpty().trim().takeIf { it.isNotBlank() }?.let { it to value }
    }
    val max = parsed.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            parsed.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Text(compactNumber(value) + component.unitWithSpace(), style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { (value / max).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (parsed.isEmpty()) {
                Text("Sin datos para graficar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FormulaBlock(
    component: GeneratedToolComponent,
    numericState: SnapshotStateMap<String, Double>,
) {
    val result = GeneratedFormulaEngine.evaluate(component.text, numericState)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(component.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                result?.let(::compactNumber)?.plus(component.unitWithSpace()) ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (result == null && component.text.isNotBlank()) {
                Text(
                    "Completá los datos necesarios para calcularlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionButtonBlock(component: GeneratedToolComponent) {
    val context = LocalContext.current
    val executor = remember { ActionExecutor(context.applicationContext) }
    var status by rememberSaveable(component.id + "_status") { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = {
                val result = when (component.action) {
                    "camera" -> executor.openCamera()
                    "video" -> executor.openAppByName("NIKO_TOOL_VIDEO")
                    "audio_recorder" -> executor.openAppByName("NIKO_TOOL_AUDIO")
                    "music" -> executor.openAppByName("NIKO_TOOL_MUSIC")
                    "calculator" -> executor.openAppByName("NIKO_TOOL_CALCULATOR")
                    "notes" -> executor.openAppByName("NIKO_TOOL_NOTES")
                    "flashlight_on" -> executor.setTorch(true)
                    "flashlight_off" -> executor.setTorch(false)
                    "wifi" -> executor.openSystemPanel(SystemPanel.WIFI)
                    "bluetooth" -> executor.openSystemPanel(SystemPanel.BLUETOOTH)
                    "internet" -> executor.openSystemPanel(SystemPanel.INTERNET)
                    "location" -> executor.openSystemPanel(SystemPanel.LOCATION)
                    "maps" -> if (component.payload.isBlank()) executor.openApp(SupportedApp.MAPS) else executor.openMaps(component.payload)
                    "web_search" -> executor.searchWeb(component.payload)
                    "share_text" -> executor.shareText(component.payload)
                    "vibrate" -> executor.vibrate(350L)
                    else -> null
                }
                status = result?.spokenMessage.orEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(component.label)
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun GeneratedToolComponent.unitWithSpace(): String =
    unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

private fun compactNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    val rounded = value.roundToInt().toDouble()
    return if (kotlin.math.abs(value - rounded) < 0.000001) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

private fun formatCountdown(milliseconds: Long): String {
    val total = (milliseconds / 1_000L).coerceAtLeast(0L)
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
