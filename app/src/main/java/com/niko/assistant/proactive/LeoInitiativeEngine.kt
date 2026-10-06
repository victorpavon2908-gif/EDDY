package com.niko.assistant.proactive

/** Decisions require explicit evidence. No random phrases, background polling or implicit actions. */
class LeoInitiativeEngine {
    data class Candidate(val id: String, val text: String, val reason: String, val priority: Int,
        val relevance: Double, val expiresAt: Long)
    data class Event(val id: String, val reason: String, val at: Long, val outcome: String)
    data class State(val day: String = "", val count: Int = 0, val nextAllowed: Long = 0L,
        val suppressed: Map<String, Long> = emptyMap(), val history: List<Event> = emptyList())
    var state = State()
        private set
    private var idleSince: Long? = null

    fun restore(saved: State) { state = saved.copy(history = saved.history.takeLast(40), suppressed = saved.suppressed.entries.take(100).associate { it.toPair() }) }
    fun interacted(now: Long, rejected: Boolean = false) {
        val recent = state.history.lastOrNull()?.takeIf { it.outcome == "OFFERED" && now - it.at in 0..120_000L }
        if (recent != null) settle(recent, if (rejected) "REJECTED" else "ENGAGED", now)
        idleSince = null
        state = state.copy(nextAllowed = maxOf(state.nextAllowed, now + 90_000L))
    }
    fun silence(now: Long) {
        interacted(now, rejected = true)
        state = state.copy(nextAllowed = now + 30 * 60_000L)
    }

    fun decide(now: Long, day: String, hour: Int, enabled: Boolean, available: Boolean,
        candidates: List<Candidate>): Candidate? {
        state.history.lastOrNull()?.takeIf { it.outcome == "OFFERED" && now - it.at > 120_000L }
            ?.let { settle(it, "IGNORED", now) }
        if (state.day != day) state = state.copy(day = day, count = 0)
        if (!enabled || !available || hour !in 8..21) { idleSince = null; return null }
        if (now < state.nextAllowed || state.count >= 2) return null
        val start = idleSince ?: now.also { idleSince = it }
        if (now - start < 45_000L) return null
        val chosen = candidates.filter {
            it.id.isNotBlank() && it.reason.isNotBlank() && it.text.isNotBlank() && it.relevance >= 0.7 &&
                it.expiresAt > now && (state.suppressed[it.id] ?: 0L) <= now
        }.sortedWith(compareByDescending<Candidate> { it.priority }.thenByDescending { it.relevance }).firstOrNull() ?: return null
        idleSince = null
        state = state.copy(count = state.count + 1, nextAllowed = now + 30 * 60_000L,
            suppressed = (state.suppressed.filterValues { it > now } + (chosen.id to now + 24 * 60 * 60_000L)),
            history = (state.history + Event(chosen.id, chosen.reason, now, "OFFERED")).takeLast(40))
        return chosen
    }

    private fun settle(event: Event, outcome: String, now: Long) {
        state = state.copy(history = state.history.map { if (it == event) it.copy(outcome = outcome) else it },
            suppressed = if (outcome == "ENGAGED") state.suppressed else
                state.suppressed + (event.id to now + 7 * 24 * 60 * 60_000L))
    }
}
