package com.niko.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun MiniNikoMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = max(2f, min(w, h) * 0.075f)
        val thin = stroke * 0.55f
        val left = w * 0.22f
        val right = w * 0.78f
        val top = h * 0.12f
        val middle = h * 0.55f
        val bottom = h * 0.88f

        drawLine(NikoBlack, Offset(left, top + stroke * 0.3f), Offset(left, bottom), stroke, StrokeCap.Butt)
        drawArc(
            NikoBlack,
            180f,
            180f,
            false,
            Offset(left, top),
            Size(right - left, h * 0.26f),
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        drawLine(NikoBlack, Offset(left, middle), Offset(right, middle), stroke, StrokeCap.Butt)
        drawLine(NikoBlack, Offset(left, bottom), Offset(right, bottom), stroke, StrokeCap.Butt)

        val eyeStyle = Stroke(width = thin, cap = StrokeCap.Round)
        drawArc(NikoBlack, 180f, 180f, false, Offset(w * 0.31f, h * 0.32f), Size(w * 0.15f, h * 0.05f), style = eyeStyle)
        drawArc(NikoBlack, 180f, 180f, false, Offset(w * 0.56f, h * 0.32f), Size(w * 0.15f, h * 0.05f), style = eyeStyle)
        drawArc(NikoBlack, 0f, 180f, false, Offset(w * 0.39f, h * 0.44f), Size(w * 0.24f, h * 0.12f), style = eyeStyle)
        drawLine(NikoBlack, Offset(w * 0.38f, h * 0.62f), Offset(w * 0.50f, h * 0.74f), thin, StrokeCap.Square)
        drawLine(NikoBlack, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.50f, h * 0.74f), thin, StrokeCap.Square)
        drawLine(NikoBlack, Offset(w * 0.50f, h * 0.74f), Offset(w * 0.50f, bottom), thin, StrokeCap.Square)
    }
}

@Composable
internal fun MiniGridIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = min(size.width, size.height) * 0.075f
        val xs = listOf(size.width * 0.34f, size.width * 0.66f)
        val ys = listOf(size.height * 0.34f, size.height * 0.66f)
        xs.forEach { x ->
            ys.forEach { y ->
                drawCircle(
                    color = NikoBlack,
                    radius = r,
                    center = Offset(x, y),
                    style = Stroke(width = max(1.5f, r * 0.55f)),
                )
            }
        }
    }
}

@Composable
internal fun SliderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = max(2f, min(size.width, size.height) * 0.065f)
        val y1 = size.height * 0.38f
        val y2 = size.height * 0.66f
        drawLine(NikoBlack, Offset(size.width * 0.15f, y1), Offset(size.width * 0.85f, y1), stroke, StrokeCap.Round)
        drawLine(NikoBlack, Offset(size.width * 0.15f, y2), Offset(size.width * 0.85f, y2), stroke, StrokeCap.Round)
        drawCircle(Color.White, radius = size.width * 0.09f, center = Offset(size.width * 0.42f, y1))
        drawCircle(NikoBlack, radius = size.width * 0.09f, center = Offset(size.width * 0.42f, y1), style = Stroke(width = stroke))
        drawCircle(Color.White, radius = size.width * 0.09f, center = Offset(size.width * 0.62f, y2))
        drawCircle(NikoBlack, radius = size.width * 0.09f, center = Offset(size.width * 0.62f, y2), style = Stroke(width = stroke))
    }
}
