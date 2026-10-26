package com.niko.assistant.memory

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.ln

/**
 * Motor RAG Multimodal Local con Índice Vectorial Híbrido (100% soberano en dispositivo).
 *
 * Indexa texto extraído de PDFs, imágenes/fotos (OCR), notas de voz y apuntes locales
 * en fragmentos semánticos, combinando vectores densos L2 (`EddyTransformerEmbedder`)
 * con ranking léxico BM25 para responder consultas sobre archivos del usuario sin subir
 * datos a ningún servidor ni depender de modelos externos.
 */
class LeoLocalRagEngine(
    private val embedder: EddyTransformerEmbedder = EddyTransformerEmbedder.INSTANCE,
) {
    enum class Modality(val label: String) {
        PDF_DOCUMENT("PDF"),
        PHOTO_OCR("Foto/OCR"),
        VOICE_NOTE("Nota de voz"),
        USER_NOTE("Nota"),
        MARKDOWN("Documento"),
    }

    data class DocumentChunk(
        val docId: String,
        val title: String,
        val modality: Modality,
        val chunkIndex: Int,
        val text: String,
        val tokens: Set<String>,
        val vector: FloatArray,
    )

    data class RetrievedChunk(
        val chunk: DocumentChunk,
        val score: Double,
        val vectorSimilarity: Double,
        val lexicalScore: Double,
    )

    private val chunks = mutableListOf<DocumentChunk>()
    private val docFreq = mutableMapOf<String, Int>()

    @Synchronized
    fun documentCount(): Int = chunks.map { it.docId }.distinct().size

    @Synchronized
    fun chunkCount(): Int = chunks.size

    /**
     * Indexa un documento o contenido multimodal dividiéndolo en fragmentos semánticos.
     */
    @Synchronized
    fun ingestDocument(
        docId: String,
        title: String,
        content: String,
        modality: Modality = Modality.MARKDOWN,
    ): Int {
        val cleanTitle = title.trim().ifBlank { docId }
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return 0

        removeDocument(docId)
        val rawParts = splitIntoChunks(cleanContent)
        var added = 0
        rawParts.forEachIndexed { idx, part ->
            val tokens = tokenize(part)
            if (tokens.isNotEmpty()) {
                val vector = embedder.encode("$cleanTitle $part")
                val docChunk = DocumentChunk(
                    docId = docId,
                    title = cleanTitle,
                    modality = modality,
                    chunkIndex = idx,
                    text = part,
                    tokens = tokens,
                    vector = vector,
                )
                chunks.add(docChunk)
                tokens.forEach { tok -> docFreq[tok] = (docFreq[tok] ?: 0) + 1 }
                added++
            }
        }
        return added
    }

    @Synchronized
    fun removeDocument(docId: String): Boolean {
        val removed = chunks.filter { it.docId == docId }
        if (removed.isEmpty()) return false
        chunks.removeAll { it.docId == docId }
        rebuildDocFreq()
        return true
    }

    /**
     * Búsqueda híbrida (Similitud Coseno Transformer + BM25 Léxico).
     */
    @Synchronized
    fun search(query: String, topK: Int = 3): List<RetrievedChunk> {
        if (chunks.isEmpty() || query.isBlank()) return emptyList()
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()
        val queryVec = embedder.encode(query)
        val totalChunks = chunks.size.coerceAtLeast(1)
        val avgLen = chunks.sumOf { it.tokens.size }.toDouble() / totalChunks

        return chunks.mapNotNull { chunk ->
            val overlap = queryTokens.intersect(chunk.tokens)
            val titleTokens = tokenize(chunk.title)
            val titleOverlap = queryTokens.intersect(titleTokens)
            if (overlap.isEmpty() && titleOverlap.isEmpty()) return@mapNotNull null

            val cosine = embedder.cosineSimilarity(queryVec, chunk.vector)
            var bm25 = 0.0
            for (term in overlap) {
                val df = (docFreq[term] ?: 1).toDouble()
                val idf = ln(1.0 + (totalChunks - df + 0.5) / (df + 0.5))
                val k1 = 1.2
                val b = 0.75
                val tf = 1.0
                val norm = tf * (k1 + 1.0) / (tf + k1 * (1.0 - b + b * (chunk.tokens.size / avgLen.coerceAtLeast(1.0))))
                bm25 += idf * norm
            }
            val titleBoost = titleOverlap.size * 0.45
            val lexicalNormalized = ((bm25 / queryTokens.size.coerceAtLeast(1)) + titleBoost).coerceAtMost(2.0)
            val combined = cosine * 0.55 + lexicalNormalized * 0.45
            if (combined < 0.22) null
            else RetrievedChunk(chunk, combined, cosine, lexicalNormalized)
        }.sortedByDescending { it.score }.take(topK)
    }

    /**
     * Genera una respuesta extractiva fundamentada en los documentos locales del usuario.
     */
    @Synchronized
    fun answer(query: String): String? {
        val hits = search(query, topK = 3)
        if (hits.isEmpty()) return null
        val best = hits.take(2)
        return buildString {
            best.forEachIndexed { idx, hit ->
                if (idx > 0) append(" ")
                val snippet = hit.chunk.text.trim().trimEnd('.')
                append("$snippet [${hit.chunk.modality.label}: ${hit.chunk.title}].")
            }
        }
    }

    /**
     * Procesa comandos conversacionales para guardar o consultar documentos locales.
     */
    @Synchronized
    fun handleCommand(input: String): String? {
        val clean = input.trim().replace(Regex("(?i)^(?:leo|niko|nico)[,\\s:;-]*"), "")
        // Ingesta conversacional: "guardá en mis documentos como 'Título': contenido"
        val ingestRegex = Regex("(?is)^(?:guarda|guardá|indexa|indexá|agrega|agregá)\\s+en\\s+(?:mis\\s+)?documentos\\s+(?:como\\s+)?[\"«']?([^\"»':]{2,80})[\"»']?\\s*:\\s*(.+)$")
        ingestRegex.matchEntire(clean)?.let { match ->
            val title = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            val id = MemoryLearning.key(title).replace(' ', '_')
            val count = ingestDocument(id, title, body, Modality.USER_NOTE)
            return if (count > 0) "Indexé «$title» en tu base vectorial local ($count fragmento(s)). Podés consultarlo sin internet."
            else "No pude indexar el documento porque el contenido está vacío."
        }

        // Consulta explícita sobre documentos: "buscá en mis documentos sobre X" / "qué dicen mis documentos de X"
        val searchRegex = Regex("(?is)^(?:busca|buscá|consulta|consultá|revisa|revisá|que dicen mis documentos|qué dicen mis documentos)\\s+(?:en\\s+(?:mis\\s+)?documentos\\s+)?(?:sobre|de|acerca de)?\\s*(.+)$")
        if (MemoryLearning.key(clean).contains("mis documentos")) {
            val q = searchRegex.matchEntire(clean)?.groupValues?.getOrNull(1)?.trim() ?: clean
            return answer(q) ?: "Revisé tus documentos locales indexados, pero no encontré pasajes sobre «$q»."
        }
        return null
    }

    @Synchronized
    fun exportJson(): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        fun b64(s: String): String = enc.encodeToString(s.toByteArray(StandardCharsets.UTF_8))
        val byDoc = chunks.groupBy { it.docId }
        val entries = byDoc.map { (docId, docChunks) ->
            val first = docChunks.first()
            val fullText = docChunks.sortedBy { it.chunkIndex }.joinToString("\n\n") { it.text }
            listOf(b64(docId), b64(first.title), b64(first.modality.name), b64(fullText)).joinToString(":")
        }
        return "LEORAG1|" + entries.joinToString(";")
    }

    @Synchronized
    fun importJson(json: String) {
        val clean = json.trim()
        if (!clean.startsWith("LEORAG1|")) return
        val dec = Base64.getUrlDecoder()
        fun unb64(s: String): String = if (s.isEmpty()) "" else String(dec.decode(s), StandardCharsets.UTF_8)
        val payload = clean.removePrefix("LEORAG1|")
        if (payload.isBlank()) return
        for (item in payload.split(';')) {
            val fields = item.split(':', limit = 4)
            if (fields.size == 4) {
                val docId = unb64(fields[0])
                val title = unb64(fields[1])
                val modality = runCatching { Modality.valueOf(unb64(fields[2])) }.getOrDefault(Modality.MARKDOWN)
                val content = unb64(fields[3])
                if (docId.isNotBlank() && content.isNotBlank()) {
                    ingestDocument(docId, title, content, modality)
                }
            }
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (para in paragraphs) {
            if (para.length <= MAX_CHUNK_CHARS) {
                result.add(para)
            } else {
                val sentences = para.split(Regex("(?<=[.!?])\\s+"))
                val current = StringBuilder()
                for (sentence in sentences) {
                    if (current.length + sentence.length + 1 > MAX_CHUNK_CHARS && current.isNotEmpty()) {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(sentence.take(MAX_CHUNK_CHARS))
                }
                if (current.isNotEmpty()) result.add(current.toString().trim())
            }
        }
        return result.take(MAX_CHUNKS_PER_DOC)
    }

    private fun tokenize(text: String): Set<String> =
        MemoryLearning.key(text)
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    private fun rebuildDocFreq() {
        docFreq.clear()
        for (chunk in chunks) {
            for (token in chunk.tokens) {
                docFreq[token] = (docFreq[token] ?: 0) + 1
            }
        }
    }

    companion object {
        private const val MAX_CHUNK_CHARS = 360
        private const val MAX_CHUNKS_PER_DOC = 64
        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "en", "por", "para", "con", "del", "al",
            "que", "como", "sobre", "mis", "documentos", "documento", "busca", "decime", "dime",
        )
    }
}
