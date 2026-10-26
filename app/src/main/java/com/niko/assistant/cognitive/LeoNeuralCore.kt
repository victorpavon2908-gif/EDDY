package com.niko.assistant.cognitive

import com.niko.assistant.agent.TurnController
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.learning.LeoAutodidactEngine
import com.niko.assistant.memory.LeoLocalRagEngine
import kotlinx.coroutines.CancellationException

/** Coordinates answer production inside the existing turn capability, never owns a second voice job. */
class LeoNeuralCore(
    private val turns: TurnController,
    val autodidact: LeoAutodidactEngine = LeoAutodidactEngine(),
    val rag: LeoLocalRagEngine = LeoLocalRagEngine(),
    val emotionalHeart: LeoEmotionalHeart = LeoEmotionalHeart(),
) {
    data class Request(val message: String, val localFirst: Boolean, val autoResearch: Boolean, val learnedSearch: Boolean, val contextualInference: Boolean = false)
    interface Knowledge {
        suspend fun local(): String?
        suspend fun research(requireSources: Boolean): NikoAiReply?
        suspend fun infer(): NikoAiReply? = null
        suspend fun fallback(): String
    }
    private val reasoning = LeoReasoningEngine()
    private val code = LeoCodeIntelligence()

    suspend fun personalReply(message: String, memory: suspend (String) -> String?, emotion: LeoEmotionEngine): NikoAiReply? {
        turns.checkpoint()
        val answer = memory(message)?.let { NikoAiReply(it, false, emptyList()) }
            ?: autodidact.interact(message)?.let { NikoAiReply(it, false, emptyList()) }
            ?: rag.handleCommand(message)?.let { NikoAiReply(it, false, emptyList()) }
            ?: emotion.reply(message)?.let { NikoAiReply(it.text, false, emptyList()) }
            ?: code.reply(message)
        turns.checkpoint()
        return answer
    }

    suspend fun reply(request: Request, knowledge: Knowledge): NikoAiReply {
        turns.checkpoint()
        val answer = try {
            autodidact.interact(request.message)?.let { NikoAiReply(it, false, emptyList()) }
                ?: rag.handleCommand(request.message)?.let { NikoAiReply(it, false, emptyList()) }
                ?: code.reply(request.message)
                ?: reasoning.reply(request, ResilientKnowledge(knowledge))
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        // Producers may return after cancellation. Never publish their obsolete answer.
        turns.checkpoint()
        return answer
    }
    /** A failed optional producer must not abort the whole turn; cancellation always wins. */
    private inner class ResilientKnowledge(private val source: Knowledge) : Knowledge {
        private suspend fun <T> attempt(block: suspend () -> T): T? {
            turns.checkpoint()
            val result = try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            turns.checkpoint()
            return result
        }
        override suspend fun local() = attempt { source.local() }?.takeIf { it.isNotBlank() }
        override suspend fun infer() = attempt { source.infer() }?.takeIf { it.text.isNotBlank() }
        override suspend fun research(requireSources: Boolean) = attempt { source.research(requireSources) }
            ?.takeIf { it.text.isNotBlank() }
        override suspend fun fallback() = attempt { source.fallback() }?.takeIf { it.isNotBlank() }
            ?: "No pude completar esta respuesta. Podés volver a intentarlo o reformular la pregunta."
    }

}

/** Verified arithmetic plus existing retrieval and research; not a general-purpose reasoning LLM. */
class LeoReasoningEngine {
    suspend fun reply(request: LeoNeuralCore.Request, knowledge: LeoNeuralCore.Knowledge): NikoAiReply {
        NikoMathEngine.solve(request.message)?.let { return NikoAiReply(it, false, emptyList()) }
        NikoMathEngine.convertUnits(request.message)?.let { return NikoAiReply(it, false, emptyList()) }
        val mayInfer = !com.niko.assistant.ai.AutonomousResearch.offlineOnly(request.message) &&
            !com.niko.assistant.brain.WebQueryRouter.needsCurrentInformation(request.message) &&
            com.niko.assistant.brain.WebQueryRouter.explicitQuery(request.message) == null
        val key = com.niko.assistant.memory.MemoryLearning.key(request.message)
        val codeRequest = Regex("\\b(codigo|programa|funcion|kotlin|python|javascript|typescript|rust|java|sql|compilacion)\\b").containsMatchIn(key) &&
            Regex("^(crea|creame|genera|escribe|escribi|implementa|depura|corrige|refactoriza|explica|explicame)\\b").containsMatchIn(key)
        val preferInference = codeRequest || request.contextualInference
        if (mayInfer && preferInference) {
            // Respect local-first for coding and references, not only casual conversation.
            if (request.localFirst) knowledge.local()?.takeIf { it.isNotBlank() }
                ?.let { return NikoAiReply(it, false, emptyList()) }
            knowledge.infer()?.let { return it }
        }
        return ConversationCoordinator.reply(request.message, request.localFirst, request.autoResearch,
            request.learnedSearch, knowledge::local,
            { requireSources ->
                if (requireSources) knowledge.research(true)
                else if (mayInfer && !preferInference) knowledge.infer() else null
            }, knowledge::fallback)
    }
}
