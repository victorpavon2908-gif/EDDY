package com.niko.assistant.localai

import com.niko.assistant.compat.UpgradeIdentity

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

data class NikoModelProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val state: State,
) {
    enum class State { CHECKING, DOWNLOADING, INSTALLING, READY, FAILED }
}

class NikoModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, UpgradeIdentity.modelDirectory).apply { mkdirs() }

    fun modelDir(spec: NikoModelSpec): File = File(root, spec.directoryName)

    fun file(spec: NikoModelSpec, relative: String = spec.expectedFiles.first()): File =
        File(modelDir(spec), relative)

    fun isInstalled(spec: NikoModelSpec): Boolean = validateDirectory(spec, modelDir(spec)) == null

    // TTS neuronal y LLM enriquecen a NIKO, pero no deben impedir que el núcleo privado
    // de activación, Voice ID, VAD y reconocimiento español pueda iniciar.
    fun coreReady(): Boolean = NikoModelCatalog.voiceCore.all(::isInstalled)

    fun invalidReason(spec: NikoModelSpec): String? = validateDirectory(spec, modelDir(spec))

    fun invalidate(spec: NikoModelSpec) {
        modelDir(spec).deleteRecursively()
        File(root, "${spec.id}.part").delete()
        File(root, "${spec.id}.download").delete()
        File(root, "${spec.directoryName}.installing").deleteRecursively()
    }

    suspend fun ensureRecommended(
        @Suppress("UNUSED_PARAMETER") profile: NikoDeviceProfile,
        onProgress: (NikoModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        // Install only the acoustic core. Never download a 450+ MB LLM at startup.
        val models = NikoModelCatalog.voiceCore
        var allReady = true
        for (spec in models) {
            var installed = ensure(spec, onProgress)

            // Retain a partial download after a network failure; ensure already rejects invalid installs.
            if (!installed && spec != NikoModelCatalog.localLlm) {
                onProgress(
                    NikoModelProgress(
                        "${spec.id} · reparando",
                        0,
                        0,
                        NikoModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(REPAIR_RETRY_DELAY_MS)
                installed = ensure(spec, onProgress)
            }

            if (!installed) {
                allReady = false
                val essential = spec in NikoModelCatalog.voiceCore
                if (essential) {
                    onProgress(
                        NikoModelProgress(
                            "${spec.id} · error de instalación",
                            0,
                            0,
                            NikoModelProgress.State.DOWNLOADING,
                        ),
                    )
                    break
                } else {
                    // La voz neural y el LLM son mejoras opcionales. NIKO continúa con el
                    // fallback del sistema en vez de bloquear el asistente completo.
                    onProgress(
                        NikoModelProgress(
                            "${spec.id} · alternativa activa",
                            0,
                            0,
                            NikoModelProgress.State.DOWNLOADING,
                        ),
                    )
                }
            }
        }
        allReady
    }

    suspend fun ensureAcousticCore(
        onProgress: (NikoModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        var ready = true
        for (spec in NikoModelCatalog.voiceCore) {
            var installed = ensure(spec, onProgress)
            if (!installed) {
                onProgress(
                    NikoModelProgress(
                        "${spec.id} · reparando",
                        0,
                        0,
                        NikoModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(REPAIR_RETRY_DELAY_MS)
                installed = ensure(spec, onProgress)
            }
            if (!installed) {
                ready = false
                onProgress(
                    NikoModelProgress(
                        "${spec.id} · error de instalación",
                        0,
                        0,
                        NikoModelProgress.State.DOWNLOADING,
                    ),
                )
                break
            }
        }
        ready
    }

    suspend fun repair(
        spec: NikoModelSpec,
        onProgress: (NikoModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        invalidate(spec)
        ensure(spec, onProgress)
    }

    suspend fun ensure(
        spec: NikoModelSpec,
        onProgress: (NikoModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress(NikoModelProgress(spec.id, 0, 0, NikoModelProgress.State.CHECKING))
        if (isInstalled(spec)) {
            onProgress(NikoModelProgress(spec.id, 0, 0, NikoModelProgress.State.READY))
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
                NikoModelProgress(
                    "${spec.id} · instalando",
                    0,
                    archive.length(),
                    NikoModelProgress.State.DOWNLOADING,
                ),
            )
            onProgress(
                NikoModelProgress(
                    spec.id,
                    archive.length(),
                    archive.length(),
                    NikoModelProgress.State.INSTALLING,
                ),
            )

            when (spec.archiveType) {
                NikoArchiveType.FILE -> {
                    val destination = File(installDir, spec.expectedFiles.first())
                    destination.parentFile?.mkdirs()
                    archive.copyTo(destination, overwrite = true)
                    onProgress(
                        NikoModelProgress(
                            "${spec.id} · instalando",
                            archive.length(),
                            archive.length(),
                            NikoModelProgress.State.DOWNLOADING,
                        ),
                    )
                    archive.delete()
                }
                NikoArchiveType.TAR_BZ2 -> {
                    extractTarBz2Safely(archive, installDir) { done, total ->
                        onProgress(
                            NikoModelProgress(
                                "${spec.id} · instalando",
                                done,
                                total,
                                NikoModelProgress.State.DOWNLOADING,
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

            onProgress(NikoModelProgress(spec.id, 0, 0, NikoModelProgress.State.READY))
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            // Conservamos .part si el fallo fue durante la red. Los temporales posteriores
            // a una descarga sí se descartan para no activar contenido incompleto.
            archive.delete()
            installDir.deleteRecursively()
            if (validateDirectory(spec, finalDir) != null) finalDir.deleteRecursively()
            onProgress(NikoModelProgress(spec.id, part.length(), 0, NikoModelProgress.State.FAILED))
            false
        }
    }

    private fun copyBundledModel(spec: NikoModelSpec, output: File): Boolean {
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

    private fun validateDirectory(spec: NikoModelSpec, dir: File): String? {
        if (!dir.isDirectory) return "Falta el directorio ${spec.directoryName}"
        validateFiles(spec, dir)?.let { return it }
        if (spec.requireInstallMarker) {
            val marker = File(dir, INSTALL_MARKER)
            val installedId = if (marker.isFile) marker.readText().trim() else ""
            val compatibleKeyword = spec == NikoModelCatalog.keyword && installedId == UpgradeIdentity.keywordModelId
            if (installedId != spec.id && !compatibleKeyword) {
                return "La revisión instalada de ${spec.id} no es válida"
            }
        }
        return null
    }

    private fun validateFiles(spec: NikoModelSpec, dir: File): String? {
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
        spec: NikoModelSpec,
        output: File,
        onProgress: (NikoModelProgress) -> Unit,
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
                    NikoModelProgress(
                        spec.id,
                        output.length(),
                        0,
                        NikoModelProgress.State.DOWNLOADING,
                    ),
                )
                delay(RETRY_BASE_DELAY_MS * (attempt + 1L))
            }
        }

        throw IOException("No se pudo completar ${spec.id} tras $DOWNLOAD_ATTEMPTS intentos", lastFailure)
    }

    private fun downloadAttempt(
        spec: NikoModelSpec,
        output: File,
        onProgress: (NikoModelProgress) -> Unit,
    ) {
        val existingBytes = output.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "NIKO-Local-Core/0.5.1")
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
                        NikoModelProgress(
                            spec.id,
                            existingBytes,
                            serverTotal,
                            NikoModelProgress.State.DOWNLOADING,
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
            onProgress(NikoModelProgress(spec.id, done, totalBytes, NikoModelProgress.State.DOWNLOADING))

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
                                NikoModelProgress(
                                    spec.id,
                                    done,
                                    totalBytes,
                                    NikoModelProgress.State.DOWNLOADING,
                                ),
                            )
                            nextReport = done + PROGRESS_REPORT_BYTES
                        }
                    }
                }
            }

            val finalTotal = if (totalBytes > 0L) totalBytes else done
            onProgress(NikoModelProgress(spec.id, done, finalTotal, NikoModelProgress.State.DOWNLOADING))

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
        private const val INSTALL_MARKER = UpgradeIdentity.modelMarker
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
