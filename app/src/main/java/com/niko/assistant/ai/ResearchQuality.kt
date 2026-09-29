package com.niko.assistant.ai

/** Chooses the strongest independently sourced result without mixing uncited claims. */
internal object ResearchQuality {
    fun choose(native: NikoAiReply, compound: NikoAiReply?): NikoAiReply {
        val assisted = compound?.takeIf { it.webUsed && it.sources.isNotEmpty() } ?: return native
        if (!native.webUsed || native.sources.isEmpty()) return assisted

        fun score(reply: NikoAiReply): Int {
            val publishers = AutonomousResearch.publisherCount(reply.sources.map { it.url })
            val depth = (reply.text.length / 220).coerceAtMost(10)
            val citations = reply.sources.size.coerceAtMost(6)
            return publishers * 12 + depth + citations
        }

        return if (score(assisted) > score(native)) assisted else native
    }
}
