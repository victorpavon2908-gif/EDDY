package com.eddy.assistant.localai

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class EddyModelProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val state: State,
) {
    enum class State { CHECKING, DOWNLOADING, INSTALLING, READY, FAILED }
}

class EddyModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "eddy-local-ai").apply { mkdirs() }

    fun modelDir(spec: EddyModelSpec): File = File(root, spec.directoryName)

    fun file(spec: EddyModelSpec, relative: String = spec.expectedFiles.first()): File =
        File(modelDir(spec), relative)

    fun isInstalled(spec: EddyModelSpec): Boolean = validateDirectory(spec, modelDir(spec)) == null

    fun coreReady(): Boolean = EddyModelCatalog.acousticCore.all(::isInstalled)

    fun invalidReason(spec: EddyModelSpec): String? = validateDirectory(spec, modelDir(spec))

    fun invalidate(spec: EddyModelSpec) {
        modelDir(spec).deleteRecursively()
        File(root, "${spec.id}.part").delete()
        File(root, "${spec.id}.download").delete()
        File(root, "${spec.directoryName}.installing").deleteRecursively()
    }

    suspend fun ensureRecommended(
        profile: EddyDeviceProfile,
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val models = buildList {
            addAll(EddyModelCatalog.acousticCore)
            if (profile.supportsLocalLlm) add(EddyModelCatalog.localLlm)
        }
        var allReady = true
        for (spec in models) {
            var installed = ensure(spec, onProgress)

            // Los modelos del núcleo son obligatorios. Si la descarga terminó pero la
            // extracción/validación falló, hacemos una segunda instalación completamente
            // limpia. Esto evita que EDDY quede clavado para siempre en el último 100%.
            if (!installed && spec != EddyModelCatalog.localLlm) {
                onProgress(
                    EddyModelProgress(
                        "${spec.id} · reparando",
                        0,
                        0,
                        EddyModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(REPAIR_RETRY_DELAY_MS)
                installed = repair(spec, onProgress)
            }

            if (!installed) {
                allReady = false
                if (spec != EddyModelCatalog.localLlm) {
                    // EddyAssistantService actualmente muestra DOWNLOADING en pantalla.
                    // Emitimos además este estado visible para no dejar un 100% obsoleto
                    // cuando los dos intentos de instalación hayan fallado.
                    onProgress(
                        EddyModelProgress(
                            "${spec.id} · error de instalación",
                            0,
                            0,
                            EddyModelProgress.State.DOWNLOADING,
                        ),
                    )
                    break
                }
            }
        }
        allReady
    }

    suspend fun ensureAcousticCore(
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        var ready = true
        for (spec in EddyModelCatalog.acousticCore) {
            var installed = ensure(spec, onProgress)
            if (!installed) {
                onProgress(
                    EddyModelProgress(
                        "${spec.id} · reparando",
                        0,
                        0,
                        EddyModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(REPAIR_RETRY_DELAY_MS)
                installed = repair(spec, onProgress)
            }
            if (!installed) {
                ready = false
                onProgress(
                    EddyModelProgress(
                        "${spec.id} · error de instalación",
                        0,
                        0,
                        EddyModelProgress.State.DOWNLOADING,
                    ),
                )
                break
            }
        }
        ready
    }

    suspend fun repair(
        spec: EddyModelSpec,
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        invalidate(spec)
        ensure(spec, onProgress)
    }

    suspend fun ensure(
        spec: EddyModelSpec,
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.CHECKING))
        if (isInstalled(spec)) {
            onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.READY))
            return@withContext true
        }

        // La instalación se arma en un directorio temporal y solo se activa cuando valida.
        // El archivo .part NO se elimina aquí: si Android o la red interrumpen una descarga,
        // el siguiente intento continúa desde el último byte en vez de volver a 0%.
        val finalDir = modelDir(spec)
        val installDir = File(root, "${spec.directoryName}.installing")
        val part = File(root, "${spec.id}.part")
        val archive = File(root, "${spec.id}.download")

        runCatching {
            installDir.deleteRecursively()
            installDir.mkdirs()
            archive.delete()

            download(spec, part, onProgress)
            check(part.renameTo(archive)) { "No se pudo preparar ${spec.id}" }

            // La interfaz existente solo pinta DOWNLOADING. Antes, desde aquí en adelante,
            // se quedaba mostrando el 100% de descarga aunque Android estuviera varios
            // minutos descomprimiendo. Publicamos una fase visible de instalación.
            onProgress(
                EddyModelProgress(
                    "${spec.id} · instalando",
                    0,
                    archive.length(),
                    EddyModelProgress.State.DOWNLOADING,
                ),
            )
            onProgress(
                EddyModelProgress(
                    spec.id,
                    archive.length(),
                    archive.length(),
                    EddyModelProgress.State.INSTALLING,
                ),
            )

            when (spec.archiveType) {
                EddyArchiveType.FILE -> {
                    val destination = File(installDir, spec.expectedFiles.first())
                    destination.parentFile?.mkdirs()
                    archive.copyTo(destination, overwrite = true)
                    onProgress(
                        EddyModelProgress(
                            "${spec.id} · instalando",
                            archive.length(),
                            archive.length(),
                            EddyModelProgress.State.DOWNLOADING,
                        ),
                    )
                    archive.delete()
                }
                EddyArchiveType.TAR_BZ2 -> {
                    extractTarBz2Safely(archive, installDir) { done, total ->
                        onProgress(
                            EddyModelProgress(
                                "${spec.id} · instalando",
                                done,
                                total,
                                EddyModelProgress.State.DOWNLOADING,
                            ),
                        )
                    }
                    archive.delete()
                }
            }

            val installProblem = validateFiles(spec, installDir)
            check(installProblem == null) { installProblem ?: "Modelo inválido" }

            if (spec.requireInstallMarker) {
                File(installDir, INSTALL_MARKER).writeText(spec.id)
            }

            // Verificamos también el marcador antes de hacer visible la instalación.
            val finalProblemInTemp = validateDirectory(spec, installDir)
            check(finalProblemInTemp == null) { finalProblemInTemp ?: "Modelo inválido" }

            finalDir.deleteRecursively()
            check(installDir.renameTo(finalDir)) { "No se pudo activar ${spec.id}" }

            val activatedProblem = validateDirectory(spec, finalDir)
            check(activatedProblem == null) { activatedProblem ?: "Modelo inválido tras instalar" }

            onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.READY))
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            // Conservamos .part si el fallo fue durante la red. Así EDDY puede reanudar.
            // Los temporales posteriores a la descarga sí se descartan para no activar basura.
            archive.delete()
            installDir.deleteRecursively()
            if (validateDirectory(spec, finalDir) != null) finalDir.deleteRecursively()
            onProgress(EddyModelProgress(spec.id, part.length(), 0, EddyModelProgress.State.FAILED))
            false
        }
    }

    private fun validateDirectory(spec: EddyModelSpec, dir: File): String? {
        if (!dir.isDirectory) return "Falta el directorio ${spec.directoryName}"
        validateFiles(spec, dir)?.let { return it }
        if (spec.requireInstallMarker) {
            val marker = File(dir, INSTALL_MARKER)
            if (!marker.isFile || marker.readText().trim() != spec.id) {
                return "La revisión instalada de ${spec.id} no es válida"
            }
        }
        return null
    }

    private fun validateFiles(spec: EddyModelSpec, dir: File): String? {
        for (relative in spec.expectedFiles) {
            val candidate = File(dir, relative)
            if (!candidate.exists()) return "Falta $relative"
            val minimum = spec.expectedMinBytes[relative]
            if (minimum != null) {
                if (!candidate.isFile) return "$relative no es un archivo válido"
                if (candidate.length() < minimum) {
                    return "$relative está incompleto (${candidate.length()} < $minimum bytes)"
                }
            }
        }
        return null
    }

    private suspend fun download(
        spec: EddyModelSpec,
        output: File,
        onProgress: (EddyModelProgress) -> Unit,
    ) {
        var lastFailure: Throwable? = null

        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                downloadAttempt(spec, output, onProgress)
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastFailure = error
                if (attempt == DOWNLOAD_ATTEMPTS - 1) return@repeat

                // Mantenemos el mismo modelId para no romper consumidores del progreso.
                // El próximo intento usa HTTP Range y continúa el .part existente.
                onProgress(
                    EddyModelProgress(
                        spec.id,
                        output.length(),
                        0,
                        EddyModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(RETRY_BASE_DELAY_MS * (attempt + 1L))
            }
        }

        throw IOException("No se pudo completar ${spec.id} tras $DOWNLOAD_ATTEMPTS intentos", lastFailure)
    }

    private fun downloadAttempt(
        spec: EddyModelSpec,
        output: File,
        onProgress: (EddyModelProgress) -> Unit,
    ) {
        val existingBytes = output.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "EDDY-Local-Core/0.5.1")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (existingBytes > 0L) {
            connection.setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode

            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && existingBytes > 0L) {
                val serverTotal = contentRangeTotal(connection.getHeaderField("Content-Range"))
                if (serverTotal != null && serverTotal == existingBytes && output.length() >= spec.minBytes) {
                    onProgress(
                        EddyModelProgress(
                            spec.id,
                            existingBytes,
                            serverTotal,
                            EddyModelProgress.State.DOWNLOADING,
                        ),
                    )
                    return
                }
                output.delete()
                throw IOException("El servidor rechazó la reanudación de ${spec.id}")
            }

            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode al descargar ${spec.id}")
            }

            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (existingBytes > 0L && !append) {
                // Algunos CDN ignoran Range y responden 200. En ese caso reiniciamos solo
                // este intento para no concatenar dos archivos y corromper el modelo.
                output.delete()
            }

            val baseBytes = if (append) existingBytes else 0L
            val responseBytes = connection.contentLengthLong.coerceAtLeast(0L)
            val rangedTotal = contentRangeTotal(connection.getHeaderField("Content-Range"))
            val totalBytes = when {
                rangedTotal != null -> rangedTotal
                responseBytes > 0L -> baseBytes + responseBytes
                else -> 0L
            }

            var done = baseBytes
            var nextReport = done
            onProgress(EddyModelProgress(spec.id, done, totalBytes, EddyModelProgress.State.DOWNLOADING))

            BufferedInputStream(connection.inputStream, DOWNLOAD_BUFFER_BYTES).use { input ->
                FileOutputStream(output, append).buffered(DOWNLOAD_BUFFER_BYTES).use { out ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                        done += count
                        if (done >= nextReport) {
                            onProgress(
                                EddyModelProgress(
                                    spec.id,
                                    done,
                                    totalBytes,
                                    EddyModelProgress.State.DOWNLOADING,
                                ),
                            )
                            nextReport = done + PROGRESS_REPORT_BYTES
                        }
                    }
                }
            }

            // Siempre enviamos el progreso final. Antes el último callback podía quedar en
            // 55%, 95%, etc. aunque la descarga ya hubiese terminado y estuviera extrayendo.
            val finalTotal = if (totalBytes > 0L) totalBytes else done
            onProgress(EddyModelProgress(spec.id, done, finalTotal, EddyModelProgress.State.DOWNLOADING))

            if (totalBytes > 0L && done != totalBytes) {
                throw IOException("Descarga truncada: ${spec.id} ($done/$totalBytes)")
            }
            if (output.length() < spec.minBytes) {
                throw IOException("Descarga incompleta: ${spec.id}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun contentRangeTotal(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return value.substringAfterLast('/', missingDelimiterValue = "")
            .trim()
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun extractTarBz2Safely(
        archive: File,
        destination: File,
        onInstallProgress: (done: Long, total: Long) -> Unit,
    ) {
        val canonicalRoot = destination.canonicalFile
        val totalBytes = archive.length().coerceAtLeast(1L)
        val countingInput = CountingInputStream(archive.inputStream())
        var nextReport = 0L

        onInstallProgress(0L, totalBytes)

        BufferedInputStream(countingInput, ARCHIVE_BUFFER_BYTES).use { compressedInput ->
            BZip2CompressorInputStream(compressedInput).use { bz2 ->
                BufferedInputStream(bz2, ARCHIVE_BUFFER_BYTES).use { decompressedInput ->
                    TarArchiveInputStream(decompressedInput).use { tar ->
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            val out = File(destination, entry.name).canonicalFile
                            check(out.path == canonicalRoot.path || out.path.startsWith(canonicalRoot.path + File.separator)) {
                                "Ruta insegura en modelo"
                            }
                            if (entry.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().buffered(ARCHIVE_BUFFER_BYTES).use { target ->
                                    tar.copyTo(target, ARCHIVE_BUFFER_BYTES)
                                }
                            }

                            val consumed = countingInput.bytesRead.coerceAtMost(totalBytes)
                            if (consumed >= nextReport) {
                                onInstallProgress(consumed, totalBytes)
                                nextReport = consumed + INSTALL_PROGRESS_REPORT_BYTES
                            }
                        }
                    }
                }
            }
        }

        onInstallProgress(totalBytes, totalBytes)
    }

    private class CountingInputStream(input: java.io.InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead += 1L
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead += count.toLong()
            return count
        }
    }

    companion object {
        private const val INSTALL_MARKER = ".eddy-model-id"
        private const val DOWNLOAD_ATTEMPTS = 4
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val RETRY_BASE_DELAY_MS = 1_500L
        private const val REPAIR_RETRY_DELAY_MS = 1_000L
        private const val PROGRESS_REPORT_BYTES = 512L * 1024L
        private const val INSTALL_PROGRESS_REPORT_BYTES = 1L * 1024L * 1024L
        private const val DOWNLOAD_BUFFER_BYTES = 256 * 1024
        private const val ARCHIVE_BUFFER_BYTES = 256 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
