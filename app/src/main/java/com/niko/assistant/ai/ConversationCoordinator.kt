package com.niko.assistant.ai

import com.niko.assistant.brain.WebQueryRouter

/** One activated turn, at most one cloud call, with a genuinely offline route. */
object ConversationCoordinator {
    suspend fun reply(
        message: String,
        localFirst: Boolean,
        autoResearch: Boolean,
        learnedSearch: Boolean,
        local: suspend () -> String?,
        cloud: suspend (requireSources: Boolean) -> NikoAiReply?,
        fallback: suspend () -> String,
    ): NikoAiReply {
        fun localReply(text: String) = NikoAiReply(text, false, emptyList())
        if (AutonomousResearch.offlineOnly(message)) return localReply(local() ?: fallback())
        val mayResearch = (autoResearch || WebQueryRouter.explicitQuery(message) != null) && AutonomousResearch.allowedFor(message)
        if (mayResearch && (WebQueryRouter.needsCurrentInformation(message) || learnedSearch)) {
            return cloud(true) ?: localReply("No pude verificar ese dato en Internet. Las funciones locales siguen disponibles.")
        }
        if (localFirst) {
            val answer = local()
            if (!answer.isNullOrBlank() && !AutonomousResearch.uncertain(answer)) return localReply(answer)
            return cloud(mayResearch && answer != null) ?: localReply(answer ?: fallback())
        }
        return cloud(false) ?: localReply(local() ?: fallback())
    }
}
