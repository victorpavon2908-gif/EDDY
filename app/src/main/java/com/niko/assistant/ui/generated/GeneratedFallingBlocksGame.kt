package com.niko.assistant.ui.generated

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private const val BLOCK_COLS = 10
private const val BLOCK_ROWS = 18

private val blockShapes = listOf(
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
    listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0),
    listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
    listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
    listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
    listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
)

@Composable
internal fun GeneratedFallingBlocksGame(component: GeneratedToolComponent) {
    var board by remember(component.id) { mutableStateOf(emptyBoard()) }
    var shapeIndex by rememberSaveable(component.id + "_shape") { mutableIntStateOf(Random.nextInt(blockShapes.size)) }
    var rotation by rememberSaveable(component.id + "_rotation") { mutableIntStateOf(0) }
    var pieceX by rememberSaveable(component.id + "_x") { mutableIntStateOf(3) }
    var pieceY by rememberSaveable(component.id + "_y") { mutableIntStateOf(0) }
    var score by rememberSaveable(component.id + "_score") { mutableIntStateOf(0) }
    var lines by rememberSaveable(component.id + "_lines") { mutableIntStateOf(0) }
    var best by rememberSaveable(component.id + "_best") { mutableIntStateOf(0) }
    var running by rememberSaveable(component.id + "_running") { mutableStateOf(false) }
    var gameOver by rememberSaveable(component.id + "_over") { mutableStateOf(false) }

    fun cells(
        shape: Int = shapeIndex,
        rot: Int = rotation,
        x: Int = pieceX,
        y: Int = pieceY,
    ): List<Pair<Int, Int>> = rotatedShape(blockShapes[shape], rot).map { (dx, dy) -> (x + dx) to (y + dy) }

    fun fits(
        shape: Int = shapeIndex,
        rot: Int = rotation,
        x: Int = pieceX,
        y: Int = pieceY,
    ): Boolean = canPlace(board, cells(shape, rot, x, y))

    fun spawnNext(nextBoard: List<List<Boolean>>) {
        board = nextBoard
        shapeIndex = Random.nextInt(blockShapes.size)
        rotation = 0
        pieceX = 3
        pieceY = 0
        if (!canPlace(board, cells())) {
            running = false
            gameOver = true
            best = max(best, score)
        }
    }

    fun lockPiece() {
        val merged = mergePiece(board, cells())
        val cleared = clearCompletedRows(merged)
        if (cleared.second > 0) {
            lines += cleared.second
            score += when (cleared.second) {
                1 -> 100
                2 -> 300
                3 -> 500
                else -> 800
            }
        } else {
            score += 10
        }
        best = max(best, score)
        spawnNext(cleared.first)
    }

    fun dropOne() {
        if (!running || gameOver) return
        if (fits(y = pieceY + 1)) {
            pieceY += 1
        } else {
            lockPiece()
        }
    }

    fun resetGame() {
        board = emptyBoard()
        shapeIndex = Random.nextInt(blockShapes.size)
        rotation = 0
        pieceX = 3
        pieceY = 0
        score = 0
        lines = 0
        gameOver = false
        running = false
    }

    LaunchedEffect(running, gameOver, score, component.id) {
        while (running && !gameOver) {
            val interval = (700L - (score / 10L)).coerceAtLeast(150L)
            delay(interval)
            dropOne()
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
                    Text(
                        component.label.ifBlank { "Bloques" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            gameOver -> "Partida terminada"
                            running -> "Bloques en movimiento"
                            else -> "Listo para jugar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Puntos $score", fontWeight = FontWeight.Bold)
                    Text("Líneas $lines · Récord $best", style = MaterialTheme.typography.bodySmall)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BLOCK_COLS.toFloat() / BLOCK_ROWS.toFloat())
                    .background(Color(0xFF17201D), RoundedCornerShape(20.dp)),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val cellW = size.width / BLOCK_COLS
                    val cellH = size.height / BLOCK_ROWS
                    val active = cells().toSet()

                    for (row in 0 until BLOCK_ROWS) {
                        for (col in 0 until BLOCK_COLS) {
                            val filled = board[row][col] || (col to row) in active
                            if (filled) {
                                val pad = 2f
                                drawRoundRect(
                                    color = if ((col to row) in active) Color(0xFF9DD8C4) else Color(0xFF6E8E84),
                                    topLeft = Offset(col * cellW + pad, row * cellH + pad),
                                    size = Size(cellW - pad * 2, cellH - pad * 2),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
                                )
                            } else {
                                drawRect(
                                    color = Color.White.copy(alpha = 0.035f),
                                    topLeft = Offset(col * cellW + 1f, row * cellH + 1f),
                                    size = Size(cellW - 2f, cellH - 2f),
                                )
                            }
                        }
                    }
                }

                if (!running) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (gameOver) "FIN" else "BLOQUES",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (gameOver) "Puntos: $score" else "Completá líneas sin llegar arriba",
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val next = pieceX - 1
                        if (fits(x = next)) pieceX = next
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !gameOver,
                ) { Text("←") }

                OutlinedButton(
                    onClick = {
                        val next = (rotation + 1) % 4
                        if (fits(rot = next)) rotation = next
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !gameOver,
                ) { Text("↻") }

                OutlinedButton(
                    onClick = {
                        val next = pieceX + 1
                        if (fits(x = next)) pieceX = next
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !gameOver,
                ) { Text("→") }

                Button(
                    onClick = {
                        if (gameOver) {
                            resetGame()
                            running = true
                        } else if (running) {
                            var y = pieceY
                            while (fits(y = y + 1)) y++
                            pieceY = y
                            lockPiece()
                        } else {
                            running = true
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                ) {
                    Text(
                        when {
                            gameOver -> "REINICIAR"
                            running -> "BAJAR"
                            else -> "JUGAR"
                        },
                    )
                }
            }

            if (running) {
                OutlinedButton(
                    onClick = { running = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("PAUSA") }
            }
        }
    }
}

internal fun rotatedShape(shape: List<Pair<Int, Int>>, rotation: Int): List<Pair<Int, Int>> {
    var result = shape
    repeat(((rotation % 4) + 4) % 4) {
        val rotated = result.map { (x, y) -> y to -x }
        val minX = rotated.minOf { it.first }
        val minY = rotated.minOf { it.second }
        result = rotated.map { (x, y) -> (x - minX) to (y - minY) }
    }
    return result
}

internal fun canPlace(
    board: List<List<Boolean>>,
    cells: List<Pair<Int, Int>>,
): Boolean = cells.all { (x, y) ->
    x in 0 until BLOCK_COLS &&
        y in 0 until BLOCK_ROWS &&
        !board[y][x]
}

internal fun mergePiece(
    board: List<List<Boolean>>,
    cells: List<Pair<Int, Int>>,
): List<List<Boolean>> {
    val copy = board.map { it.toMutableList() }
    cells.forEach { (x, y) ->
        if (x in 0 until BLOCK_COLS && y in 0 until BLOCK_ROWS) {
            copy[y][x] = true
        }
    }
    return copy.map { it.toList() }
}

internal fun clearCompletedRows(board: List<List<Boolean>>): Pair<List<List<Boolean>>, Int> {
    val remaining = board.filterNot { row -> row.all { it } }
    val removed = BLOCK_ROWS - remaining.size
    val emptyRows = List(removed) { List(BLOCK_COLS) { false } }
    return (emptyRows + remaining) to removed
}

private fun emptyBoard(): List<List<Boolean>> =
    List(BLOCK_ROWS) { List(BLOCK_COLS) { false } }
