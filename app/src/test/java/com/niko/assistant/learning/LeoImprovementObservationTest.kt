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
}
