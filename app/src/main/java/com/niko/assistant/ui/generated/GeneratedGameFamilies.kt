package com.niko.assistant.ui.generated

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val GRID_W = 14
private const val GRID_H = 20

private enum class SnakeDirection { UP, DOWN, LEFT, RIGHT }
private data class GridPoint(val x: Int, val y: Int)

@Composable
internal fun GeneratedSnakeGame(component: GeneratedToolComponent) {
    val snake = remember(component.id) {
        mutableStateListOf(
            GridPoint(7, 12),
            GridPoint(7, 13),
            GridPoint(7, 14),
        )
    }
    var food by remember(component.id) { mutableStateOf(randomFood(snake)) }
    var direction by rememberSaveable(component.id + "_dir") { mutableStateOf(SnakeDirection.UP) }
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var gameOver by rememberSaveable(component.id + "_over") { mutableStateOf(false) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }

    fun reset() {
        snake.clear()
        snake.addAll(listOf(GridPoint(7, 12), GridPoint(7, 13), GridPoint(7, 14)))
        food = randomFood(snake)
        direction = SnakeDirection.UP
        score = 0
        gameOver = false
        running = false
    }

    LaunchedEffect(running, gameOver, component.id) {
        while (running && !gameOver) {
            delay((230L - score * 4L).coerceAtLeast(75L))
            val head = snake.first()
            val next = when (direction) {
                SnakeDirection.UP -> GridPoint(head.x, head.y - 1)
                SnakeDirection.DOWN -> GridPoint(head.x, head.y + 1)
                SnakeDirection.LEFT -> GridPoint(head.x - 1, head.y)
                SnakeDirection.RIGHT -> GridPoint(head.x + 1, head.y)
            }
            if (next.x !in 0 until GRID_W || next.y !in 0 until GRID_H || next in snake) {
                running = false
                gameOver = true
                continue
            }
            snake.add(0, next)
            if (next == food) {
                score += 10
                food = randomFood(snake)
            } else {
                snake.removeAt(snake.lastIndex)
            }
        }
    }

    GameCard(component.label.ifBlank { "Snake" }, "Puntos $score") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GRID_W.toFloat() / GRID_H.toFloat())
                .background(Color(0xFF14201C), RoundedCornerShape(18.dp)),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val cw = size.width / GRID_W
                val ch = size.height / GRID_H
                snake.forEachIndexed { index, point ->
                    drawRoundRect(
                        color = if (index == 0) Color(0xFF9DE2C8) else Color(0xFF5BAA90),
                        topLeft = Offset(point.x * cw + 2f, point.y * ch + 2f),
                        size = Size(cw - 4f, ch - 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                    )
                }
                drawCircle(
                    color = Color(0xFFF07878),
                    radius = minOf(cw, ch) * 0.32f,
                    center = Offset((food.x + 0.5f) * cw, (food.y + 0.5f) * ch),
                )
            }
            if (!running) {
                Text(
                    if (gameOver) "FIN" else "SNAKE",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (direction != SnakeDirection.RIGHT) direction = SnakeDirection.LEFT },
                modifier = Modifier.weight(1f),
            ) { Text("←") }
            OutlinedButton(
                onClick = { if (direction != SnakeDirection.DOWN) direction = SnakeDirection.UP },
                modifier = Modifier.weight(1f),
            ) { Text("↑") }
            OutlinedButton(
                onClick = { if (direction != SnakeDirection.UP) direction = SnakeDirection.DOWN },
                modifier = Modifier.weight(1f),
            ) { Text("↓") }
            OutlinedButton(
                onClick = { if (direction != SnakeDirection.LEFT) direction = SnakeDirection.RIGHT },
                modifier = Modifier.weight(1f),
            ) { Text("→") }
        }

        Button(
            onClick = {
                if (gameOver) {
                    reset()
                    running = true
                } else {
                    running = !running
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (gameOver) "REINICIAR" else if (running) "PAUSA" else "JUGAR") }
    }
}

private fun randomFood(snake: List<GridPoint>): GridPoint {
    repeat(100) {
        val candidate = GridPoint(Random.nextInt(GRID_W), Random.nextInt(GRID_H))
        if (candidate !in snake) return candidate
    }
    return GridPoint(0, 0)
}

@Composable
internal fun GeneratedMemoryGame(component: GeneratedToolComponent) {
    val symbols = component.items.take(8).ifEmpty {
        listOf("★", "●", "▲", "◆", "♥", "☀", "☂", "♫")
    }
    var deck by remember(component.id) {
        mutableStateOf((symbols + symbols).shuffled())
    }
    val matched = remember(component.id) { mutableStateListOf<Boolean>().apply { repeat(deck.size) { add(false) } } }
    var first by rememberSaveable(component.id + "_first") { mutableIntStateOf(-1) }
    var second by rememberSaveable(component.id + "_second") { mutableIntStateOf(-1) }
    var moves by rememberSaveable(component.id + "_moves") { mutableIntStateOf(0) }

    LaunchedEffect(second) {
        if (first >= 0 && second >= 0) {
            delay(650L)
            if (deck[first] == deck[second]) {
                matched[first] = true
                matched[second] = true
            }
            first = -1
            second = -1
        }
    }

    GameCard(component.label.ifBlank { "Memoria" }, "Movimientos $moves") {
        deck.chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIndex, symbol ->
                    val index = rowIndex * 4 + colIndex
                    val visible = matched[index] || index == first || index == second
                    Button(
                        onClick = {
                            if (second >= 0 || matched[index] || index == first) return@Button
                            if (first < 0) first = index else {
                                second = index
                                moves++
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (visible) symbol else "?", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (matched.isNotEmpty() && matched.all { it }) {
            Text("¡Completado!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = {
                deck = (symbols + symbols).shuffled()
                matched.clear()
                repeat(deck.size) { matched.add(false) }
                first = -1
                second = -1
                moves = 0
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("NUEVA PARTIDA") }
    }
}

@Composable
internal fun GeneratedPaddleGame(component: GeneratedToolComponent) {
    var playerX by rememberSaveable(component.id + "_px") { mutableStateOf(0.5f) }
    var ballX by rememberSaveable(component.id + "_bx") { mutableStateOf(0.5f) }
    var ballY by rememberSaveable(component.id + "_by") { mutableStateOf(0.5f) }
    var vx by rememberSaveable(component.id + "_vx") { mutableStateOf(0.42f) }
    var vy by rememberSaveable(component.id + "_vy") { mutableStateOf(-0.48f) }
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }
    var lives by rememberSaveable(component.id + "_lives") { mutableIntStateOf(3) }

    LaunchedEffect(running, lives, component.id) {
        var last = System.nanoTime()
        while (running && lives > 0) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0.01f, 0.05f)
            last = now
            ballX += vx * dt
            ballY += vy * dt
            if (ballX <= 0.04f || ballX >= 0.96f) {
                vx = -vx
                ballX = ballX.coerceIn(0.04f, 0.96f)
            }
            if (ballY <= 0.05f) {
                vy = abs(vy)
                score += 5
            }
            if (ballY >= 0.88f && ballY <= 0.96f && abs(ballX - playerX) <= 0.19f && vy > 0f) {
                vy = -abs(vy) * 1.03f
                vx += (ballX - playerX) * 0.22f
                score += 10
            }
            if (ballY > 1.02f) {
                lives--
                ballX = 0.5f
                ballY = 0.5f
                vx = if (Random.nextBoolean()) 0.42f else -0.42f
                vy = -0.48f
                if (lives <= 0) running = false
            }
            delay(16L)
        }
    }

    GameCard(component.label.ifBlank { "Pong" }, "Puntos $score · Vidas $lives") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .background(Color(0xFF14201C), RoundedCornerShape(18.dp)),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color(0xFFF3F6F5), size.width * 0.025f, Offset(ballX * size.width, ballY * size.height))
                drawRoundRect(
                    color = Color(0xFF91D9BF),
                    topLeft = Offset((playerX - 0.16f) * size.width, 0.91f * size.height),
                    size = Size(size.width * 0.32f, size.height * 0.025f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                )
                drawRoundRect(
                    color = Color(0xFF688C80),
                    topLeft = Offset((ballX - 0.14f).coerceIn(0.04f, 0.72f) * size.width, 0.04f * size.height),
                    size = Size(size.width * 0.28f, size.height * 0.018f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                )
            }
            if (lives <= 0) {
                Text(
                    "FIN",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { playerX = (playerX - 0.12f).coerceAtLeast(0.17f) },
                modifier = Modifier.weight(1f),
            ) { Text("←") }
            Button(
                onClick = {
                    if (lives <= 0) {
                        lives = 3
                        score = 0
                        ballX = 0.5f
                        ballY = 0.5f
                    }
                    running = !running
                },
                modifier = Modifier.weight(1.4f),
            ) { Text(if (lives <= 0) "REINICIAR" else if (running) "PAUSA" else "JUGAR") }
            OutlinedButton(
                onClick = { playerX = (playerX + 0.12f).coerceAtMost(0.83f) },
                modifier = Modifier.weight(1f),
            ) { Text("→") }
        }
    }
}

private data class PoolBall(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val pocketed: Boolean = false,
    val cue: Boolean = false,
)

@Composable
internal fun GeneratedBilliardsGame(component: GeneratedToolComponent) {
    val balls = remember(component.id) { mutableStateListOf<PoolBall>() }
    var angle by rememberSaveable(component.id + "_angle") { mutableStateOf(0f) }
    var power by rememberSaveable(component.id + "_power") { mutableStateOf(0.6f) }
    var moving by rememberSaveable(component.id + "_moving") { mutableStateOf(false) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }

    fun reset() {
        balls.clear()
        balls += PoolBall(0.25f, 0.5f, cue = true)
        val starts = listOf(
            0.67f to 0.50f,
            0.72f to 0.46f,
            0.72f to 0.54f,
            0.77f to 0.42f,
            0.77f to 0.50f,
            0.77f to 0.58f,
        )
        starts.forEach { (x, y) -> balls += PoolBall(x, y) }
        score = 0
        moving = false
    }

    if (balls.isEmpty()) reset()

    LaunchedEffect(moving, component.id) {
        while (moving) {
            var anyMoving = false
            val next = balls.toMutableList()
            for (i in balls.indices) {
                val ball = balls[i]
                if (ball.pocketed) continue
                var x = ball.x + ball.vx * 0.018f
                var y = ball.y + ball.vy * 0.018f
                var vxLocal = ball.vx * 0.985f
                var vyLocal = ball.vy * 0.985f

                if (x < 0.055f || x > 0.945f) {
                    vxLocal = -vxLocal
                    x = x.coerceIn(0.055f, 0.945f)
                }
                if (y < 0.07f || y > 0.93f) {
                    vyLocal = -vyLocal
                    y = y.coerceIn(0.07f, 0.93f)
                }

                val pockets = listOf(
                    0.03f to 0.04f, 0.50f to 0.03f, 0.97f to 0.04f,
                    0.03f to 0.96f, 0.50f to 0.97f, 0.97f to 0.96f,
                )
                val pocketed = pockets.any { (px, py) ->
                    val dx = x - px
                    val dy = y - py
                    dx * dx + dy * dy < 0.0032f
                }
                if (pocketed) {
                    if (!ball.cue) score += 100
                    next[i] = ball.copy(x = x, y = y, vx = 0f, vy = 0f, pocketed = true)
                    continue
                }

                if (abs(vxLocal) > 0.008f || abs(vyLocal) > 0.008f) anyMoving = true
                else {
                    vxLocal = 0f
                    vyLocal = 0f
                }
                next[i] = ball.copy(x = x, y = y, vx = vxLocal, vy = vyLocal)
            }

            for (i in next.indices) {
                for (j in i + 1 until next.size) {
                    val a = next[i]
                    val b = next[j]
                    if (a.pocketed || b.pocketed) continue
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist in 0.0001f..0.075f) {
                        val nx = dx / dist
                        val ny = dy / dist
                        val av = a.vx * nx + a.vy * ny
                        val bv = b.vx * nx + b.vy * ny
                        val impulse = bv - av
                        next[i] = a.copy(vx = a.vx + impulse * nx, vy = a.vy + impulse * ny)
                        next[j] = b.copy(vx = b.vx - impulse * nx, vy = b.vy - impulse * ny)
                        anyMoving = true
                    }
                }
            }

            balls.clear()
            balls.addAll(next)
            moving = anyMoving
            delay(16L)
        }

        val cueIndex = balls.indexOfFirst { it.cue }
        if (cueIndex >= 0 && balls[cueIndex].pocketed) {
            balls[cueIndex] = PoolBall(0.25f, 0.5f, cue = true)
        }
    }

    GameCard(component.label.ifBlank { "Billar" }, "Puntos $score") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
                .background(Color(0xFF174F3B), RoundedCornerShape(18.dp)),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val pockets = listOf(
                    Offset(0.03f * size.width, 0.05f * size.height),
                    Offset(0.50f * size.width, 0.04f * size.height),
                    Offset(0.97f * size.width, 0.05f * size.height),
                    Offset(0.03f * size.width, 0.95f * size.height),
                    Offset(0.50f * size.width, 0.96f * size.height),
                    Offset(0.97f * size.width, 0.95f * size.height),
                )
                pockets.forEach { drawCircle(Color(0xFF07110D), size.minDimension * 0.055f, it) }
                balls.forEachIndexed { index, ball ->
                    if (ball.pocketed) return@forEachIndexed
                    val color = if (ball.cue) Color.White else listOf(
                        Color(0xFFF0C44C),
                        Color(0xFF4C83D9),
                        Color(0xFFD95A5A),
                        Color(0xFF774CCB),
                        Color(0xFFF08A45),
                        Color(0xFF4FA46C),
                    )[(index - 1).coerceAtLeast(0) % 6]
                    drawCircle(
                        color,
                        size.minDimension * 0.045f,
                        Offset(ball.x * size.width, ball.y * size.height),
                    )
                }

                if (!moving) {
                    balls.firstOrNull { it.cue && !it.pocketed }?.let { cue ->
                        val radians = angle * PI.toFloat()
                        val start = Offset(cue.x * size.width, cue.y * size.height)
                        val end = Offset(
                            start.x + cos(radians) * size.width * 0.18f,
                            start.y + sin(radians) * size.height * 0.32f,
                        )
                        drawLine(Color.White.copy(alpha = 0.72f), start, end, strokeWidth = 4f)
                    }
                }
            }
        }

        Text("Dirección", style = MaterialTheme.typography.bodySmall)
        Slider(value = angle, onValueChange = { angle = it }, valueRange = -1f..1f, enabled = !moving)
        Text("Potencia", style = MaterialTheme.typography.bodySmall)
        Slider(value = power, onValueChange = { power = it }, valueRange = 0.15f..1f, enabled = !moving)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { reset() }, modifier = Modifier.weight(1f)) { Text("REINICIAR") }
            Button(
                onClick = {
                    if (moving) return@Button
                    val index = balls.indexOfFirst { it.cue && !it.pocketed }
                    if (index >= 0) {
                        val radians = angle * PI.toFloat()
                        balls[index] = balls[index].copy(
                            vx = cos(radians) * power * 1.25f,
                            vy = sin(radians) * power * 1.25f,
                        )
                        moving = true
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !moving,
            ) { Text("TIRAR") }
        }
    }
}

@Composable
internal fun GeneratedPlatformGame(component: GeneratedToolComponent) {
    var jumping by rememberSaveable(component.id + "_jumping") { mutableStateOf(false) }
    var playerY by rememberSaveable(component.id + "_y") { mutableStateOf(0.78f) }
    var velocity by rememberSaveable(component.id + "_vy") { mutableStateOf(0f) }
    var obstacleX by rememberSaveable(component.id + "_ox") { mutableStateOf(1.05f) }
    var running by rememberSaveable(component.id + "_run") { mutableStateOf(false) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }
    var gameOver by rememberSaveable(component.id + "_over") { mutableStateOf(false) }

    LaunchedEffect(running, gameOver, component.id) {
        while (running && !gameOver) {
            obstacleX -= 0.018f
            if (obstacleX < -0.08f) {
                obstacleX = 1.05f
                score += 10
            }
            if (jumping) {
                velocity += 0.025f
                playerY += velocity
                if (playerY >= 0.78f) {
                    playerY = 0.78f
                    velocity = 0f
                    jumping = false
                }
            }
            if (obstacleX in 0.16f..0.34f && playerY > 0.62f) {
                gameOver = true
                running = false
            }
            delay(32L)
        }
    }

    GameCard(component.label.ifBlank { "Plataformas" }, "Puntos $score") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.65f)
                .background(Color(0xFF18251F), RoundedCornerShape(18.dp)),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Color(0xFF395247), topLeft = Offset(0f, size.height * 0.87f), size = Size(size.width, size.height * 0.13f))
                drawRoundRect(
                    color = Color(0xFF9DD8C4),
                    topLeft = Offset(size.width * 0.22f, size.height * playerY),
                    size = Size(size.width * 0.09f, size.height * 0.10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                )
                drawRoundRect(
                    color = Color(0xFFD96A6A),
                    topLeft = Offset(size.width * obstacleX, size.height * 0.77f),
                    size = Size(size.width * 0.07f, size.height * 0.10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                )
            }
            if (!running) {
                Text(
                    if (gameOver) "FIN" else "PLATAFORMAS",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (gameOver) {
                        gameOver = false
                        score = 0
                        obstacleX = 1.05f
                        playerY = 0.78f
                    }
                    running = true
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (gameOver) "REINICIAR" else if (running) "CORRIENDO" else "JUGAR") }
            OutlinedButton(
                onClick = {
                    if (running && !jumping) {
                        jumping = true
                        velocity = -0.13f
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = running,
            ) { Text("SALTAR") }
        }
    }
}

@Composable
internal fun GeneratedTargetGame(component: GeneratedToolComponent) {
    var targetX by rememberSaveable(component.id + "_tx") { mutableStateOf(0.5f) }
    var targetY by rememberSaveable(component.id + "_ty") { mutableStateOf(0.5f) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }
    var misses by rememberSaveable(component.id + "_miss") { mutableIntStateOf(0) }

    GameCard(component.label.ifBlank { "Objetivos" }, "Aciertos $score · Fallos $misses") {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .background(Color(0xFF18251F), RoundedCornerShape(18.dp))
                .pointerInput(component.id, targetX, targetY) {
                    detectTapGestures { tap ->
                        val nx = tap.x / size.width
                        val ny = tap.y / size.height
                        val dx = nx - targetX
                        val dy = ny - targetY
                        if (dx * dx + dy * dy <= 0.012f) {
                            score += 1
                            targetX = Random.nextFloat().coerceIn(0.12f, 0.88f)
                            targetY = Random.nextFloat().coerceIn(0.12f, 0.88f)
                        } else {
                            misses += 1
                        }
                    }
                },
        ) {
            drawCircle(
                Color(0xFFF2D36F),
                size.minDimension * 0.095f,
                Offset(targetX * size.width, targetY * size.height),
            )
            drawCircle(
                Color(0xFFD95A5A),
                size.minDimension * 0.055f,
                Offset(targetX * size.width, targetY * size.height),
            )
        }
        OutlinedButton(
            onClick = {
                score = 0
                misses = 0
                targetX = 0.5f
                targetY = 0.5f
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("REINICIAR") }
    }
}

@Composable
private fun GameCard(
    title: String,
    status: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
