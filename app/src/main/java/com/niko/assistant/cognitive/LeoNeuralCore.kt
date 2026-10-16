package com.niko.assistant.cognitive

import com.niko.assistant.agent.TurnController
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.brain.NikoMathEngine
import kotlinx.coroutines.CancellationException

/** Coordinates answer production inside the existing turn capability, never owns a second voice job. */
class LeoNeuralCore(private val turns: TurnController) {
    data class Request(val message: String, val localFirst: Boolean, val autoResearch: Boolean, val learnedSearch: Boolean)
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
            ?: emotion.reply(message)?.let { NikoAiReply(it.text, false, emptyList()) }
            ?: code.reply(message)
        turns.checkpoint()
        return answer
    }

    suspend fun reply(request: Request, knowledge: Knowledge): NikoAiReply {
        turns.checkpoint()
        val answer = try {
            code.reply(request.message) ?: reasoning.reply(request, knowledge)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        // Producers may return after cancellation. Never publish their obsolete answer.
        turns.checkpoint()
        return answer
    }
}

/** Verified arithmetic plus existing retrieval and research; not a general-purpose reasoning LLM. */
class LeoReasoningEngine {
    suspend fun reply(request: LeoNeuralCore.Request, knowledge: LeoNeuralCore.Knowledge): NikoAiReply {
        NikoMathEngine.solve(request.message)?.let { return NikoAiReply(it, false, emptyList()) }
        val mayInfer = !com.niko.assistant.ai.AutonomousResearch.offlineOnly(request.message) &&
            !com.niko.assistant.brain.WebQueryRouter.needsCurrentInformation(request.message) &&
            com.niko.assistant.brain.WebQueryRouter.explicitQuery(request.message) == null
        val key = com.niko.assistant.memory.MemoryLearning.key(request.message)
        val codeRequest = Regex("\\b(codigo|programa|funcion|kotlin|python|javascript|typescript|rust|java|sql|compilacion)\\b").containsMatchIn(key) &&
            Regex("^(crea|creame|genera|escribe|escribi|implementa|depura|corrige|refactoriza|explica|explicame)\\b").containsMatchIn(key)
        if (mayInfer && codeRequest) knowledge.infer()?.let { return it }
        return ConversationCoordinator.reply(request.message, request.localFirst, request.autoResearch,
            request.learnedSearch, knowledge::local,
            { requireSources ->
                if (requireSources) knowledge.research(true)
                else if (mayInfer && !codeRequest) knowledge.infer() else null
            }, knowledge::fallback)
    }
}
