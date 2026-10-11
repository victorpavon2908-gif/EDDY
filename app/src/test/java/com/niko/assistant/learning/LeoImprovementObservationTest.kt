package com.niko.assistant.learning

import com.niko.assistant.programming.NikoCodeAgent
import com.niko.assistant.selfupgrade.NikoSelfUpgradeManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoImprovementObservationTest {
    @Test fun observationsDoNotPretendToBeTestedOrActivatedCodeAndCanBeForgotten() {
        val agent = NikoCodeAgent(RuntimeEnvironment.getApplication())
        agent.clearEvolutionHistory()
        repeat(2) { agent.registerImprovementObservation("Nueva capacidad", "Requiere implementación", "No pude completarla", "test") }
        val item = agent.evolutionHistory().single()
        assertEquals(NikoSelfUpgradeManager.State.PROPOSED, item.state)
        assertEquals("", item.testReport)
        assertTrue(item.summary.startsWith("Pendiente de implementación"))
        agent.clearEvolutionHistory()
        assertTrue(agent.evolutionHistory().isEmpty())
    }
    @Test fun transitionsRequireOrderIntactPayloadAndObservedActivation() {
        val context = RuntimeEnvironment.getApplication() as android.content.Context
        val manager = NikoSelfUpgradeManager(context)
        manager.clear()
        val item = manager.propose("Test", "Candidate", "previous", "actual candidate")
        assertFalse(manager.markReady(item.id, "Report"))
        assertFalse(manager.markActivated(item.id))
        assertTrue(manager.markTesting(item.id))
        assertFalse(manager.markReady(item.id, ""))
        assertTrue(manager.markReady(item.id, "Validator passed"))
        assertFalse(manager.markActivated(item.id))
        assertTrue(manager.markActivated(item.id, item.candidateVersion))
        assertFalse(manager.rollback(item.id))
        assertTrue(manager.rollback(item.id, "previous"))
        assertFalse(manager.markTesting(item.id))
        val corrupt = manager.propose("Corrupt", "Candidate", "previous", "actual candidate")
        java.io.File(context.filesDir, com.niko.assistant.compat.UpgradeIdentity.evolutionDirectory + "/${corrupt.id}.candidate.txt").writeText("tampered")
        assertFalse(manager.markTesting(corrupt.id))
        manager.clear()
    }

    @Test fun proposalHistoryAndPayloadsAreBoundedAndIdsNeverCollide() {
        val context = RuntimeEnvironment.getApplication() as android.content.Context
        val manager = NikoSelfUpgradeManager(context)
        manager.clear()
        val ids = (1..105).map { manager.propose("Same", "Same", "old", "same payload").id }
        assertEquals(105, ids.distinct().size)
        assertEquals(100, manager.history().size)
        val files = java.io.File(context.filesDir, com.niko.assistant.compat.UpgradeIdentity.evolutionDirectory).listFiles().orEmpty()
        assertEquals(100, files.count { it.name.endsWith(".candidate.txt") })
        manager.clear()
    }
}
