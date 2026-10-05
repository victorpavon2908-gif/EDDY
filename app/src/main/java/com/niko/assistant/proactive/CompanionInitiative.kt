package com.niko.assistant.proactive

/** Uses elapsed realtime. No microphone, network, timers or Android dependencies. */
class CompanionInitiative {
    private var idleSince: Long? = null
    private var nextAllowed = 0L
    private var invitations = 0
    private var phrase = 0

    fun interacted(now: Long) { idleSince = null; nextAllowed = now + 90_000L; invitations = 0 }
    fun silence(now: Long) { idleSince = null; nextAllowed = now + 30 * 60_000L; invitations = 2 }

    fun invitation(now: Long, enabled: Boolean, available: Boolean): String? {
        if (!enabled || !available) { idleSince = null; return null }
        if (now < nextAllowed || invitations >= 2) return null
        val start = idleSince ?: now.also { idleSince = it }
        if (now - start < 45_000L) return null
        idleSince = null
        nextAllowed = now + 5 * 60_000L
        invitations++
        return prompts[phrase++ % prompts.size]
    }

    private val prompts = listOf(
        "Tengo una curiosidad: ¿qué te gustaría aprender a hacer, aunque parezca difícil?",
        "Mi gran plan era quedarme callado, pero tengo una idea: ¿te animás a un acertijo?",
        "¿Qué tal va tu día? Podés contarme lo bueno o desahogarte un rato.",
    )
}
