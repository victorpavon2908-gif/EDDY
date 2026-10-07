package com.niko.assistant.memory.embedding

import android.content.Context
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Explicit installation; no conversations are uploaded. A batch always uses one vector space. */
class LeoSemanticMemory private constructor(context: Context) {
    private val root = File(context.noBackupFilesDir, "leo-semantic-${LeoEmbeddingModel.REVISION}")
    private val installMutex = Mutex()
    private var encoder: LeoSemanticEncoder? = null
    private var unavailable = false
    private val maintenance = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var idleRelease: kotlinx.coroutines.Job? = null
    private var lastUsed = 0L
    @Volatile var status: String = "Memoria por coincidencias · modelo semántico sin preparar"
        private set

    fun installed(): Boolean = File(root, "ready").isFile && LeoEmbeddingModel.artifacts.all {
        File(root, it.name).length() == it.bytes
    }

    suspend fun install(progress: (Long, Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        installMutex.withLock {
            if (!LeoEmbeddingModel.verify(root)) {
                val staging = File(root.parentFile, root.name + ".installing")
                staging.mkdirs()
                val total = LeoEmbeddingModel.artifacts.sumOf { it.bytes }
                var completed = 0L
                for (artifact in LeoEmbeddingModel.artifacts) {
                    currentCoroutineContext().ensureActive()
                    if (!artifact.valid(staging)) {
                        val part = File(staging, artifact.name + ".part")
                        val connection = URL(artifact.url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 20_000
                        connection.readTimeout = 30_000
                        try {
                            require(connection.responseCode == 200) { "No pude descargar los pesos (${connection.responseCode})" }
                            require(connection.url.protocol == "https")
                            connection.inputStream.buffered().use { input ->
                                part.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var count = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        count += size
                                        require(count <= artifact.bytes) { "Tamaño de modelo inesperado" }
                                        output.write(buffer, 0, size)
                                        progress(completed + count, total)
                                    }
                                }
                            }
                            require(part.renameTo(File(staging, artifact.name)))
                            require(artifact.valid(staging)) { "Falló la verificación SHA-256" }
                        } finally { connection.disconnect(); part.delete() }
                    }
                    completed += artifact.bytes
                }
                require(LeoEmbeddingModel.verify(staging))
                File(staging, "ready").writeText(LeoEmbeddingModel.REVISION)
                synchronized(this@LeoSemanticMemory) {
                    encoder?.close(); encoder = null
                    if (root.exists()) require(root.deleteRecursively())
                    require(staging.renameTo(root)) { "No se pudo activar la memoria semántica" }
                }
            } else File(root, "ready").writeText(LeoEmbeddingModel.REVISION)
            synchronized(this@LeoSemanticMemory) { unavailable = false }
            // Exercise the installed model before reporting it ready.
            val probe = encodeTrained(listOf("Memoria personal de LEO"))
            check(probe != null) { status }
        }
    }

    @Synchronized fun encodeTrained(texts: List<String>): List<FloatArray>? {
        // Never load native weights on the UI/audio thread.
        if (Looper.myLooper() == Looper.getMainLooper() || unavailable || !installed()) return null
        return try {
            val active = encoder ?: LeoSemanticEncoder(root).also { encoder = it }
            texts.map(active::encode).also {
                status = "Memoria semántica local · DistilUSE INT8 · 512 dimensiones"
                lastUsed = System.nanoTime()
                idleRelease?.cancel()
                idleRelease = maintenance.launch {
                    kotlinx.coroutines.delay(120_000L)
                    synchronized(this@LeoSemanticMemory) {
                        if (System.nanoTime() - lastUsed >= 120_000_000_000L) release()
                    }
                }
            }
        } catch (error: Exception) {
            encoder?.close(); encoder = null; unavailable = true
            status = "Memoria por coincidencias · no se pudo abrir el modelo semántico"
            null
        } catch (error: LinkageError) {
            unavailable = true
            status = "Memoria por coincidencias · runtime semántico no disponible"
            null
        }
    }

    @Synchronized fun release() { encoder?.close(); encoder = null }

    companion object {
        @Volatile private var instance: LeoSemanticMemory? = null
        fun get(context: Context): LeoSemanticMemory = instance ?: synchronized(this) {
            instance ?: LeoSemanticMemory(context.applicationContext).also { instance = it }
        }
    }
}
