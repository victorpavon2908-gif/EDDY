package com.niko.assistant.cognitive

import com.niko.assistant.actions.LeoHardwareProtocolRunner
import com.niko.assistant.actions.LeoLabProtocols
import com.niko.assistant.agent.TurnController
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.devicecontrol.LeoLiveVisionAnalyzer
import com.niko.assistant.learning.LeoAutodidactEngine
import com.niko.assistant.memory.LeoEpisodicTimeline
import com.niko.assistant.memory.LeoLocalRagEngine
import com.niko.assistant.memory.LeoSovereignStore
import kotlinx.coroutines.CancellationException

/** Coordinates answer production inside the existing turn capability, never owns a second voice job. */
class LeoNeuralCore(
    private val turns: TurnController,
    val autodidact: LeoAutodidactEngine = LeoAutodidactEngine(),
    val rag: LeoLocalRagEngine = LeoLocalRagEngine(),
    val emotionalHeart: LeoEmotionalHeart = LeoEmotionalHeart(),
    val persona: LeoEddyPersona = LeoEddyPersona(),
    val protocols: LeoLabProtocols = LeoLabProtocols(),
    val timeline: LeoEpisodicTimeline = LeoEpisodicTimeline(rag),
    val vision: LeoLiveVisionAnalyzer = LeoLiveVisionAnalyzer(rag),
    val consciousness: LeoLivingConsciousness = LeoLivingConsciousness(),
    val store: LeoSovereignStore? = null,
    val hardwareRunner: LeoHardwareProtocolRunner? = null,
) {
    init {
        store?.restoreAll(autodidact, rag, timeline, persona, consciousness)
    }

    private fun persistState() {
        store?.saveAll(autodidact, rag, timeline, persona, consciousness)
    }

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
            ?: persona.handleCommand(message)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
            ?: consciousness.interact(message, emotionalHeart)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
            ?: protocols.handleCommand(message)?.let { exec ->
                hardwareRunner?.execute(exec)
                timeline.recordEpisode(LeoEpisodicTimeline.EpisodeKind.PROTOCOL_EXECUTED, exec.protocol.name, exec.message)
                persistState()
                NikoAiReply(exec.message, false, emptyList())
            }
            ?: vision.handleVisualCommand(message)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
            ?: timeline.handleCommand(message)?.let { NikoAiReply(it, false, emptyList()) }
            ?: autodidact.interact(message)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
            ?: rag.handleCommand(message)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
            ?: emotion.reply(message)?.let { NikoAiReply(it.text, false, emptyList()) }
            ?: code.reply(message)
        turns.checkpoint()
        return answer
    }

    suspend fun reply(request: Request, knowledge: Knowledge): NikoAiReply {
        turns.checkpoint()
        val answer = try {
            persona.handleCommand(request.message)?.let {
                persistState()
                NikoAiReply(it, false, emptyList())
            }
                ?: consciousness.interact(request.message, emotionalHeart)?.let {
                    persistState()
                    NikoAiReply(it, false, emptyList())
                }
                ?: protocols.handleCommand(request.message)?.let { exec ->
                    hardwareRunner?.execute(exec)
                    timeline.recordEpisode(LeoEpisodicTimeline.EpisodeKind.PROTOCOL_EXECUTED, exec.protocol.name, exec.message)
                    persistState()
                    NikoAiReply(exec.message, false, emptyList())
                }
                ?: vision.handleVisualCommand(request.message)?.let {
                    persistState()
                    NikoAiReply(it, false, emptyList())
                }
                ?: timeline.handleCommand(request.message)?.let { NikoAiReply(it, false, emptyList()) }
                ?: autodidact.interact(request.message)?.let {
                    persistState()
                    NikoAiReply(it, false, emptyList())
                }
                ?: rag.handleCommand(request.message)?.let {
                    persistState()
                    NikoAiReply(it, false, emptyList())
                }
                ?: code.reply(request.message)
                ?: findDistilledAnswer(request.message)
                ?: reasoning.reply(request, ResilientKnowledge(knowledge)).also { produced ->
                    if (produced.webUsed && produced.text.isNotBlank()) {
                        timeline.distillKnowledge(request.message, produced.text)
                        persistState()
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        // Producers may return after cancellation. Never publish their obsolete answer.
        turns.checkpoint()
        return answer
    }
    private fun findDistilledAnswer(query: String): NikoAiReply? {
        if (com.niko.assistant.brain.WebQueryRouter.needsRecentInformation(query)) return null
        if (com.niko.assistant.brain.WebQueryRouter.explicitQuery(query) != null) return null
        if (com.niko.assistant.ai.AutonomousResearch.offlineOnly(query)) return null
        if (query.trim().length < 4) return null
        val hits = rag.search(query, topK = 1)
        val best = hits.firstOrNull() ?: return null
        if (best.chunk.docId.startsWith("distilled_") && best.score >= 0.72) {
            val cleanSnippet = best.chunk.text.trim()
            val spoken = "Según mis registros locales: ${com.niko.assistant.ai.LeoNativeWebSearch.buildSpokenSummary(cleanSnippet) ?: cleanSnippet}"
            return NikoAiReply(
                text = "📌 **Memoria Local (Ficha Destilada):**\n\n$cleanSnippet\n\n*(Recuperado al instante desde tu base vectorial local)*",
                webUsed = false,
                sources = emptyList(),
                spokenText = spoken,
                suggestions = listOf("Buscar versión actualizada en la red", "Profundizar en este tema"),
            )
        }
        return null
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
