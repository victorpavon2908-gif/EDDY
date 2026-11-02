package com.niko.assistant.memory

import com.niko.assistant.cognitive.LeoEddyPersona
import com.niko.assistant.cognitive.LeoLivingConsciousness
import com.niko.assistant.learning.LeoAutodidactEngine
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Puente de Persistencia Automática en Disco para los Motores Soberanos de LEO (`LeoSovereignStore`).
 *
 * Guarda y restaura de forma atómica el estado de:
 * - [LeoAutodidactEngine] (reglas aprendidas del usuario y contadores de éxito)
 * - [LeoLocalRagEngine] (documentos locales, PDFs y OCR vectorizados)
 * - [LeoEpisodicTimeline] (episodios y línea de tiempo autobiográfica)
 * - [LeoEddyPersona] (modo de personalidad activo: Eddy, Misión o Enfoque)
 */
class LeoSovereignStore(
    private val storageDir: File? = null,
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private var memorySnapshot: String = ""

    @Synchronized
    fun saveAll(
        autodidact: LeoAutodidactEngine,
        rag: LeoLocalRagEngine,
        timeline: LeoEpisodicTimeline,
        persona: LeoEddyPersona,
        consciousness: LeoLivingConsciousness? = null,
    ) {
        val payload = listOf(
            b64(autodidact.exportJson()),
            b64(rag.exportJson()),
            b64(timeline.exportSerialized()),
            b64(persona.exportState()),
            b64(consciousness?.exportState().orEmpty()),
        ).joinToString("|")

        val dir = storageDir
        if (dir != null) {
            check(dir.isDirectory || dir.mkdirs()) { "No pude crear el almacenamiento del núcleo." }
            val target = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            try {
                FileOutputStream(tmp).use { output ->
                    output.write(payload.toByteArray(StandardCharsets.UTF_8))
                    output.fd.sync()
                }
                try { Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { tmp.delete() }
        }
        memorySnapshot = if (dir == null) payload else ""
    }

    @Synchronized
    fun restoreAll(
        autodidact: LeoAutodidactEngine,
        rag: LeoLocalRagEngine,
        timeline: LeoEpisodicTimeline,
        persona: LeoEddyPersona,
        consciousness: LeoLivingConsciousness? = null,
    ): Boolean {
        val file = storageDir?.let { File(it, FILE_NAME) }
        val raw = if (file?.exists() == true) {
            check(file.isFile) { "El estado del núcleo no es un archivo legible." }
            file.readText(StandardCharsets.UTF_8).trim().also { check(it.isNotBlank()) { "El estado guardado está vacío." } }
        } else if (storageDir == null) memorySnapshot else ""

        if (raw.isBlank()) return false
        val parts = raw.split('|')
        check(parts.size in 4..5) { "Formato de estado del núcleo inválido." }
        val decoded = parts.map(::unb64)
        check(decoded[0].startsWith("LEOAUTO1|") && decoded[1].startsWith("LEORAG1|") &&
            decoded[2].startsWith("LEOTIME1|") && LeoEddyPersona.PersonaMode.entries.any { it.name == decoded[3] } &&
            (decoded.size == 4 || decoded[4].isEmpty() || decoded[4].startsWith("LEOCONSC1|"))) {
            "El estado del núcleo contiene componentes inválidos."
        }
        return runCatching {
            autodidact.importJson(decoded[0])
            rag.importJson(decoded[1])
            timeline.importSerialized(decoded[2])
            persona.importState(decoded[3])
            if (parts.size >= 5 && parts[4].isNotBlank()) {
                consciousness?.importState(decoded[4])
            }
            true
        }.getOrElse { throw IllegalStateException("No pude restaurar el estado del núcleo; se conservó el archivo.", it) }
    }

    @Synchronized
    fun clear() {
        memorySnapshot = ""
        val dir = storageDir ?: return
        listOf(File(dir, FILE_NAME), File(dir, "$FILE_NAME.tmp")).forEach { file ->
            check(!file.exists() || file.delete()) { "No se pudo borrar ${file.name}" }
        }
    }

    private fun b64(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    companion object {
        private const val FILE_NAME = "leo_sovereign_state.dat"
    }
}
