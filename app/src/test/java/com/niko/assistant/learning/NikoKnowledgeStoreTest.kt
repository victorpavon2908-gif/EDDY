package com.niko.assistant.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoKnowledgeStoreTest {
    @Test
    fun normalizationRemovesWakeAliases() {
        assertEquals(
            "como funciona un agujero negro",
            NikoKnowledgeStore.normalize("Niko, ¿cómo funciona un agujero negro?"),
        )
        assertEquals(
            "explicame la gravedad",
            NikoKnowledgeStore.normalize("Nin porfa explicame la gravedad"),
        )
        assertEquals(
            "explicame la gravedad",
            NikoKnowledgeStore.normalize("Leo porfa explicame la gravedad"),
        )
    }

    @Test
    fun relatedQuestionScoresAndCoversAboveUnrelatedQuestion() {
        val learned = NikoKnowledgeStore.normalize("como funciona la fotosintesis en las plantas")
        val related = NikoKnowledgeStore.normalize("explicame como funciona la fotosintesis de una planta")
        val unrelated = NikoKnowledgeStore.normalize("abre whatsapp y manda un mensaje")

        val relatedScore = NikoKnowledgeStore.cosineLikeSimilarity(learned, related)
        val unrelatedScore = NikoKnowledgeStore.cosineLikeSimilarity(learned, unrelated)
        val relatedCoverage = NikoKnowledgeStore.semanticCoverage(related, learned)
        val unrelatedCoverage = NikoKnowledgeStore.semanticCoverage(unrelated, learned)

        assertTrue(relatedScore > unrelatedScore)
        assertTrue(relatedScore > 0.52)
        assertTrue(relatedCoverage >= 0.67)
        assertTrue(unrelatedCoverage < 0.67)
    }
}
