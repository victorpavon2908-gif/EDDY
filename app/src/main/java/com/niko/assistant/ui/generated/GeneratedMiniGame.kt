package com.niko.assistant.ui.generated

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private data class ArcadeObstacle(
    val lane: Int,
    val y: Float,
    val serial: Long,
)

@Composable
internal fun GeneratedMiniGame(component: GeneratedToolComponent) {
    val kind = component.text.lowercase().ifBlank { component.label.lowercase() }
    when (resolveGeneratedGameKind(kind)) {
        GeneratedGameKind.FALLING_BLOCKS -> {
            GeneratedFallingBlocksGame(component)
            return
        }
        GeneratedGameKind.SNAKE -> {
            GeneratedSnakeGame(component)
            return
        }
        GeneratedGameKind.PADDLE -> {
            GeneratedPaddleGame(component)
            return
        }
        GeneratedGameKind.MEMORY -> {
            GeneratedMemoryGame(component)
            return
        }
        GeneratedGameKind.BILLIARDS -> {
            GeneratedBilliardsGame(component)
            return
        }
        GeneratedGameKind.PLATFORMER -> {
            GeneratedPlatformGame(component)
            return
        }
        GeneratedGameKind.TARGET_TAP -> {
            GeneratedTargetGame(component)
            return
        }
        GeneratedGameKind.ARCADE_LANES -> Unit
    }

    val vehicle = when {
        "moto" in kind || "motorcycle" in kind -> "MOTO"
        "carro" in kind || "auto" in kind || "car" in kind -> "AUTO"
        "nave" in kind || "space" in kind -> "NAVE"
        else -> "JUGADOR"
    }

    var playerLane by rememberSaveable(component.id + "_lane") { mutableIntStateOf(1) }
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var gameOver by rememberSaveable(component.id + "_over") { mutableStateOf(false) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }
    var best by rememberSaveable(component.id + "_best") { mutableIntStateOf(0) }
    var lastSpawn by rememberSaveable(component.id + "_spawn") { mutableLongStateOf(0L) }
    var serial by rememberSaveable(component.id + "_serial") { mutableLongStateOf(0L) }
    val obstacles = remember(component.id) { mutableStateListOf<ArcadeObstacle>() }

    fun reset() {
        running = false
        gameOver = false
        score = 0
        playerLane = 1
        obstacles.clear()
        lastSpawn = 0L
    }

    LaunchedEffect(running, gameOver, component.id) {
        if (!running || gameOver) return@LaunchedEffect
        var lastTick = SystemClock.elapsedRealtime()
        while (running && !gameOver) {
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastTick).coerceIn(16L, 80L) / 1000f)
            lastTick = now

            val speed = (0.28f + (score / 2500f)).coerceAtMost(0.72f)
            for (i in obstacles.indices.reversed()) {
                val moved = obstacles[i].copy(y = obstacles[i].y + speed * dt)
                if (moved.y > 1.08f) {
                    obstacles.removeAt(i)
                    score += 10
                    best = max(best, score)
                } else {
                    obstacles[i] = moved
                }
            }

            val collision = obstacles.any { it.lane == playerLane && it.y in 0.78f..0.97f }
            if (collision) {
                running = false
                gameOver = true
                best = max(best, score)
            } else {
                val spawnEvery = (1050L - score * 2L).coerceAtLeast(430L)
                if (lastSpawn == 0L || now - lastSpawn >= spawnEvery) {
                    lastSpawn = now
                    serial += 1
                    obstacles += ArcadeObstacle(
                        lane = Random.nextInt(0, 3),
                        y = -0.12f,
                        serial = serial,
                    )
                }
                score += 1
                best = max(best, score)
            }
            delay(40L)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(component.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (gameOver) "Choque · reiniciá para volver a jugar" else if (running) "En carrera" else "Listo para jugar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Puntos $score", fontWeight = FontWeight.Bold)
                    Text("Récord $best", style = MaterialTheme.typography.bodySmall)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .background(Color(0xFF17201D), RoundedCornerShape(20.dp)),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val laneWidth = size.width / 3f
                    drawRect(Color(0xFF1C2723))
                    for (lane in 1..2) {
                        val x = laneWidth * lane
                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.22f),
                                start = Offset(x, y),
                                end = Offset(x, (y + size.height * 0.06f).coerceAtMost(size.height)),
                                strokeWidth = 3f,
                            )
                            y += size.height * 0.12f
                        }
                    }

                    val playerCenterX = laneWidth * (playerLane + 0.5f)
                    val playerCenterY = size.height * 0.87f
                    if (vehicle == "MOTO") {
                        drawCircle(Color(0xFF9DD8C4), size.width * 0.035f, Offset(playerCenterX, playerCenterY - size.height * 0.045f))
                        drawCircle(Color(0xFF9DD8C4), size.width * 0.035f, Offset(playerCenterX, playerCenterY + size.height * 0.045f))
                        drawLine(
                            Color(0xFFF4F7F5),
                            Offset(playerCenterX, playerCenterY - size.height * 0.03f),
                            Offset(playerCenterX, playerCenterY + size.height * 0.03f),
                            strokeWidth = size.width * 0.045f,
                        )
                    } else if (vehicle == "NAVE") {
                        val w = laneWidth * 0.42f
                        val h = size.height * 0.075f
                        drawOval(
                            color = Color(0xFF9DD8C4),
                            topLeft = Offset(playerCenterX - w / 2f, playerCenterY - h / 2f),
                            size = Size(w, h),
                        )
                    } else {
                        val w = laneWidth * 0.38f
                        val h = size.height * 0.09f
                        drawRoundRect(
                            color = Color(0xFF9DD8C4),
                            topLeft = Offset(playerCenterX - w / 2f, playerCenterY - h / 2f),
                            size = Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                        )
                    }

                    obstacles.forEach { obstacle ->
                        val cx = laneWidth * (obstacle.lane + 0.5f)
                        val cy = size.height * obstacle.y
                        val w = laneWidth * 0.46f
                        val h = size.height * 0.085f
                        drawRoundRect(
                            color = Color(0xFFD96969),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.18f),
                            topLeft = Offset(cx - w / 2f, cy - h / 2f),
                            size = Size(w, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                            style = Stroke(width = 2f),
                        )
                    }
                }

                if (!running) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (gameOver) "FIN DE LA PARTIDA" else vehicle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (gameOver) "Puntuación: $score" else "Esquivá los obstáculos",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { playerLane = (playerLane - 1).coerceAtLeast(0) },
                    modifier = Modifier.weight(1f),
                    enabled = !gameOver,
                ) { Text("←") }
                Button(
                    onClick = {
                        if (gameOver) {
                            reset()
                            running = true
                        } else {
                            running = !running
                        }
                    },
                    modifier = Modifier.weight(1.25f),
                ) { Text(if (gameOver) "REINICIAR" else if (running) "PAUSA" else "JUGAR") }
                OutlinedButton(
                    onClick = { playerLane = (playerLane + 1).coerceAtMost(2) },
                    modifier = Modifier.weight(1f),
                    enabled = !gameOver,
                ) { Text("→") }
            }
        }
    }
}
