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
class LeoLocalModelStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("leo_local_generation", Context.MODE_PRIVATE)
    private val storage = LeoBrainStorage(app)
    private val root = storage.generative
    val modelFile = File(root, "$SHA256.gguf")
    private val marker = File(root, "$SHA256.validated")
    val installed get() = modelFile.isFile && modelFile.length() == BYTES && marker.isFile
    val enabled get() = prefs.getBoolean("enabled", false) && installed
    fun disable() { check(prefs.edit().putBoolean("enabled", false).commit()) }
    fun compatible(): Boolean {
        val info = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() && info.totalMem >= 5_000_000_000L && !info.lowMemory
    }
    suspend fun install(progress: suspend (String) -> Unit) = installLock.withLock {
        withContext(Dispatchers.IO) {
            check(compatible()) { "Requiere Android de 64 bits y al menos 5 GB de RAM física" }
            if (!modelFile.exists()) {
                val part = File(root, "$SHA256.part")
                if (part.length() > BYTES) part.delete()
                val remaining = BYTES - part.length()
                check(root.usableSpace >= remaining + LeoBrainStorage.MIN_DEVICE_FREE_BYTES) { "No hay espacio libre suficiente" }
                check(storage.usage().totalBytes + remaining <= LeoBrainStorage.TOTAL_MAX_BYTES) { "La descarga excede el límite de almacenamiento de LEO" }
                if (part.length() < BYTES) download(part, progress)
                progress("Verificando los 1,83 GB descargados…")
                val job = currentCoroutineContext()
                try { LeoModelIntegrity.verify(part, BYTES, SHA256) { job.ensureActive() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { part.delete(); throw failure }
                check(part.renameTo(modelFile)) { "No se pudo instalar el modelo" }
            } else {
                val job = currentCoroutineContext()
                LeoModelIntegrity.verify(modelFile, BYTES, SHA256) { job.ensureActive() }
            }
            progress("Probando el modelo en este teléfono; todavía no está activado…")
            val client = LeoLocalInferenceClient(app)
            val arithmetic = client.reply("¿Cuánto es 2 + 2? Respondé solamente el número.", validating = true)
            check(arithmetic?.trim()?.trimEnd('.') == "4") { "El modelo no superó la prueba de respuesta; no fue activado" }
            marker.writeText("$VERSION\n$SHA256\n")
            check(prefs.edit().putBoolean("enabled", true).commit()) { "No se pudo activar el modelo" }
            progress("Modelo local activado. Prueba básica superada; no equivale a una evaluación general de calidad.")
        }
    }
    private suspend fun download(part: File, progress: suspend (String) -> Unit) {
        var address = URL(URL_PINNED)
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
                val start = LeoModelIntegrity.resumedOffset(c.responseCode, c.getHeaderField("Content-Range"), part.length(), BYTES)
                var done = start
                c.inputStream.use { input ->
                    FileOutputStream(part, start > 0).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var reported = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(done + count <= BYTES) { "Descarga mayor que el modelo autorizado" }
                            output.write(buffer, 0, count); done += count
                            val percent = (done * 100 / BYTES).toInt()
                            if (percent != reported) { reported = percent; progress("Descargando modelo local: $percent % · podés cancelar y reanudar") }
                        }
                        output.fd.sync()
                    }
                }
                check(done == BYTES) { "Descarga interrumpida; podés reanudarla" }
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
