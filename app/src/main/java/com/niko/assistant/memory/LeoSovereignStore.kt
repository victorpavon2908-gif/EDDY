package com.niko.assistant.memory

import com.niko.assistant.cognitive.LeoEddyPersona
import com.niko.assistant.learning.LeoAutodidactEngine
import java.io.File
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
    ) {
        val payload = listOf(
            b64(autodidact.exportJson()),
            b64(rag.exportJson()),
            b64(timeline.exportSerialized()),
            b64(persona.exportState()),
        ).joinToString("|")

        memorySnapshot = payload
        val dir = storageDir ?: return
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(payload, StandardCharsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(payload, StandardCharsets.UTF_8)
                tmp.delete()
            }
        }
    }

    @Synchronized
    fun restoreAll(
        autodidact: LeoAutodidactEngine,
        rag: LeoLocalRagEngine,
        timeline: LeoEpisodicTimeline,
        persona: LeoEddyPersona,
    ): Boolean {
        val raw = runCatching {
            val file = storageDir?.let { File(it, FILE_NAME) }
            if (file != null && file.exists() && file.isFile) {
                file.readText(StandardCharsets.UTF_8).trim()
            } else {
                memorySnapshot
            }
        }.getOrDefault(memorySnapshot)

        if (raw.isBlank()) return false
        val parts = raw.split('|')
        if (parts.size < 4) return false

        return runCatching {
            autodidact.importJson(unb64(parts[0]))
            rag.importJson(unb64(parts[1]))
            timeline.importSerialized(unb64(parts[2]))
            persona.importState(unb64(parts[3]))
            true
        }.getOrDefault(false)
    }

    private fun b64(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    companion object {
        private const val FILE_NAME = "leo_sovereign_state.dat"
    }
}
