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

    fun isInstalled(spec: EddyModelSpec): Boolean =
        spec.expectedFiles.all { relative -> File(modelDir(spec), relative).exists() }

    fun coreReady(): Boolean = EddyModelCatalog.acousticCore.all(::isInstalled)

    suspend fun ensureRecommended(
        profile: EddyDeviceProfile,
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val models = buildList {
            addAll(EddyModelCatalog.acousticCore)
            if (profile.supportsLocalLlm) add(EddyModelCatalog.localLlm)
        }
        models.all { ensure(it, onProgress) }
    }

    suspend fun ensureAcousticCore(
        onProgress: (EddyModelProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        EddyModelCatalog.acousticCore.all { ensure(it, onProgress) }
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

        val dir = modelDir(spec).apply { mkdirs() }
        val part = File(root, "${spec.id}.part")
        val archive = File(root, "${spec.id}.download")

        runCatching {
            download(spec, part, onProgress)
            if (archive.exists()) archive.delete()
            check(part.renameTo(archive)) { "No se pudo preparar ${spec.id}" }
            onProgress(EddyModelProgress(spec.id, archive.length(), archive.length(), EddyModelProgress.State.INSTALLING))

            when (spec.archiveType) {
                EddyArchiveType.FILE -> {
                    val destination = File(dir, spec.expectedFiles.first())
                    destination.parentFile?.mkdirs()
                    if (destination.exists()) destination.delete()
                    check(archive.renameTo(destination)) { "No se pudo mover ${spec.id}" }
                }
                EddyArchiveType.TAR_BZ2 -> {
                    extractTarBz2Safely(archive, dir)
                    archive.delete()
                }
            }

            check(isInstalled(spec)) { "El modelo ${spec.id} quedó incompleto" }
            onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.READY))
            true
        }.getOrElse {
            part.delete()
            archive.delete()
            onProgress(EddyModelProgress(spec.id, 0, 0, EddyModelProgress.State.FAILED))
            false
        }
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
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "EDDY-Local-Core/0.5")
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
                            onProgress(EddyModelProgress(spec.id, done, total, EddyModelProgress.State.DOWNLOADING))
                            nextReport = done + 2L * 1024L * 1024L
                        }
                    }
                }
            }
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
}
