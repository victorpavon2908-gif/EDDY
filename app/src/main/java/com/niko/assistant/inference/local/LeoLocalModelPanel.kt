package com.niko.assistant.inference.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PanelCardBg = Color(0xFF101917)
private val PanelSubCardBg = Color(0xFF16221F)
private val PanelBorder = Color(0xFF243832)
private val PanelAccent = Color(0xFF36E5B2)
private val PanelCyan = Color(0xFF55E8FF)
private val PanelTextPrimary = Color(0xFFF1F7F5)
private val PanelTextSecondary = Color(0xFF9EB2AB)
private val PanelDanger = Color(0xFFFF6B81)

@Composable
fun LeoLocalModelPanel() {
    val app = LocalContext.current.applicationContext
    var selected by remember { mutableStateOf(LeoLocalModelStore(app).spec) }
    val store = remember(selected) { LeoLocalModelStore(app, selected) }
    val scope = rememberCoroutineScope()
    val workManager = remember { WorkManager.getInstance(app) }
    val work by remember { workManager.getWorkInfosForUniqueWorkFlow(LeoModelDownloadWorker.NAME) }.collectAsState(emptyList())
    val activeWork = work.firstOrNull { !it.state.isFinished }
    val downloading = activeWork != null
    val progress = activeWork?.progress?.getString(LeoModelDownloadWorker.STATUS)
        ?: activeWork?.let { "Descarga en cola o ejecutándose en segundo plano…" }
    val completedStatus = work.firstOrNull()?.let {
        if (it.state == WorkInfo.State.CANCELLED) "Descarga cancelada. El avance parcial se conservó para reanudar cuando quieras."
        else it.outputData.getString(LeoModelDownloadWorker.STATUS)
    }
    var job by remember { mutableStateOf<Job?>(null) }
    var isInstalled by remember(selected) { mutableStateOf(store.installed) }
    var isEnabled by remember(selected) { mutableStateOf(store.enabled) }
    val isCompatible = remember(selected) { store.compatible() }
    var status by remember(selected) {
        mutableStateOf(
            when {
                store.enabled -> "Modelo activo: ${store.spec.id} (instalado y verificado)"
                store.installed -> "Archivo descargado (${store.spec.id}), listo para activar."
                else -> "Usando el núcleo rápido soberano de LEO (modelo GGUF pesado desactivado)."
            },
        )
    }
    var deleteConfirm by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(completedStatus, downloading) {
        isInstalled = store.installed
        isEnabled = store.enabled
        if (completedStatus != null && !downloading) {
            status = completedStatus
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PanelCardBg,
        border = BorderStroke(1.dp, PanelBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MODELO GENERATIVO GGUF (OPCIONAL)",
                        color = PanelCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Razonamiento profundo y código sin Internet",
                        color = PanelTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (isEnabled) PanelAccent.copy(alpha = 0.16f) else PanelSubCardBg,
                    border = BorderStroke(1.dp, if (isEnabled) PanelAccent.copy(alpha = 0.45f) else PanelBorder),
                ) {
                    Text(
                        text = when {
                            downloading -> "DESCARGANDO"
                            isEnabled -> "ACTIVO"
                            isInstalled -> "INSTALADO"
                            else -> "OPCIONAL"
                        },
                        color = if (isEnabled || downloading) PanelAccent else PanelTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Text(
                text = "Modelos oficiales Qwen3 Apache 2.0 en tu teléfono. No requieren clave ni conexión una vez descargados. Todo el contexto permanece privado en tu dispositivo.",
                color = PanelTextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LeoLocalModelCatalog.all.forEach { option ->
                    val isSelected = selected == option
                    val optionStore = remember(option) { LeoLocalModelStore(app, option) }
                    val optionCompatible = remember(option) { optionStore.compatible() }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = job == null && !downloading) { selected = option },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) PanelAccent.copy(alpha = 0.11f) else PanelSubCardBg,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PanelAccent else PanelBorder,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { if (job == null && !downloading) selected = option },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = PanelAccent,
                                    unselectedColor = PanelTextSecondary,
                                ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (option == LeoLocalModelCatalog.compact) "Qwen3 1.7B · Compacto y Rápido" else "Qwen3 4B · Máxima Capacidad",
                                    color = PanelTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = option.label,
                                    color = PanelTextSecondary,
                                    fontSize = 11.5.sp,
                                )
                                Text(
                                    text = if (optionCompatible) "✓ Compatible con la RAM de este teléfono" else "⚠ Requiere más RAM libre en este dispositivo",
                                    color = if (optionCompatible) PanelAccent else Color(0xFFFFB45C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = PanelSubCardBg,
                border = BorderStroke(1.dp, PanelBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (downloading || isEnabled) PanelAccent else PanelCyan, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = progress ?: status,
                        color = PanelTextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }

            Button(
                onClick = { confirm = true },
                enabled = job == null && !downloading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelAccent,
                    contentColor = Color(0xFF061510),
                    disabledContainerColor = PanelBorder,
                    disabledContentColor = PanelTextSecondary,
                ),
            ) {
                Text(
                    text = when {
                        downloading -> "DESCARGA EN CURSO…"
                        isInstalled -> "VALIDAR Y ACTIVAR ${selected.id.uppercase()}"
                        else -> "DESCARGAR Y ACTIVAR MODELO LLM"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (downloading) {
                            workManager.cancelUniqueWork(LeoModelDownloadWorker.NAME)
                            status = "Cancelando descarga en curso… El avance parcial se conserva."
                        } else {
                            workManager.cancelUniqueWork(LeoModelDownloadWorker.NAME)
                            status = "No había ninguna descarga activa. Estado limpio."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (downloading) PanelDanger else PanelBorder),
                ) {
                    Text(
                        text = if (downloading) "DETENER DESCARGA" else "CANCELAR COLA",
                        color = if (downloading) PanelDanger else PanelTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { store.disable() }
                            isEnabled = false
                            status = "Modelo pesado desactivado. LEO usa ahora su núcleo biónico rápido; el archivo se conserva."
                        }
                    },
                    enabled = job == null && !downloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PanelBorder),
                ) {
                    Text(
                        text = "USAR NÚCLEO RÁPIDO",
                        color = PanelTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            OutlinedButton(
                onClick = { deleteConfirm = true },
                enabled = job == null && !downloading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PanelDanger.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = "ELIMINAR ARCHIVO DEL MODELO (LIBERAR ESPACIO)",
                    color = PanelDanger,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (!isCompatible) {
                Text(
                    text = "Nota: Tu teléfono tiene menos RAM libre de la recomendada para ${selected.id}. El núcleo biónico rápido de LEO funciona sin necesidad de este archivo pesado.",
                    color = Color(0xFFFFB45C),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            containerColor = Color(0xFF121C19),
            titleContentColor = PanelTextPrimary,
            textContentColor = PanelTextSecondary,
            title = { Text("Liberar espacio del modelo", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se eliminarán los archivos descargados de ${store.spec.label}. Tus recuerdos, tu huella de voz y el núcleo biónico de LEO se conservan intactos.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        job = scope.launch {
                            try {
                                store.removeDownload()
                                isInstalled = store.installed
                                isEnabled = store.enabled
                                status = "Archivos del modelo eliminados. Espacio liberado y núcleo rápido activo."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                status = failure.message ?: "No pude eliminar la descarga."
                            } finally {
                                job = null
                            }
                        }
                    },
                ) {
                    Text("ELIMINAR ARCHIVO", color = PanelDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text("CANCELAR", color = PanelTextSecondary)
                }
            },
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color(0xFF121C19),
            titleContentColor = PanelTextPrimary,
            textContentColor = PanelTextSecondary,
            title = { Text("Descargar modelo local (${store.spec.id})", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se descargarán hasta ${"%.2f".format(store.spec.bytes / 1_000_000_000.0)} GB desde el repositorio oficial. Puede continuar en segundo plano y reanudarse si salís de Ajustes. Te recomendamos usar Wi-Fi. Se activará automáticamente tras verificar la integridad SHA-256.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        LeoModelDownloadWorker.enqueue(app, selected)
                        status = "Descarga iniciada para ${selected.id}. Podés seguir el avance aquí o en la notificación."
                    },
                ) {
                    Text("DESCARGAR Y VALIDAR", color = PanelAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text("AHORA NO", color = PanelTextSecondary)
                }
            },
        )
    }
}
