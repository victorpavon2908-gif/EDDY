package com.niko.assistant.ai

/** Syntactic provenance check only: source membership does not prove a claim's truth. */
object ResearchCitationPolicy {
    /** Rebase per-search citation numbers before combining independent tool results. */
    fun combine(replies: List<NikoAiReply>): NikoAiReply {
        require(replies.size <= 8)
        val sources = replies.filter { it.webUsed }.flatMap { it.sources }.distinctBy { it.url }
        require(sources.size <= 64)
        val text = replies.joinToString(" ") { reply ->
            if (!reply.webUsed) reply.text else Regex("\\[(\\d+)\\]").replace(reply.text) { match ->
                val localIndex = match.groupValues[1].toIntOrNull()?.minus(1)
                val source = localIndex?.let { reply.sources.getOrNull(it) }
                if (source == null) "[referencia no válida]"
                else "[${sources.indexOfFirst { it.url == source.url } + 1}]"
            }
        }
        return NikoAiReply(text, sources.isNotEmpty(), sources.mapIndexed { index, source ->
            source.copy(title = "[${index + 1}] " + source.title.replace(Regex("^\\[\\d+\\]\\s*"), ""))
        })
    }

    fun accepts(reply: NikoAiReply): Boolean {
        if (!reply.webUsed || reply.sources.isEmpty()) return false
        val citations = Regex("\\[(\\d+)\\]").findAll(reply.text).map { it.groupValues[1].toIntOrNull() }.toList()
        if (citations.isEmpty() || citations.any { it == null || it !in 1..reply.sources.size }) return false
        val links = Regex("https?://[^\\s<>\\)\\]]+").findAll(reply.text).map { it.value.trimEnd('.', ',', ';') }
        return links.all { url -> reply.sources.any { it.url == url } }
    }
}
