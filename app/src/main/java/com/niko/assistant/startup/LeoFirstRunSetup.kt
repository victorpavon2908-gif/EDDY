package com.niko.assistant.startup

import android.content.Context
import android.os.StatFs
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelProgress
import com.niko.assistant.localai.NikoModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Instalación inicial atómica de LEO.
 *
 * La primera vez que se abre la app, LEO NO inicia el servicio de voz hasta que todos
 * los módulos previstos para ese teléfono estén instalados y validados. Las descargas
 * parciales se conservan por NikoModelManager, así que si Android cierra el proceso o
 * se pierde Internet, la siguiente apertura continúa en vez de empezar de cero.
 *
 * En aperturas posteriores [isReady] sólo valida archivos locales; no vuelve a descargar
 * nada mientras el paquete inicial siga íntegro. Además se guarda una marca muy pequeña
 * para que receptores/servicios de Android puedan saber, sin recorrer modelos grandes,
 * si la preparación inicial ya fue liberada.
 */
class LeoFirstRunSetup(context: Context) {
    private val appContext = context.applicationContext
    private val models = NikoModelManager(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val profile: NikoDeviceProfile = NikoDeviceProfile.detect(appContext)

    fun requiredModels(): List<NikoModelSpec> = NikoModelCatalog.firstRunModels(profile)

    fun isReady(): Boolean {
        val ready = requiredModels().all(models::isInstalled)
        if (prefs.getBoolean(READY_KEY, false) != ready) {
            prefs.edit().putBoolean(READY_KEY, ready).apply()
        }
        return ready
    }

    fun missingModels(): List<NikoModelSpec> = requiredModels().filterNot(models::isInstalled)

    /**
     * Espacio final estimado + una copia temporal del modelo más grande que falte.
     * NikoModelManager instala en un directorio temporal y activa sólo tras validar,
     * por lo que durante una instalación puede coexistir el archivo descargado con su copia.
     */
    fun estimatedRequiredFreeBytes(): Long {
        val missing = missingModels()
        if (missing.isEmpty()) return 0L
        val finalBytes = missing.sumOf(::estimatedInstalledBytes)
        val largestTemporary = missing.maxOf(::estimatedInstalledBytes)
        return finalBytes + largestTemporary + STORAGE_HEADROOM_BYTES
    }

    fun availableBytes(): Long = runCatching { StatFs(appContext.filesDir.absolutePath).availableBytes }
        .getOrDefault(Long.MAX_VALUE)

    suspend fun prepare(onProgress: (LeoFirstRunState) -> Unit = {}): LeoFirstRunResult =
        withContext(Dispatchers.IO) {
            markReady(false)
            val required = requiredModels()
            onProgress(
                LeoFirstRunState(
                    phase = LeoFirstRunState.Phase.CHECKING,
                    currentLabel = "Verificando instalación local",
                    currentIndex = 0,
                    totalModels = required.size,
                    message = "Comprobando todo antes de arrancar LEO…",
                ),
            )

            if (required.all(models::isInstalled)) {
                markReady(true)
                onProgress(LeoFirstRunState.ready(required.size))
                return@withContext LeoFirstRunResult(true, "LEO está preparado.")
            }

            val requiredFree = estimatedRequiredFreeBytes()
            val available = availableBytes()
            if (available < requiredFree) {
                val result = LeoFirstRunResult(
                    ready = false,
                    message = "No hay espacio suficiente para preparar LEO. Liberá aproximadamente ${formatBytes(requiredFree - available)} y reintentá.",
                )
                onProgress(LeoFirstRunState.failed(required.size, result.message))
                return@withContext result
            }

            for ((index, spec) in required.withIndex()) {
                if (models.isInstalled(spec)) {
                    onProgress(
                        LeoFirstRunState(
                            phase = LeoFirstRunState.Phase.CHECKING,
                            currentLabel = labelFor(spec),
                            currentIndex = index + 1,
                            totalModels = required.size,
                            message = "Módulo ya preparado · verificando siguiente…",
                        ),
                    )
                    continue
                }

                var installed = false
                repeat(MODEL_INSTALL_PASSES) { pass ->
                    if (installed) return@repeat
                    if (pass > 0) {
                        onProgress(
                            LeoFirstRunState(
                                phase = LeoFirstRunState.Phase.DOWNLOADING,
                                currentLabel = labelFor(spec),
                                currentIndex = index + 1,
                                totalModels = required.size,
                                message = "Reanudando la descarga pendiente…",
                            ),
                        )
                        delay(RETRY_DELAY_MS)
                    }

                    try {
                        installed = models.ensure(spec) { raw ->
                            onProgress(raw.toFirstRunState(spec, index + 1, required.size))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    }
                }

                if (!installed || !models.isInstalled(spec)) {
                    val result = LeoFirstRunResult(
                        ready = false,
                        message = "No pude terminar ${labelFor(spec)}. Revisá Internet y espacio; LEO no arrancará incompleto.",
                    )
                    onProgress(LeoFirstRunState.failed(required.size, result.message, labelFor(spec)))
                    return@withContext result
                }
            }

            val ready = required.all(models::isInstalled)
            markReady(ready)
            val result = if (ready) {
                LeoFirstRunResult(true, "Instalación inicial completa. LEO ya puede arrancar normalmente.")
            } else {
                LeoFirstRunResult(false, "La verificación final encontró un módulo incompleto. Reintentá la preparación.")
            }
            if (ready) onProgress(LeoFirstRunState.ready(required.size))
            else onProgress(LeoFirstRunState.failed(required.size, result.message))
            result
        }

    private fun markReady(value: Boolean) {
        prefs.edit().putBoolean(READY_KEY, value).apply()
    }

    private fun NikoModelProgress.toFirstRunState(
        spec: NikoModelSpec,
        index: Int,
        total: Int,
    ): LeoFirstRunState {
        val phase = when (state) {
            NikoModelProgress.State.CHECKING -> LeoFirstRunState.Phase.CHECKING
            NikoModelProgress.State.DOWNLOADING -> LeoFirstRunState.Phase.DOWNLOADING
            NikoModelProgress.State.INSTALLING -> LeoFirstRunState.Phase.INSTALLING
            NikoModelProgress.State.READY -> LeoFirstRunState.Phase.CHECKING
            NikoModelProgress.State.FAILED -> LeoFirstRunState.Phase.DOWNLOADING
        }
        val message = when (state) {
            NikoModelProgress.State.CHECKING -> "Verificando módulo…"
            NikoModelProgress.State.DOWNLOADING -> "Descargando módulo local…"
            NikoModelProgress.State.INSTALLING -> "Instalando y validando…"
            NikoModelProgress.State.READY -> "Módulo listo."
            NikoModelProgress.State.FAILED -> "La descarga se interrumpió; LEO intentará reanudarla."
        }
        return LeoFirstRunState(
            phase = phase,
            currentLabel = labelFor(spec),
            currentIndex = index,
            totalModels = total,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            message = message,
        )
    }

    companion object {
        private const val PREFS = "leo_first_run_setup"
        private const val READY_KEY = "bundle_ready_v1"
        private const val MODEL_INSTALL_PASSES = 2
        private const val RETRY_DELAY_MS = 1_250L
        private const val STORAGE_HEADROOM_BYTES = 256L * 1024L * 1024L

        /** Lectura O(1) para impedir que un servicio de Android arranque antes de tiempo. */
        fun isMarkedReady(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(READY_KEY, false)

        fun labelFor(spec: NikoModelSpec): String = when (spec) {
            NikoModelCatalog.keyword -> "Activación por voz LEO"
            NikoModelCatalog.vad -> "Detección de voz"
            NikoModelCatalog.denoiser -> "Limpieza de audio"
            NikoModelCatalog.spanishAsr -> "Reconocimiento español Canary"
            NikoModelCatalog.whisperAsr -> "Segundo oído Whisper"
            NikoModelCatalog.speaker -> "Identificación de voz"
            NikoModelCatalog.spanishVoice -> "Voz local de LEO"
            NikoModelCatalog.localLlmQuality -> "Cerebro local de alta calidad"
            NikoModelCatalog.localLlmFast -> "Cerebro local rápido de respaldo"
            else -> spec.id
        }

        fun estimatedInstalledBytes(spec: NikoModelSpec): Long = max(
            spec.minBytes,
            spec.expectedMinBytes.values.sum().coerceAtLeast(0L),
        )

        fun formatBytes(bytes: Long): String {
            val safe = bytes.coerceAtLeast(0L)
            val gb = safe.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) "%.1f GB".format(gb) else "%.0f MB".format(safe / (1024.0 * 1024.0))
        }
    }
}

data class LeoFirstRunResult(
    val ready: Boolean,
    val message: String,
)

data class LeoFirstRunState(
    val phase: Phase = Phase.WAITING,
    val currentLabel: String = "Preparando LEO",
    val currentIndex: Int = 0,
    val totalModels: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = "Preparando la primera ejecución…",
) {
    enum class Phase { WAITING, CHECKING, DOWNLOADING, INSTALLING, READY, FAILED }

    val modelProgress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f

    val overallProgress: Float
        get() {
            if (totalModels <= 0) return 0f
            val completedBefore = (currentIndex - 1).coerceAtLeast(0)
            return ((completedBefore + modelProgress) / totalModels.toFloat()).coerceIn(0f, 1f)
        }

    companion object {
        fun ready(total: Int) = LeoFirstRunState(
            phase = Phase.READY,
            currentLabel = "LEO listo",
            currentIndex = total,
            totalModels = total,
            downloadedBytes = 1L,
            totalBytes = 1L,
            message = "Todo quedó descargado, instalado y verificado.",
        )

        fun failed(total: Int, message: String, label: String = "Preparación detenida") = LeoFirstRunState(
            phase = Phase.FAILED,
            currentLabel = label,
            totalModels = total,
            message = message,
        )
    }
}
