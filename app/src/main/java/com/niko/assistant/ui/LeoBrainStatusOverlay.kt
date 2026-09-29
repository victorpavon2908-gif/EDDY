package com.niko.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niko.assistant.background.NikoRuntimeState.BrainState
import java.util.Locale

/** Compact, always-visible status for Leo's separately downloaded frozen brain. */
@Composable
internal fun LeoBrainStatusOverlay(
    state: BrainState,
    progress: Int,
    status: String,
    downloadedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val accent = when (state) {
        BrainState.READY -> Color(0xFF54D7A0)
        BrainState.ERROR -> Color(0xFFFF7272)
        BrainState.VERIFYING, BrainState.INSTALLING -> Color(0xFFB497FF)
        else -> Color(0xFF79A7FF)
    }
    val title = when (state) {
        BrainState.WAITING -> "CEREBRO LOCAL · PENDIENTE"
        BrainState.CHECKING -> "CEREBRO LOCAL · COMPROBANDO"
        BrainState.DOWNLOADING -> "CEREBRO LOCAL · DESCARGANDO $progress%"
        BrainState.VERIFYING -> "CEREBRO LOCAL · VERIFICANDO"
        BrainState.INSTALLING -> "CEREBRO LOCAL · INSTALANDO $progress%"
        BrainState.READY -> "CEREBRO LOCAL · LISTO"
        BrainState.ERROR -> "CEREBRO LOCAL · ERROR"
    }
    val detail = when (state) {
        BrainState.DOWNLOADING -> transferText(downloadedBytes, totalBytes)
        BrainState.INSTALLING -> if (totalBytes > 0L) "${decimalMb(downloadedBytes)} / ${decimalMb(totalBytes)} MB instalados" else status
        BrainState.READY -> if (totalBytes > 0L) "${decimalMb(totalBytes)} MB instalados y disponibles sin Internet" else "Instalado y disponible sin Internet"
        BrainState.ERROR -> status.ifBlank { "No se pudo instalar. Leo reintentará al volver a iniciar." }
        else -> status.ifBlank { "Preparando el conocimiento base de Leo" }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF09101A).copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Canvas(Modifier.size(7.dp)) { drawCircle(accent) }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.75.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state == BrainState.DOWNLOADING || state == BrainState.INSTALLING) {
                    Text(
                        text = "$progress%",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (state == BrainState.DOWNLOADING || state == BrainState.INSTALLING) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(100.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress.coerceIn(0, 100) / 100f))
                            .height(3.dp)
                            .background(accent, RoundedCornerShape(100.dp)),
                    )
                }
            }
        }
    }
}

private fun transferText(done: Long, total: Long): String = when {
    total > 0L -> "${decimalMb(done)} / ${decimalMb(total)} MB descargados"
    done > 0L -> "${decimalMb(done)} MB descargados"
    else -> "Conectando con GitHub para descargar el cerebro"
}

private fun decimalMb(bytes: Long): String = String.format(Locale.US, "%.1f", bytes.coerceAtLeast(0L) / 1_000_000.0)
