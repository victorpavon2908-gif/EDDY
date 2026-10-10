package com.niko.assistant.learning

import org.junit.Assert.*
import org.junit.Test

class LeoAdaptiveTrainerTest {
    @Test fun candidateNeverMutatesActiveWeightsAndCannotReduceProbeScore() {
        val active = OnlineIntentNetwork.pretrained(7)
        val original = active.encode()
        val result = LeoAdaptiveTrainer.propose(active, listOf("buscame datos nuevos sobre astronomia" to LearnedIntent.SEARCH))
        assertArrayEquals(original, active.encode())
        assertTrue("A non-regressing supervised update should be retained", result.accepted)
        println("ROUTING_EVIDENCE before=${result.before.correctByClass} after=${result.after.correctByClass} accepted=${result.accepted}")
        if (result.accepted) {
            assertTrue(result.network.observations > active.observations)
            LearnedIntent.entries.forEach { assertTrue(result.after.correctByClass.getValue(it) >= result.before.correctByClass.getValue(it)) }
            assertNotNull(OnlineIntentNetwork.decode(result.network.encode()))
        } else assertSame(active, result.network)
    }

    @Test fun secretsCannotCreateAnAcceptedUpdate() {
        val active = OnlineIntentNetwork.pretrained(7)
        val result = LeoAdaptiveTrainer.propose(active, listOf("mi contraseña es abc123" to LearnedIntent.MEMORY))
        assertFalse(result.accepted)
        assertSame(active, result.network)
    }

    @Test fun harmfulRepeatedRelabelingCannotDegradeAnyRoutingClass() {
        var active = OnlineIntentNetwork.pretrained(7)
        val baseline = LeoAdaptiveTrainer.evaluate(active)
        var rejected = 0
        repeat(25) {
            val result = LeoAdaptiveTrainer.propose(active, listOf("enciende la linterna del telefono" to LearnedIntent.SEARCH))
            if (!result.accepted) rejected++
            active = result.network
        }
        assertTrue("A contradictory label must eventually be rejected", rejected > 0)
        val final = LeoAdaptiveTrainer.evaluate(active)
        LearnedIntent.entries.forEach { assertTrue(final.correctByClass.getValue(it) >= baseline.correctByClass.getValue(it)) }
    }
}
