package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchCitationPolicyTest {
    private fun reply(text: String) = NikoAiReply(text, true, listOf(NikoWebSource("Fuente", "https://example.org/article")))
    @Test fun acceptsOnlyRecoveredIndex() { assertTrue(ResearchCitationPolicy.accepts(reply("Dato [1]"))) }
    @Test fun rejectsInventedCitation() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato [2]"))) }
    @Test fun rejectsUncitedCloudResearch() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato sin referencia"))) }
    @Test fun rejectsInventedUrl() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato [1] https://inventado.org"))) }
    @Test fun acceptsRecoveredUrl() { assertTrue(ResearchCitationPolicy.accepts(reply("Dato [1] https://example.org/article"))) }
    @Test fun noSourcesIsNotEvidence() { assertFalse(ResearchCitationPolicy.accepts(NikoAiReply("Dato [1]", true, emptyList()))) }
}
