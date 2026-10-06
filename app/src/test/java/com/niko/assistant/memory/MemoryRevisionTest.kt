package com.niko.assistant.memory

import org.junit.Assert.*
import org.junit.Test

class MemoryRevisionTest {
    @Test fun explicitNegationReplacesSamePreference() { assertTrue(MemoryRevision.supersedes("Me gusta el café", "No me gusta café")) }
    @Test fun unrelatedPreferenceDoesNotNegateCoffee() { assertFalse(MemoryRevision.supersedes("Me gusta café", "No me gusta el té")) }
    @Test fun stoppedConsumptionRetiresOlderCoffeeClaim() { assertTrue(MemoryRevision.supersedes("Me gusta el café", "Ya no tomo café")) }
    @Test fun questionDoesNotTeachNegation() { assertFalse(MemoryRevision.supersedes("Me gusta café", "¿Ya no tomo café?")) }
    @Test fun updatedLocationSupersedesOldEpisode() { assertTrue(MemoryRevision.supersedes("Vivo en Managua", "Vivo en León")) }
    @Test fun currentFactsRemoveOppositeSlot() { assertEquals(setOf("likes"), MemoryRevision.removedKeys(mapOf("likes" to "el café"), mapOf("dislikes" to "café"))) }
}
