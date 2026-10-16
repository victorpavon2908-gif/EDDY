package com.niko.assistant.cognitive

import com.niko.assistant.agent.TurnController
import com.niko.assistant.ai.NikoAiReply
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LeoNeuralCoreTest {
    private fun request(text: String) = LeoNeuralCore.Request(text, true, false, false)
    private open class Knowledge : LeoNeuralCore.Knowledge {
        override suspend fun local(): String? = "Respuesta local"
        override suspend fun research(requireSources: Boolean): NikoAiReply? = null
        override suspend fun fallback() = "Sin respuesta verificada"
    }
    @Test fun arithmeticIsVerifiedWithoutNetwork() = runBlocking {
        val core = LeoNeuralCore(TurnController())
        assertEquals("42", core.reply(request("6 * 7"), object : Knowledge() {
            override suspend fun local(): String? = error("Arithmetic must not generate an answer")
        }).text)
    }
    @Test fun obsoleteProducerCannotPublish() = runBlocking {
        val turns = TurnController(); val token = turns.begin()
        try {
            withContext(token) {
                LeoNeuralCore(turns).reply(request("hola"), object : Knowledge() {
                    override suspend fun local(): String { turns.cancel(); return "obsoleta" }
                })
            }
            fail("Stale reply published")
        } catch (_: CancellationException) { /* expected */ }
    }
    @Test fun cancellationIsNotConvertedIntoFallback() = runBlocking {
        try {
            LeoNeuralCore(TurnController()).reply(request("hola"), object : Knowledge() {
                override suspend fun local(): String? = throw CancellationException("stop")
                override suspend fun fallback(): String = error("must not swallow cancellation")
            })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { /* expected */ }
    }
    @Test fun localCodeHasFilesAndSeparateNarration() {
        val code = LeoCodeIntelligence()
        assertEquals(setOf("calculator.py", "test_calculator.py"), code.project("creame una calculadora en Python")!!.files.keys)
        assertFalse(code.reply("creame una calculadora en Python")!!.spokenText!!.contains("def "))
        assertNull(code.project("creame una aplicación bancaria en Rust"))
    }
    @Test fun generatedPythonProjectActuallyPassesItsTests() {
        val directory = java.nio.file.Files.createTempDirectory("leo-code-test").toFile()
        try {
            LeoCodeIntelligence().project("crea una calculadora en Python")!!.files.forEach { (name, source) ->
                java.io.File(directory, name).writeText(source)
            }
            val process = ProcessBuilder("python3", "-m", "unittest", "-v").directory(directory)
                .redirectErrorStream(true).start()
            try {
                assertTrue("Python test timeout", process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output, 0, process.exitValue())
                assertTrue(output, output.contains("Ran 3 tests"))
            } finally { process.destroyForcibly() }
        } finally { directory.deleteRecursively() }
    }
    @Test fun emotionAbstainsOnNegationQuotesAndActing() {
        for (text in listOf("no estoy triste", "estoy no triste", "¿estoy triste?", "él está triste", "pon cara de miedo", "estoy triste pero feliz", "estoy programando")) {
            assertNull(text, LeoEmotionClassifier.classify(text))
        }
    }
    @Test fun contextualAffectSelectsGentleBehavior() {
        assertEquals(LeoEmotionClassifier.Emotion.FRUSTRATION, LeoEmotionClassifier.classify("Me siento frustrada con este error")?.emotion)
        assertTrue(LeoEmotionClassifier.needsGentleResponse("estoy preocupada por mañana"))
        assertFalse(LeoEmotionClassifier.needsGentleResponse("estoy contento con el resultado"))
    }
}
