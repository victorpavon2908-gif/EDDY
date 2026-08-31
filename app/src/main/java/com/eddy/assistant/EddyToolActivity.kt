package com.eddy.assistant

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.MathContext

class EddyToolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tool = intent.getStringExtra(EXTRA_TOOL).orEmpty().lowercase()
        setContent {
            EddyTheme {
                when (tool) {
                    TOOL_STOPWATCH -> StopwatchTool(onClose = ::finish)
                    else -> CalculatorTool(onClose = ::finish)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TOOL = "eddy_tool"
        const val TOOL_CALCULATOR = "calculator"
        const val TOOL_STOPWATCH = "stopwatch"
    }
}

@Composable
private fun ToolShell(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EDDY · $title", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("Volver") }
        }
        content()
    }
}

@Composable
private fun CalculatorTool(onClose: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    ToolShell("Calculadora", onClose) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.End) {
                Text(expression.ifBlank { " " }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(result, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            }
        }
        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "="),
        )
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Button(
                        onClick = {
                            when (key) {
                                "C" -> { expression = ""; result = "0" }
                                "⌫" -> expression = expression.dropLast(1)
                                "=" -> result = SafeMath.evaluate(expression) ?: "Error"
                                else -> expression += key
                            }
                        },
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text(key, style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    }
}

@Composable
private fun StopwatchTool(onClose: () -> Unit) {
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var accumulated by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(30L)
        }
    }
    val elapsed = accumulated + if (running) (now - startedAt) else 0L
    ToolShell("Cronómetro", onClose) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(formatStopwatch(elapsed), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { running = false; accumulated = 0L; startedAt = 0L; now = SystemClock.elapsedRealtime() },
                modifier = Modifier.weight(1f).height(58.dp),
            ) { Text("Reiniciar") }
            Button(
                onClick = {
                    if (running) {
                        accumulated += SystemClock.elapsedRealtime() - startedAt
                        running = false
                    } else {
                        startedAt = SystemClock.elapsedRealtime()
                        now = startedAt
                        running = true
                    }
                },
                modifier = Modifier.weight(1f).height(58.dp),
                colors = ButtonDefaults.buttonColors(),
            ) { Text(if (running) "Pausar" else "Iniciar") }
        }
    }
}

private fun formatStopwatch(milliseconds: Long): String {
    val totalHundredths = milliseconds / 10
    val hundredths = totalHundredths % 100
    val totalSeconds = totalHundredths / 100
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
}

private object SafeMath {
    private val mc = MathContext.DECIMAL64

    fun evaluate(raw: String): String? = runCatching {
        val parser = Parser(raw.replace('×', '*').replace('÷', '/').replace('−', '-'))
        val value = parser.parseExpression()
        parser.skipSpaces()
        check(parser.end())
        value.stripTrailingZeros().toPlainString()
    }.getOrNull()

    private class Parser(private val source: String) {
        private var index = 0
        fun end() = index >= source.length
        fun skipSpaces() { while (!end() && source[index].isWhitespace()) index++ }

        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    take('+') -> value.add(parseTerm(), mc)
                    take('-') -> value.subtract(parseTerm(), mc)
                    else -> return value
                }
            }
        }

        private fun parseTerm(): BigDecimal {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                value = when {
                    take('*') -> value.multiply(parseFactor(), mc)
                    take('/') -> value.divide(parseFactor(), mc)
                    else -> return value
                }
            }
        }

        private fun parseFactor(): BigDecimal {
            skipSpaces()
            if (take('+')) return parseFactor()
            if (take('-')) return parseFactor().negate(mc)
            if (take('(')) {
                val value = parseExpression()
                check(take(')'))
                return value
            }
            val start = index
            while (!end() && (source[index].isDigit() || source[index] == '.')) index++
            check(index > start)
            return source.substring(start, index).toBigDecimal(mc)
        }

        private fun take(char: Char): Boolean {
            skipSpaces()
            if (!end() && source[index] == char) { index++; return true }
            return false
        }
    }
}
