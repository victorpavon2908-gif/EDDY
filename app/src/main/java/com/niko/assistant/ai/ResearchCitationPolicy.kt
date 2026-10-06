package com.niko.assistant.ai

/** Syntactic provenance check only: source membership does not prove a claim's truth. */
object ResearchCitationPolicy {
    fun accepts(reply: NikoAiReply): Boolean {
        if (!reply.webUsed || reply.sources.isEmpty()) return false
        val citations = Regex("\\[(\\d+)\\]").findAll(reply.text).map { it.groupValues[1].toIntOrNull() }.toList()
        if (citations.isEmpty() || citations.any { it == null || it !in 1..reply.sources.size }) return false
        val links = Regex("https?://[^\\s<>\\)\\]]+").findAll(reply.text).map { it.value.trimEnd('.', ',', ';') }
        return links.all { url -> reply.sources.any { it.url == url } }
    }
}
