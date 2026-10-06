package com.niko.assistant.memory

/** Conservative explicit contradiction handling. No inferred relationships or negation guessing. */
object MemoryRevision {
    fun subject(value: String): String = MemoryLearning.key(value)
        .replace(Regex("^(?:el|la|los|las|un|una)\\s+"), "")

    fun supersedes(previous: String, current: String): Boolean {
        val old = MemoryLearning.facts(previous)
        val next = MemoryLearning.facts(current)
        if (old.isEmpty() || next.isEmpty()) return false
        if (old.any { (key, value) -> next[key]?.let { subject(it) != subject(value) } == true }) return true
        if (old["likes"]?.let { subject(it) == subject(next["dislikes"].orEmpty()) } == true) return true
        if (old["dislikes"]?.let { subject(it) == subject(next["likes"].orEmpty()) } == true) return true
        if (old["no_longer_drinks"]?.let { subject(it) == subject(next["drinks"].orEmpty()) } == true) return true
        val noLonger = next["no_longer_drinks"] ?: return false
        return listOfNotNull(old["likes"], old["drinks"]).any { subject(it) == subject(noLonger) }
    }

    fun removedKeys(previous: Map<String, String>, updates: Map<String, String>): Set<String> = buildSet {
        if (subject(previous["likes"].orEmpty()).isNotBlank() &&
            listOfNotNull(updates["dislikes"], updates["no_longer_drinks"]).any { subject(it) == subject(previous["likes"].orEmpty()) }) add("likes")
        if (updates["likes"]?.let { subject(it) == subject(previous["dislikes"].orEmpty()) } == true) add("dislikes")
        if (updates["no_longer_drinks"]?.let { subject(it) == subject(previous["drinks"].orEmpty()) } == true) add("drinks")
        if (updates["drinks"]?.let { subject(it) == subject(previous["no_longer_drinks"].orEmpty()) } == true) add("no_longer_drinks")
    }
}
