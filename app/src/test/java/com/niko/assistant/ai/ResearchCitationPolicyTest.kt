package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchCitationPolicyTest {
    @Test fun independentSearchCitationsAreRebasedByUrl() {
        val first = NikoAiReply("Primero [1]", true, listOf(NikoWebSource("A", "https://a.org")))
        val second = NikoAiReply("Segundo [1], compartido [2]", true,
            listOf(NikoWebSource("B", "https://b.org"), NikoWebSource("A", "https://a.org")))
        val combined = ResearchCitationPolicy.combine(listOf(first, second))
        assertEquals("Primero [1] Segundo [2], compartido [1]", combined.text)
        assertEquals(2, combined.sources.size)
        assertTrue(ResearchCitationPolicy.accepts(combined))
    }
    @Test fun malformedSourceIndexCannotPointAtAnotherSearch() {
        val combined = ResearchCitationPolicy.combine(listOf(reply("Dato [9]")))
        assertTrue(combined.text.contains("referencia no válida"))
        assertFalse(combined.text.contains("[9]"))
    }
    private fun reply(text: String) = NikoAiReply(text, true, listOf(NikoWebSource("Fuente", "https://example.org/article")))
    @Test fun acceptsOnlyRecoveredIndex() { assertTrue(ResearchCitationPolicy.accepts(reply("Dato [1]"))) }
    @Test fun rejectsInventedCitation() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato [2]"))) }
    @Test fun rejectsUncitedCloudResearch() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato sin referencia"))) }
    @Test fun rejectsInventedUrl() { assertFalse(ResearchCitationPolicy.accepts(reply("Dato [1] https://inventado.org"))) }
    @Test fun acceptsRecoveredUrl() { assertTrue(ResearchCitationPolicy.accepts(reply("Dato [1] https://example.org/article"))) }
    @Test fun noSourcesIsNotEvidence() { assertFalse(ResearchCitationPolicy.accepts(NikoAiReply("Dato [1]", true, emptyList()))) }
}
