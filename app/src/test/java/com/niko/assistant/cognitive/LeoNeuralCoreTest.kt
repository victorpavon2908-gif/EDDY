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
    @Test fun codeHonorsLocalFirstAndUsesRemoteOnlyWhenRequested() = runBlocking {
        var calls = 0
        val knowledge = object : Knowledge() {
            override suspend fun infer(): NikoAiReply { calls++; return NikoAiReply("código propuesto", false, emptyList()) }
        }
        val core = LeoNeuralCore(TurnController())
        assertEquals("Respuesta local", core.reply(request("escribe una función en Rust"), knowledge).text)
        assertEquals(0, calls)
        assertEquals("código propuesto", core.reply(request("escribe una función en Rust").copy(localFirst = false), knowledge).text)
        core.reply(request("escribe una función en Rust sin internet"), knowledge)
        assertEquals(1, calls)
    }
    @Test fun currentInformationStillRequiresRetrievedSources() = runBlocking {
        var searched = false
        val knowledge = object : Knowledge() {
            override suspend fun infer(): NikoAiReply? = error("Current facts must not be invented by inference")
            override suspend fun research(requireSources: Boolean): NikoAiReply {
                assertTrue(requireSources); searched = true
                return NikoAiReply("fuente recuperada", true, emptyList())
            }
        }
        LeoNeuralCore(TurnController()).reply(LeoNeuralCore.Request("busca noticias de hoy", true, true, false), knowledge)
        assertTrue(searched)
    }
    @Test fun arithmeticAndUnitConversionsAreVerifiedWithoutNetwork() = runBlocking {
        val core = LeoNeuralCore(TurnController())
        assertEquals("42", core.reply(request("6 * 7"), object : Knowledge() {
            override suspend fun local(): String? = error("Arithmetic must not generate an answer")
        }).text)
        assertEquals("100 °C equivalen a 212 °F.", core.reply(request("convertí 100 grados celsius a fahrenheit"), object : Knowledge() {
            override suspend fun local(): String? = error("Unit conversion must not require local fallback")
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
        assertEquals(setOf("unit_converter.py", "test_unit_converter.py"), code.project("crea un conversor de unidades en Python")!!.files.keys)
        assertEquals(setOf("todo_manager.py", "test_todo_manager.py"), code.project("crea un gestor de tareas en Python")!!.files.keys)
        assertEquals(setOf("sqlite_notes.py", "test_sqlite_notes.py"), code.project("crea un crud sqlite en Python")!!.files.keys)
        assertEquals(setOf("RetryPolicy.kt"), code.project("implementa un retry con backoff en Kotlin")!!.files.keys)
        assertEquals(setOf("lruCache.ts"), code.project("implementa una cache LRU en TypeScript")!!.files.keys)
        assertFalse(code.reply("creame una calculadora en Python")!!.spokenText!!.contains("def "))
        assertNull(code.project("creame una aplicación bancaria en Rust"))
    }
    @Test fun generatedPythonProjectActuallyPassesItsTests() {
        val prompts = listOf(
            "crea una calculadora en Python",
            "crea un conversor de unidades en Python",
            "crea un gestor de tareas en Python",
            "crea un crud sqlite en Python",
            "crea un validador de contraseñas en Python",
        )
        for (prompt in prompts) {
            val directory = java.nio.file.Files.createTempDirectory("leo-code-test").toFile()
            try {
                LeoCodeIntelligence().project(prompt)!!.files.forEach { (name, source) ->
                    java.io.File(directory, name).writeText(source)
                }
                fun runPython(binary: String): Pair<Int, String>? = try {
                    val proc = ProcessBuilder(binary, "-m", "unittest", "-v").directory(directory)
                        .redirectErrorStream(true).start()
                    try {
                        assertTrue("Python test timeout for $prompt", proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS))
                        val out = proc.inputStream.bufferedReader().readText()
                        proc.exitValue() to out
                    } finally {
                        proc.destroyForcibly()
                    }
                } catch (_: java.io.IOException) {
                    null
                }
                val result = runPython("python3")?.takeIf { it.first == 0 } ?: runPython("python")!!
                assertEquals(result.second, 0, result.first)
                assertTrue(result.second, result.second.contains("Ran 3 tests"))
            } finally { directory.deleteRecursively() }
        }
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
    @Test fun failedLocalAndRemoteProducersRecoverWithoutExposingErrors() = runBlocking {
        val answer = LeoNeuralCore(TurnController()).reply(request("hola"), object : Knowledge() {
            override suspend fun local(): String? = throw IllegalStateException("private local data")
            override suspend fun infer(): NikoAiReply? = throw java.io.IOException("secret key")
            override suspend fun fallback(): String = throw IllegalStateException("private fallback data")
        })
        assertTrue(answer.text.contains("No pude completar"))
        assertFalse(answer.text.contains("private"))
        assertFalse(answer.text.contains("secret"))
    }
    @Test fun failedResearchDoesNotInventAnOfflineCurrentAnswer() = runBlocking {
        val answer = LeoNeuralCore(TurnController()).reply(
            LeoNeuralCore.Request("busca noticias de hoy", true, true, false), object : Knowledge() {
                override suspend fun research(requireSources: Boolean): NikoAiReply? = throw java.io.IOException()
                override suspend fun local(): String = error("Must not replace current sources with local guesses")
            })
        assertTrue(answer.text.contains("No pude verificar"))
        assertFalse(answer.webUsed)
    }
    @Test fun lateFailureAfterInterruptionCannotStartAnotherProducer() = runBlocking {
        val turns = TurnController()
        try {
            withContext(turns.begin()) {
                LeoNeuralCore(turns).reply(request("hola"), object : Knowledge() {
                    override suspend fun local(): String? { turns.cancel(); throw java.io.IOException() }
                    override suspend fun infer(): NikoAiReply? = error("Stale fallback")
                })
            }
            fail("Stale failure swallowed")
        } catch (_: CancellationException) { }
    }
    @Test fun authorizedFollowUpUsesInferenceBeforeGenericLocalAndRecoversOnFailure() = runBlocking {
        var calls = 0
        val core = LeoNeuralCore(TurnController())
        val request = request("explicá eso más sencillo").copy(contextualInference = true, localFirst = false)
        val knowledge = object : Knowledge() {
            override suspend fun infer(): NikoAiReply? {
                calls++
                return if (calls == 1) NikoAiReply("continuación contextual", false, emptyList()) else null
            }
        }
        assertEquals("Respuesta local", core.reply(request.copy(localFirst = true), knowledge).text)
        assertEquals(0, calls)
        assertEquals("continuación contextual", core.reply(request, knowledge).text)
        assertEquals("Respuesta local", core.reply(request, knowledge).text)
        assertEquals(2, calls)
        core.reply(request.copy(message = "explicá eso sin internet"), knowledge)
        assertEquals(2, calls)
    }
}
