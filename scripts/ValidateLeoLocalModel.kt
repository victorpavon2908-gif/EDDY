import com.niko.assistant.inference.local.LeoLlamaBridge
import com.niko.assistant.inference.local.LeoLocalPrompt
import java.io.File

/** Actual JNI + official weights; host quality probes, not Android performance measurements. */
fun main(args: Array<String>) {
    require(args.size == 1)
    val cases = listOf(
        "¿Cuánto es 2 + 2? Respondé solamente el número." to "4",
        "¿Cuál es la capital de Nicaragua? Respondé solamente el nombre." to "Managua",
        "¿Cuánto es 9 por 7? Respondé solamente el número." to "63",
        "Escribí solamente una expresión Python que sume 2 y 3, sin explicación." to "2 + 3",
    )
    var passed = 0
    cases.forEachIndexed { index, (question, expected) ->
        val raw = LeoLlamaBridge.generate(File(args[0]).absolutePath, LeoLocalPrompt.build(question).toByteArray(), index + 1L, 128)
        val answer = LeoLocalPrompt.answer(raw?.toString(Charsets.UTF_8))
        val ok = answer?.replace(" ", "")?.contains(expected.replace(" ", ""), ignoreCase = true) == true
        println("CASE ${index + 1} passed=$ok answer=${answer?.replace('\n', ' ')}")
        if (ok) passed++
    }
    val contextual = LeoLlamaBridge.generate(File(args[0]).absolutePath,
        LeoLocalPrompt.build("¿Cómo se llama mi proyecto? Respondé solo su nombre.", "El usuario confirmó: mi proyecto se llama Aurora.").toByteArray(), 50, 512)
    val contextualAnswer = LeoLocalPrompt.answer(contextual?.toString(Charsets.UTF_8))
    val contextPassed = contextualAnswer?.contains("Aurora", ignoreCase = true) == true
    println("CONTEXT passed=$contextPassed answer=$contextualAnswer")
    check(contextPassed) { "Context retrieval failed" }
    val passage = "El proyecto Aurora tiene tres etapas: diseño, implementación y evaluación."
    val original = com.niko.assistant.ai.NikoAiReply("Respuesta extractiva", true,
        listOf(com.niko.assistant.ai.NikoWebSource("Documento de prueba", "https://example.org/aurora")),
        passages = listOf(com.niko.assistant.ai.NikoRetrievedPassage(1, passage)))
    val evidence = com.niko.assistant.cognitive.LeoResearchSynthesis.evidence(original)!!
    val researchRaw = LeoLlamaBridge.generate(File(args[0]).absolutePath,
        com.niko.assistant.inference.local.LeoLocalResearchPrompt.build("¿Cuáles son las etapas del proyecto Aurora?", evidence).toByteArray(), 60, 1024)
    val researchAnswer = LeoLocalPrompt.answer(researchRaw?.toString(Charsets.UTF_8))
    val grounded = researchAnswer?.let { com.niko.assistant.cognitive.LeoResearchSynthesis.validate(it, original) }
    println("RESEARCH validCitations=${grounded != null} raw=$researchAnswer")
    check(grounded != null) { "Real model did not produce verifiable citations for the bounded fixture" }
    LeoLlamaBridge.cancel(100)
    check(LeoLlamaBridge.generate(File(args[0]).absolutePath, LeoLocalPrompt.build("hola").toByteArray(), 100, 128) == null)
    var cancelledAnswer: ByteArray? = byteArrayOf(1)
    val generation = Thread {
        cancelledAnswer = LeoLlamaBridge.generate(File(args[0]).absolutePath, LeoLocalPrompt.build("Explicá la fotosíntesis.").toByteArray(), 200, 384)
    }
    generation.start()
    Thread.sleep(100)
    LeoLlamaBridge.cancel(200)
    generation.join(30000)
    check(!generation.isAlive && cancelledAnswer == null) { "In-flight cancellation failed" }
    println("CANCELLATION preCancelled=true inFlight=true")
    println("RESULT passed=$passed total=${cases.size} hostOnly=true")
    check(passed == cases.size) { "Model failed bounded host acceptance probes" }
}
