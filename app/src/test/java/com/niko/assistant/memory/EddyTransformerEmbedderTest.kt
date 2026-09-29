package com.niko.assistant.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class EddyTransformerEmbedderTest {

    private val embedder = EddyTransformerEmbedder.INSTANCE

    @Test
    fun producesL2NormalizedVectorsOfConfiguredDimension() {
        val vector = embedder.encode("Recordar mi canción favorita en Spotify")
        assertEquals(EddyTransformerEmbedder.DEFAULT_DIM, vector.size)

        var sumSq = 0f
        for (v in vector) sumSq += v * v
        val norm = sqrt(sumSq)
        assertTrue("Vector norm should be approximately 1.0, was $norm", abs(norm - 1.0f) < 1e-3f)
    }

    @Test
    fun embeddingIsDeterministicAndCached() {
        val text = "Mi número de teléfono es 8888 7777"
        val vec1 = embedder.encode(text)
        val vec2 = embedder.encode(text)

        for (i in vec1.indices) {
            assertEquals("Vector index $i should match", vec1[i], vec2[i], 1e-5f)
        }
    }

    @Test
    fun identicalTextsHavePerfectSimilarity() {
        val similarity = embedder.semanticSimilarity("café con leche", "café con leche")
        assertEquals(1.0, similarity, 1e-4)
    }

    @Test
    fun semanticallyRelatedSentencesScoreHigherThanUnrelated() {
        val query = "quiero escuchar música"
        val related = "abrir la app de spotify para reproducir música"
        val unrelated = "apagar la linterna de la cámara del teléfono"

        val simRelated = embedder.semanticSimilarity(query, related)
        val simUnrelated = embedder.semanticSimilarity(query, unrelated)

        assertTrue(
            "Related ($simRelated) should score higher than unrelated ($simUnrelated)",
            simRelated > simUnrelated,
        )
    }
}
