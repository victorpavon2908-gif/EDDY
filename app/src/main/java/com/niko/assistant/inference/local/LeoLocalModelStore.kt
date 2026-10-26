package com.niko.assistant.inference.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.niko.assistant.localai.LeoBrainStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Catalog is pinned in the signed APK; the server cannot replace model metadata or code. */
class LeoLocalModelStore(context: Context, requestedModel: LeoLocalModelSpec? = null) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("leo_local_generation", Context.MODE_PRIVATE)
    private val storage = LeoBrainStorage(app)
    private val root = storage.generative
    val spec = requestedModel ?: LeoLocalModelCatalog.find(prefs.getString("model_id", null)) ?: LeoLocalModelCatalog.compact
    private val bytes get() = spec.bytes
    private val hash get() = spec.sha256
    val modelFile = File(root, "$hash.gguf")
    private val marker = File(root, "$hash.validated")
    val installed get() = modelFile.isFile && modelFile.length() == bytes && marker.isFile
    val enabled get() = prefs.getBoolean("enabled", false) && installed &&
        prefs.getString("model_id", LeoLocalModelCatalog.compact.id) == spec.id
    fun snapshot(): String? = if (enabled) prefs.getString("revision", "initial") else null
    fun isCurrent(revision: String?): Boolean = revision != null && snapshot() == revision
    fun disable() {
        check(prefs.edit().putBoolean("enabled", false).putString("revision", java.util.UUID.randomUUID().toString()).commit())
    }
    suspend fun removeDownload() = installLock.withLock {
        withContext(Dispatchers.IO) {
            if (prefs.getString("model_id", LeoLocalModelCatalog.compact.id) == spec.id) disable()
            for (file in listOf(marker, modelFile, File(root, "$hash.part"))) {
                check(!file.exists() || file.delete()) { "No pude eliminar todo el modelo. Está desactivado; intentá nuevamente." }
            }
        }
    }
    fun compatible(): Boolean {
        val info = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && info.totalMem >= spec.minimumRam && !info.lowMemory
    }
    suspend fun recoverIfVerified(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.isFile || modelFile.length() != bytes) return@withContext false
        if (!marker.isFile) {
            marker.writeText("${spec.id}\n$hash\n")
        }
        if (!prefs.getBoolean("enabled", false)) {
            prefs.edit()
                .putString("model_id", spec.id)
                .putBoolean("enabled", true)
                .putString("revision", java.util.UUID.randomUUID().toString())
                .commit()
        }
        true
    }
    suspend fun install(progress: suspend (String) -> Unit) = installLock.withLock {
        withContext(Dispatchers.IO) {
            check(compatible()) { "${spec.label}. Requiere dispositivo de 64 bits y al menos ${spec.minimumRam / 1_000_000_000} GB de RAM total" }
            if (!modelFile.exists()) {
                val part = File(root, "$hash.part")
                if (part.length() > bytes) part.delete()
                val remaining = bytes - part.length()
                check(root.usableSpace >= remaining + LeoBrainStorage.MIN_DEVICE_FREE_BYTES) { "No hay espacio libre suficiente" }
                check(storage.usage().totalBytes + remaining <= LeoBrainStorage.TOTAL_MAX_BYTES) { "La descarga excede el límite de almacenamiento de LEO" }
                if (part.length() < bytes) download(part, progress)
                progress("Verificando integridad del modelo completo…")
                val job = currentCoroutineContext()
                try { LeoModelIntegrity.verify(part, bytes, hash) { job.ensureActive() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { part.delete(); throw failure }
                check(part.renameTo(modelFile)) { "No se pudo instalar el modelo" }
            } else {
                val job = currentCoroutineContext()
                try { LeoModelIntegrity.verify(modelFile, bytes, hash) { job.ensureActive() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (prefs.getString("model_id", LeoLocalModelCatalog.compact.id) == spec.id) disable()
                    marker.delete(); modelFile.delete()
                    throw IllegalStateException("Archivo dañado eliminado. Volvé a descargar; el cerebro anterior sigue disponible.", failure)
                }
            }
            progress("Activando el modelo verificado en este teléfono…")
            currentCoroutineContext().ensureActive()
            marker.writeText("${spec.id}\n$hash\n")
            currentCoroutineContext().ensureActive()
            check(prefs.edit().putString("model_id", spec.id).putBoolean("enabled", true).putString("revision", java.util.UUID.randomUUID().toString()).commit()) { "No se pudo activar el modelo" }
            if (!currentCoroutineContext().isActive) { disable(); currentCoroutineContext().ensureActive() }
            runCatching {
                withTimeoutOrNull(6_000L) {
                    LeoLocalInferenceClient(app).reply("¿Cuánto es 2 + 2? Respondé solamente el número.", validating = true, model = spec)
                }
            }
            progress("Modelo local activado (${spec.id}) y verificado con SHA-256.")
        }
    }
    private suspend fun download(part: File, progress: suspend (String) -> Unit) {
        var address = URL(spec.url)
        var connection: HttpURLConnection? = null
        try {
            for (redirect in 0..5) {
                currentCoroutineContext().ensureActive()
                check(address.protocol == "https")
                val c = address.openConnection() as HttpURLConnection
                connection = c
                c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Accept-Encoding", "identity")
                if (part.length() > 0) c.setRequestProperty("Range", "bytes=${part.length()}-")
                if (c.responseCode in listOf(301, 302, 303, 307, 308)) {
                    val next = URL(address, c.getHeaderField("Location") ?: error("Redirección sin destino"))
                    check(redirect < 5 && next.protocol == "https")
                    c.disconnect(); address = next; continue
                }
                val start = LeoModelIntegrity.resumedOffset(c.responseCode, c.getHeaderField("Content-Range"), part.length(), bytes)
                var done = start
                c.inputStream.use { input ->
                    FileOutputStream(part, start > 0).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var reported = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(done + count <= bytes) { "Descarga mayor que el modelo autorizado" }
                            output.write(buffer, 0, count); done += count
                            val percent = (done * 100 / bytes).toInt()
                            if (percent != reported) { reported = percent; progress("Descargando modelo local: $percent % · podés cancelar y reanudar") }
                        }
                        output.fd.sync()
                    }
                }
                check(done == bytes) { "Descarga interrumpida; podés reanudarla" }
                return
            }
            error("Demasiadas redirecciones")
        } finally { connection?.disconnect() }
    }
    companion object {
        private val installLock = Mutex()
        const val VERSION = "Qwen3-1.7B-Q8_0-90862c4"
        const val BYTES = 1_834_426_016L
        const val SHA256 = "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a"
        const val URL_PINNED = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/90862c4b9d2787eaed51d12237eafdfe7c5f6077/Qwen3-1.7B-Q8_0.gguf"
    }
}
