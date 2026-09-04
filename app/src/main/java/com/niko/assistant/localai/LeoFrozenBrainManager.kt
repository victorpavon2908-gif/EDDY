package com.niko.assistant.localai

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Downloads, verifies and atomically activates Leo's immutable ~500 MB knowledge brain. */
class LeoFrozenBrainManager(context: Context) {
    data class Progress(
        val state: State,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val message: String = "",
    ) {
        enum class State { CHECKING, DOWNLOADING, VERIFYING, INSTALLING, READY, FAILED }
    }

    internal data class ReleaseManifest(
        val version: String,
        val archiveName: String,
        val archiveBytes: Long,
        val archiveSha256: String,
        val installedBytes: Long,
        val installedSha256: String,
    )

    private val appContext = context.applicationContext
    private val storage = LeoBrainStorage(appContext)

    fun isInstalled(): Boolean {
        val database = databaseFile()
        val marker = File(storage.frozen, VALIDATED_MARKER)
        if (!database.isFile || database.length() !in LeoBrainStorage.FROZEN_MIN_BYTES..LeoBrainStorage.FROZEN_MAX_BYTES) return false
        if (!marker.isFile) return false
        val value = runCatching { marker.readText().trim() }.getOrNull().orEmpty()
        return value.startsWith("$BRAIN_VERSION|")
    }

    fun databaseFile(): File = File(storage.frozen, DATABASE_NAME)

    suspend fun ensureInstalled(onProgress: (Progress) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        onProgress(Progress(Progress.State.CHECKING, message = "Comprobando cerebro congelado"))
        if (isInstalled()) {
            onProgress(Progress(Progress.State.READY, message = "Cerebro congelado listo"))
            return@withContext true
        }

        runCatching {
            val manifest = fetchManifest()
            require(manifest.version == BRAIN_VERSION) { "Versión de cerebro inesperada" }
            require(manifest.archiveName == ARCHIVE_NAME) { "Paquete de cerebro inesperado" }
            require(manifest.installedBytes in LeoBrainStorage.FROZEN_MIN_BYTES..LeoBrainStorage.FROZEN_MAX_BYTES) {
                "El cerebro congelado no respeta la cuota de 500 MB"
            }
            require(isSha256(manifest.archiveSha256) && isSha256(manifest.installedSha256)) { "Hashes inválidos" }
            require(storage.canInstallFrozen(manifest.archiveBytes, manifest.installedBytes)) {
                "No hay espacio seguro suficiente para instalar el cerebro de Leo"
            }

            val archive = File(storage.downloads, "$ARCHIVE_NAME.part")
            downloadArchive(manifest, archive, onProgress)
            onProgress(Progress(Progress.State.VERIFYING, archive.length(), manifest.archiveBytes, "Verificando paquete"))
            require(archive.length() == manifest.archiveBytes) { "Descarga incompleta del cerebro" }
            require(sha256(archive) == manifest.archiveSha256) { "SHA-256 del paquete cerebral no coincide" }

            val installing = File(storage.root, "${LeoBrainStorage.FROZEN_DIRECTORY}.installing")
            makeWritable(installing)
            installing.deleteRecursively()
            require(installing.mkdirs()) { "No se pudo preparar la instalación cerebral" }
            onProgress(Progress(Progress.State.INSTALLING, 0L, manifest.installedBytes, "Instalando cerebro congelado"))
            unzipSafely(archive, installing, manifest.installedBytes, onProgress)

            val candidate = File(installing, DATABASE_NAME)
            require(candidate.isFile && candidate.length() == manifest.installedBytes) { "Tamaño instalado del cerebro no coincide" }
            require(sha256(candidate) == manifest.installedSha256) { "SHA-256 del cerebro instalado no coincide" }
            require(File(installing, INTERNAL_MANIFEST).isFile) { "Falta el manifiesto interno del cerebro" }
            require(File(installing, ATTRIBUTION_FILE).isFile) { "Falta la atribución del conocimiento" }

            File(installing, VALIDATED_MARKER).writeText("${manifest.version}|${manifest.installedSha256}")
            val finalDir = storage.frozen
            makeWritable(finalDir)
            require(finalDir.deleteRecursively() || !finalDir.exists()) { "No se pudo reemplazar el cerebro anterior" }
            require(installing.renameTo(finalDir)) { "No se pudo activar el cerebro congelado" }
            setFrozenReadOnly(finalDir)
            archive.delete()

            onProgress(Progress(Progress.State.READY, manifest.installedBytes, manifest.installedBytes, "Cerebro congelado listo"))
            true
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            onProgress(Progress(Progress.State.FAILED, message = error.message ?: "No se pudo instalar el cerebro congelado"))
            false
        }
    }

    private fun fetchManifest(): ReleaseManifest {
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "LEO-Frozen-Brain/$BRAIN_VERSION")
        return try {
            connection.connect()
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} al consultar el cerebro" }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            parseManifest(raw)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadArchive(
        manifest: ReleaseManifest,
        output: File,
        onProgress: (Progress) -> Unit,
    ) {
        var failure: Throwable? = null
        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                downloadAttempt(manifest, output, onProgress)
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failure = error
                if (attempt + 1 < DOWNLOAD_ATTEMPTS) delay(RETRY_DELAY_MS * (attempt + 1L))
            }
        }
        throw IllegalStateException("No se pudo descargar el cerebro después de $DOWNLOAD_ATTEMPTS intentos", failure)
    }

    private fun downloadAttempt(
        manifest: ReleaseManifest,
        output: File,
        onProgress: (Progress) -> Unit,
    ) {
        output.parentFile?.mkdirs()
        var existing = output.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        if (existing > manifest.archiveBytes) {
            output.delete()
            existing = 0L
        }
        if (existing == manifest.archiveBytes && existing > 0L) return

        val connection = URL(ARCHIVE_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "LEO-Frozen-Brain/$BRAIN_VERSION")
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")

        try {
            connection.connect()
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code al descargar el cerebro" }
            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (existing > 0L && !append) {
                output.delete()
                existing = 0L
            }
            var done = existing
            var nextReport = done
            onProgress(Progress(Progress.State.DOWNLOADING, done, manifest.archiveBytes, "Descargando cerebro congelado"))
            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                FileOutputStream(output, append).buffered(BUFFER_BYTES).use { target ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        done += count
                        require(done <= manifest.archiveBytes) { "El servidor envió más datos de los esperados" }
                        if (done >= nextReport) {
                            onProgress(Progress(Progress.State.DOWNLOADING, done, manifest.archiveBytes, "Descargando cerebro congelado"))
                            nextReport = done + PROGRESS_REPORT_BYTES
                        }
                    }
                }
            }
            require(done == manifest.archiveBytes) { "Descarga truncada ($done/${manifest.archiveBytes})" }
        } finally {
            connection.disconnect()
        }
    }

    private fun unzipSafely(
        archive: File,
        destination: File,
        expectedDatabaseBytes: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val canonicalRoot = destination.canonicalFile
        var extracted = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive), BUFFER_BYTES)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(destination, entry.name).canonicalFile
                require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Entrada ZIP insegura"
                }
                if (entry.isDirectory) {
                    require(target.mkdirs() || target.isDirectory)
                } else {
                    target.parentFile?.let { require(it.mkdirs() || it.isDirectory) }
                    FileOutputStream(target).buffered(BUFFER_BYTES).use { out ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            extracted += count
                            require(extracted <= LeoBrainStorage.FROZEN_MAX_BYTES + EXTRACTION_OVERHEAD_BYTES) {
                                "Paquete cerebral excede el límite de extracción"
                            }
                        }
                    }
                }
                zip.closeEntry()
                onProgress(Progress(Progress.State.INSTALLING, minOf(extracted, expectedDatabaseBytes), expectedDatabaseBytes, "Instalando cerebro congelado"))
            }
        }
    }

    private fun setFrozenReadOnly(directory: File) {
        directory.walkTopDown().filter { it.isFile }.forEach { file ->
            runCatching { file.setWritable(false, false) }
        }
    }

    private fun makeWritable(directory: File) {
        if (!directory.exists()) return
        directory.walkTopDown().forEach { file -> runCatching { file.setWritable(true, true) } }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(BUFFER_BYTES).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val BRAIN_VERSION = "leo-brain-v1"
        const val DATABASE_NAME = "brain.sqlite"
        const val INTERNAL_MANIFEST = "brain-manifest.json"
        const val ATTRIBUTION_FILE = "ATTRIBUTION.txt"
        const val ARCHIVE_NAME = "leo-brain-v1.zip"
        const val EXTERNAL_MANIFEST_NAME = "leo-brain-v1-manifest.json"

        private const val RELEASE_BASE = "https://github.com/victorpavon2908-gif/EDDY/releases/download/$BRAIN_VERSION"
        const val MANIFEST_URL = "$RELEASE_BASE/$EXTERNAL_MANIFEST_NAME"
        const val ARCHIVE_URL = "$RELEASE_BASE/$ARCHIVE_NAME"

        private const val VALIDATED_MARKER = ".validated"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val DOWNLOAD_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 1_000L
        private const val BUFFER_BYTES = 128 * 1024
        private const val PROGRESS_REPORT_BYTES = 4L * 1024 * 1024
        private const val EXTRACTION_OVERHEAD_BYTES = 5_000_000L

        internal fun parseManifest(raw: String): ReleaseManifest {
            val json = JSONObject(raw)
            require(json.optInt("schema") == 1)
            return ReleaseManifest(
                version = json.getString("version"),
                archiveName = json.getString("archive_name"),
                archiveBytes = json.getLong("archive_bytes"),
                archiveSha256 = json.getString("archive_sha256").lowercase(),
                installedBytes = json.getLong("installed_bytes"),
                installedSha256 = json.getString("installed_sha256").lowercase(),
            )
        }

        private fun isSha256(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{64}"))
    }
}
