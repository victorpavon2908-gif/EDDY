package com.niko.assistant.agent

import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.memory.MemoryLearning

/** Incremental orchestration boundary; existing voice engines and action implementations stay intact. */
class LeoAgentRuntime(private val now: () -> Long = System::currentTimeMillis) {
    val turns = TurnController()
    val conversation = ConversationManager(now)
    private var pending: Confirmation? = null
    data class Confirmation(val command: AssistantCommand, val expiresAt: Long)
    sealed interface ConfirmationReply {
        data class Approved(val command: AssistantCommand) : ConfirmationReply
        data object Rejected : ConfirmationReply
        data object None : ConfirmationReply
    }

    fun begin(text: String): TurnController.Token = turns.begin().also { conversation.begin(text, it.generation) }
    fun interrupt() { turns.cancel(); pending = null; conversation.interrupt() }

    fun requestConfirmation(command: AssistantCommand) { pending = Confirmation(command, now() + 60_000L) }

    /** Exact one-shot confirmation, bound to a concrete command. A topic change revokes it. */
    fun confirmation(text: String): ConfirmationReply {
        val request = pending ?: return ConfirmationReply.None
        pending = null
        if (now() > request.expiresAt) return ConfirmationReply.None
        return when (MemoryLearning.key(text)) {
            "si", "si confirmo", "confirmo", "si borra mi memoria" -> ConfirmationReply.Approved(request.command)
            "no", "mejor no", "cancelar", "cancela" -> ConfirmationReply.Rejected
            else -> ConfirmationReply.None
        }
    }
}
