package com.niko.assistant.memory.embedding

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Uses actual downloaded trained weights. CI prepares them; no fake model or measured phone latency. */
class LeoSemanticEncoderTest {
    @Test fun realSpanishParaphrasesRankAboveUnrelatedMemories() {
        val path = System.getenv("LEO_EMBEDDING_MODELS")
        assumeTrue("Run scripts/prepare_semantic_test.py to enable real inference", path != null)
        LeoSemanticEncoder(File(requireNotNull(path))).use { model ->
            val cases = listOf(
                listOf("Necesito reparar mi automóvil", "El coche está averiado y necesita un mecánico", "La receta de pastel lleva harina"),
                listOf("¿Dónde dejé las llaves?", "No encuentro las llaves de casa", "El océano tiene mucha sal"),
                listOf("Tengo que terminar el informe de productividad", "Nos falta concluir el reporte de eficiencia", "Compré zapatos azules"),
                listOf("Ya no tomo café", "He dejado de beber café", "Las montañas están nevadas"),
                listOf("Debo entregar el trabajo mañana", "La fecha límite de entrega es el día siguiente", "Mi gato duerme en el sofá"),
                listOf("Me duele la cabeza", "Tengo dolor de cabeza", "Cambié la contraseña del correo"),
            )
            for ((query, related, unrelated) in cases) {
                val vectors = listOf(query, related, unrelated).map(model::encode)
                assertEquals(512, vectors[0].size)
                assertEquals(1.0, dot(vectors[0], vectors[0]), 0.0001)
                val positive = dot(vectors[0], vectors[1]); val negative = dot(vectors[0], vectors[2])
                println("SEMANTIC_EVIDENCE | $query | related=$positive | unrelated=$negative")
                assertTrue(query, positive > negative + 0.15)
            }
            val original = model.encode("La memoria sigue intacta")
            val copy = original.copyOf(); original.fill(0f)
            assertArrayEquals(copy, model.encode("La memoria sigue intacta"), 0f)
            assertArrayEquals(FloatArray(512), model.encode("  "), 0f)
        }
    }
    @Test fun missingOrTamperedArtifactsAreNotLoaded() {
        val dir = kotlin.io.path.createTempDirectory("leo-model-test").toFile()
        try {
            assertFalse(LeoEmbeddingModel.verify(dir))
            File(dir, "vocab.txt").writeText("not model weights")
            assertFalse(LeoEmbeddingModel.verify(dir))
        } finally { dir.deleteRecursively() }
    }
    private fun dot(a: FloatArray, b: FloatArray) = a.indices.sumOf { a[it].toDouble() * b[it] }
}
