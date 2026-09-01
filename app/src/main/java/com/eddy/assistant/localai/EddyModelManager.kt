package com.eddy.assistant.localai

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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

    // TTS neuronal y LLM enriquecen a EDDY, pero no deben impedir que el núcleo privado
    // de activación, Voice ID, VAD y reconocimiento español pueda iniciar.
    fun coreReady(): Boolean = EddyModelCatalog.voiceCore.all(::isInstalled)

    fun invalidReason(spec: EddyModelSpec): String? = validateDirectory(spec, modelDir(spec))

    fun invalidate(spec: EddyModelSpec) {
        modelDir(spec).deleteRecursively()
        File(root, "${spec.id}.part").delete()
        File(root, "${spec.id}.download").delete()
        File(root, "${spec.directoryName}.installing").deleteRecursively()
    }

    suspend fun ensureRecommended(
        @Suppress("UNUSED_PARAMETER") profile: EddyDeviceProfile,
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        // Install only the acoustic core. Never download a 450+ MB LLM at startup.
        val models = EddyModelCatalog.voiceCore
        var allReady = true
        for (spec in models) {
            var installed = ensure(spec, onProgress)

            // Retain a partial download after a network failure; ensure already rejects invalid installs.
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
                installed = ensure(spec, onProgress)
            }

            if (!installed) {
                allReady = false
                val essential = spec in EddyModelCatalog.voiceCore
                if (essential) {
                    onProgress(
                        EddyModelProgress(
                            "${spec.id} · error de instalación",
                            0,
                            0,
                            EddyModelProgress.State.DOWNLOADING,
                        ),
                    )
                    break
                } else {
                    // La voz neural y el LLM son mejoras opcionales. EDDY continúa con el
                    // fallback del sistema en vez de bloquear el asistente completo.
                    onProgress(
                        EddyModelProgress(
                            "${spec.id} · alternativa activa",
                            0,
                            0,
                            EddyModelProgress.State.DOWNLOADING,
                        ),
                    )
                }
            }
        }
        allReady
    }

    suspend fun ensureAcousticCore(
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        var ready = true
        for (spec in EddyModelCatalog.voiceCore) {
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
                installed = ensure(spec, onProgress)
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

            if (!copyBundledModel(spec, part)) download(spec, part, onProgress)
            check(part.renameTo(archive)) { "No se pudo preparar ${spec.id}" }

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
            // Conservamos .part si el fallo fue durante la red. Los temporales posteriores
            // a una descarga sí se descartan para no activar contenido incompleto.
            archive.delete()
            installDir.deleteRecursively()
            if (validateDirectory(spec, finalDir) != null) finalDir.deleteRecursively()
            onProgress(EddyModelProgress(spec.id, part.length(), 0, EddyModelProgress.State.FAILED))
            false
        }
    }

    private fun copyBundledModel(spec: EddyModelSpec, output: File): Boolean {
        val asset = "voice-core/${spec.id}.bundle"
        val input = try { appContext.assets.open(asset) } catch (_: IOException) { return false }
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { source ->
            output.outputStream().buffered().use { target ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    target.write(buffer, 0, count)
                }
            }
        }
        val expected = appContext.assets.open("voice-core/${spec.id}.sha256")
            .bufferedReader().use { it.readText().trim() }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == expected && output.length() >= spec.minBytes) { "Núcleo incluido incompleto: ${spec.id}" }
        return true
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

        fun reportProgress(force: Boolean = false) {
            val consumed = countingInput.bytesRead.coerceAtMost(totalBytes)
            if (force || consumed >= nextReport) {
                onInstallProgress(consumed, totalBytes)
                nextReport = consumed + INSTALL_PROGRESS_REPORT_BYTES
            }
        }

        onInstallProgress(0L, totalBytes)

        BufferedInputStream(countingInput, ARCHIVE_BUFFER_BYTES).use { compressedInput ->
            BZip2CompressorInputStream(compressedInput).use { bz2 ->
                BufferedInputStream(bz2, ARCHIVE_BUFFER_BYTES).use { decompressedInput ->
                    TarArchiveInputStream(decompressedInput).use { tar ->
                        val copyBuffer = ByteArray(ARCHIVE_COPY_BUFFER_BYTES)
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            val out = File(destination, entry.name).canonicalFile
                            check(out.path == canonicalRoot.path || out.path.startsWith(canonicalRoot.path + File.separator)) {
                                "Ruta insegura en modelo"
                            }
                            if (entry.isDirectory) {
                                out.mkdirs()
                                reportProgress()
                            } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().buffered(ARCHIVE_BUFFER_BYTES).use { target ->
                                    // No usamos tar.copyTo(): en modelos con un ONNX grande ese
                                    // método no devolvía control hasta acabar el archivo completo,
                                    // dejando teléfonos lentos aparentemente clavados en 1%.
                                    while (true) {
                                        val count = tar.read(copyBuffer, 0, copyBuffer.size)
                                        if (count < 0) break
                                        target.write(copyBuffer, 0, count)
                                        reportProgress()
                                    }
                                }
                                reportProgress(force = true)
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
        private const val INSTALL_PROGRESS_REPORT_BYTES = 512L * 1024L
        private const val DOWNLOAD_BUFFER_BYTES = 256 * 1024
        private const val ARCHIVE_BUFFER_BYTES = 128 * 1024
        private const val ARCHIVE_COPY_BUFFER_BYTES = 64 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}