package com.niko.assistant.memory

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Memoria Episódica con Línea de Tiempo Autobiográfica y Destilador Cognitivo Continuo.
 *
 * Permite que LEO recuerde en qué estuviste trabajando, qué protocolos activaste y qué
 * investigaciones o documentos se destilaron como conocimiento permanente para uso offline.
 */
class LeoEpisodicTimeline(
    private val ragEngine: LeoLocalRagEngine? = null,
    private val maxEpisodes: Int = 120,
) {
    enum class EpisodeKind(val label: String) {
        CONVERSATION("Conversación"),
        RESEARCH_DISTILLED("Investigación destilada"),
        PROTOCOL_EXECUTED("Protocolo ejecutado"),
        DOCUMENT_READ("Documento indexado"),
        PROJECT_MILESTONE("Hito de proyecto"),
    }

    data class Episode(
        val id: String,
        val timestampMs: Long,
        val kind: EpisodeKind,
        val topic: String,
        val summary: String,
    )

    private val episodes = ArrayDeque<Episode>()
    var distilledFactsCount: Int = 0
        private set

    @Synchronized
    fun recordEpisode(
        kind: EpisodeKind,
        topic: String,
        summary: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): Episode {
        val cleanTopic = topic.trim().ifBlank { "general" }.take(90)
        val cleanSummary = summary.replace(Regex("\\s+"), " ").trim().take(320)
        val ep = Episode(
            id = "ep_${timestampMs}_${episodes.size}",
            timestampMs = timestampMs,
            kind = kind,
            topic = cleanTopic,
            summary = cleanSummary,
        )
        if (episodes.size >= maxEpisodes) episodes.removeFirst()
        episodes.addLast(ep)
        return ep
    }

    /**
     * Destila automáticamente el resultado de una investigación web o lectura de documento
     * y lo convierte en una ficha permanente dentro de `LeoLocalRagEngine` y la línea de tiempo.
     */
    @Synchronized
    fun distillKnowledge(
        topic: String,
        factSummary: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val cleanTopic = topic.trim()
        val cleanSummary = factSummary.replace(Regex("\\s+"), " ").trim()
        if (cleanTopic.length < 2 || cleanSummary.length < 15) return false
        distilledFactsCount++
        recordEpisode(
            kind = EpisodeKind.RESEARCH_DISTILLED,
            topic = cleanTopic,
            summary = cleanSummary,
            timestampMs = timestampMs,
        )
        ragEngine?.ingestDocument(
            docId = "distilled_${MemoryLearning.key(cleanTopic).replace(' ', '_')}",
            title = "Ficha Destilada: $cleanTopic",
            content = cleanSummary,
            modality = LeoLocalRagEngine.Modality.MARKDOWN,
        )
        return true
    }

    @Synchronized
    fun handleCommand(input: String): String? {
        val key = MemoryLearning.key(input).removePrefix("leo ").trim()
        val isTimelineQuery = key in setOf(
            "que hicimos hoy",
            "en que quedamos",
            "en que estabamos trabajando",
            "sobre que hablamos antes",
            "resumen de mi actividad",
            "mostrame mi linea de tiempo",
            "que aprendiste hoy",
        )
        if (!isTimelineQuery) return null

        if (episodes.isEmpty()) {
            return "Todavía no tengo episodios registrados en esta sesión. A medida que conversemos, ejecutemos protocolos o investiguemos temas, los recordaré en tu línea de tiempo."
        }
        val recent = episodes.toList().takeLast(4).reversed()
        val items = recent.mapIndexed { idx, ep ->
            "${idx + 1}. [${ep.kind.label}] ${ep.topic}: ${ep.summary}"
        }.joinToString("\n")
        return "Esto es lo más reciente en tu línea de tiempo ($distilledFactsCount fichas destiladas en memoria offline):\n$items"
    }

    @Synchronized
    fun recentEpisodes(limit: Int = 10): List<Episode> =
        episodes.toList().takeLast(limit).reversed()

    @Synchronized
    fun exportSerialized(): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        fun b64(s: String): String = enc.encodeToString(s.toByteArray(StandardCharsets.UTF_8))
        val items = episodes.joinToString(";") { ep ->
            listOf(b64(ep.id), ep.timestampMs.toString(), b64(ep.kind.name), b64(ep.topic), b64(ep.summary)).joinToString(":")
        }
        return "LEOTIME1|$distilledFactsCount|$items"
    }

    @Synchronized
    fun importSerialized(raw: String) {
        val clean = raw.trim()
        if (!clean.startsWith("LEOTIME1|")) return
        val dec = Base64.getUrlDecoder()
        fun unb64(s: String): String = if (s.isEmpty()) "" else String(dec.decode(s), StandardCharsets.UTF_8)
        val parts = clean.split('|', limit = 3)
        if (parts.size < 3) return
        distilledFactsCount = parts[1].toIntOrNull() ?: distilledFactsCount
        if (parts[2].isBlank()) return
        for (entry in parts[2].split(';')) {
            val fields = entry.split(':', limit = 5)
            if (fields.size == 5) {
                val id = unb64(fields[0])
                val ts = fields[1].toLongOrNull() ?: 0L
                val kind = runCatching { EpisodeKind.valueOf(unb64(fields[2])) }.getOrDefault(EpisodeKind.CONVERSATION)
                val topic = unb64(fields[3])
                val summary = unb64(fields[4])
                if (episodes.size >= maxEpisodes) episodes.removeFirst()
                episodes.addLast(Episode(id, ts, kind, topic, summary))
            }
        }
    }
}
