package com.eddy.assistant.localai

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
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
            if (!ensure(spec, onProgress)) allReady = false
            // El LLM es opcional; no debe impedir que voz/ASR terminen de instalarse.
            if (!allReady && spec != EddyModelCatalog.localLlm && !isInstalled(spec)) break
        }
        allReady
    }

    suspend fun ensureAcousticCore(
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        var ready = true
        for (spec in EddyModelCatalog.acousticCore) {
            if (!ensure(spec, onProgress)) {
                ready = false
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

        // Cualquier instalación vieja/incompleta se descarta. La nueva se prepara
        // en un directorio temporal y solo sustituye a la anterior cuando valida.
        val finalDir = modelDir(spec)
        val installDir = File(root, "${spec.directoryName}.installing")
        val part = File(root, "${spec.id}.part")
        val archive = File(root, "${spec.id}.download")

        runCatching {
            installDir.deleteRecursively()
            installDir.mkdirs()
            part.delete()
            archive.delete()

            download(spec, part, onProgress)
            check(part.renameTo(archive)) { "No se pudo preparar ${spec.id}" }
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
                    archive.delete()
                }
                EddyArchiveType.TAR_BZ2 -> {
                    extractTarBz2Safely(archive, installDir)
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
        }.getOrElse {
            part.delete()
            archive.delete()
            installDir.deleteRecursively()
            // Si había una instalación marcada como inválida, no la conservamos para
            // evitar que el siguiente arranque vuelva a intentar cargarla.
            if (validateDirectory(spec, finalDir) != null) finalDir.deleteRecursively()
            onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.FAILED))
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

    private fun download(
        spec: EddyModelSpec,
        output: File,
        onProgress: (EddyModelProgress) -> Unit,
    ) {
        if (output.exists()) output.delete()
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.setRequestProperty("User-Agent", "EDDY-Local-Core/0.5.1")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            var done = 0L
            var nextReport = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                output.outputStream().buffered().use { out ->
                    val buffer = ByteArray(128 * 1024)
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
                                    total,
                                    EddyModelProgress.State.DOWNLOADING,
                                ),
                            )
                            nextReport = done + 2L * 1024L * 1024L
                        }
                    }
                }
            }
            if (total > 0L) check(done == total) { "Descarga truncada: ${spec.id}" }
            check(output.length() >= spec.minBytes) { "Descarga incompleta: ${spec.id}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTarBz2Safely(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())),
        ).use { tar ->
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
                    out.outputStream().buffered().use { target -> tar.copyTo(target) }
                }
            }
        }
    }

    companion object {
        private const val INSTALL_MARKER = ".eddy-model-id"
    }
}
