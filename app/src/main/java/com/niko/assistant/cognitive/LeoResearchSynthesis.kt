package com.niko.assistant.cognitive

import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoRetrievedPassage
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Citation membership and quotation validation, not a proof of logical entailment or truth. */
object LeoResearchSynthesis {
    private fun selected(reply: NikoAiReply): List<NikoRetrievedPassage> = reply.passages
        .filter { it.pageRead && it.text.length >= 24 && it.sourceIndex in 1..reply.sources.size }
        .distinctBy { it.sourceIndex }.filter { passage ->
            val uri = runCatching { java.net.URI(reply.sources[passage.sourceIndex - 1].url) }.getOrNull()
            uri?.scheme in setOf("http", "https") && !uri?.host.isNullOrBlank() && uri?.rawUserInfo == null
        }.take(6).map { it.copy(text = it.text.take(1200)) }

    fun evidence(reply: NikoAiReply): String? {
        if (!reply.webUsed) return null
        val passages = selected(reply)
        if (passages.isEmpty()) return null
        return JSONArray().apply { passages.forEach { passage ->
            put(JSONObject().put("source", passage.sourceIndex).put("text", passage.text))
        } }.toString().takeIf { it.length <= 10000 }
    }

    suspend fun synthesize(original: NikoAiReply, generate: suspend (String) -> String?): NikoAiReply {
        val input = evidence(original) ?: return original
        return try {
            val raw = generate(input) ?: return original
            validate(raw, original) ?: original
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { original }
    }

    fun validate(raw: String, original: NikoAiReply): NikoAiReply? = runCatching {
        require(original.webUsed && raw.length <= 16000)
        val passages = selected(original).associateBy { it.sourceIndex }
        val claims = JSONObject(raw).getJSONArray("claims")
        require(claims.length() in 1..6)
        val groups = linkedMapOf("reported" to mutableListOf<String>(), "inference" to mutableListOf(), "uncertain" to mutableListOf())
        for (index in 0 until claims.length()) {
            val claim = claims.getJSONObject(index)
            val kind = claim.getString("kind")
            val text = claim.getString("text").trim()
            require(kind in groups && text.length in 1..600)
            require(!Regex("https?://|www\\.|\\[\\d+\\]", RegexOption.IGNORE_CASE).containsMatchIn(text))
            val refs = claim.getJSONArray("refs")
            require(refs.length() in 1..6)
            val ids = mutableSetOf<Int>()
            for (r in 0 until refs.length()) {
                val ref = refs.getJSONObject(r)
                val id = ref.get("source") as? Int ?: error("Invalid source identifier")
                require(ids.add(id))
                val quote = normalize(ref.getString("quote"))
                require(quote.length in 24..1200)
                require(normalize(passages[id]?.text ?: error("Unknown source")).contains(quote))
            }
            groups.getValue(kind).add(text + " " + ids.joinToString(" ") { "[$it]" })
        }
        val labels = mapOf("reported" to "Información reportada", "inference" to "Inferencias", "uncertain" to "Información incierta")
        val answer = groups.filterValues { it.isNotEmpty() }.entries.joinToString("\n\n") { (kind, lines) ->
            labels.getValue(kind) + ":\n" + lines.distinct().joinToString("\n")
        }
        val referencedIds = Regex("\\[(\\d+)\\]").findAll(answer).map { it.groupValues[1].toInt() }.toSet()
        val domains = referencedIds.mapNotNull { id ->
            runCatching { java.net.URI(original.sources[id - 1].url).host.lowercase().removePrefix("www.") }.getOrNull()
        }.toSet()
        val scope = if (domains.size < 2) "\n\nLa síntesis depende de un solo dominio; no hay corroboración independiente." else ""
        original.copy(text = answer + scope, spokenText = null,
            evidence = original.evidence + " Síntesis generada: referencias y citas contrastadas con extractos, sin certificación automática de veracidad.")
    }.getOrNull()

    private fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()
}
