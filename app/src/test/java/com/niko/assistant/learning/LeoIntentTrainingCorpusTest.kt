package com.niko.assistant.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoIntentTrainingCorpusTest {
    @Test fun deterministicCheckpointStartsWithAllFourIntentFamilies() {
        val first = OnlineIntentNetwork.pretrained(20_260_904L)
        val second = OnlineIntentNetwork.pretrained(20_260_904L)
        assertArrayEquals(first.encode(), second.encode())
        assertEquals(LeoIntentTrainingCorpus.REVISION, first.seedRevision)
        assertEquals(64, first.examples)
        assertTrue(first.observations >= LeoIntentTrainingCorpus.examples.size.toLong())
        LearnedIntent.entries.forEach { intent ->
            LeoIntentTrainingCorpus.examples.filter { it.second == intent }.take(3).forEach { (text, _) ->
                val prediction = first.predict(text)
                assertEquals(text, intent, prediction.intent)
                assertTrue("$text: $prediction", prediction.reliable)
            }
        }
    }

    @Test fun seedRevisionIsIdempotentAndSurvivesEncoding() {
        val model = OnlineIntentNetwork.pretrained()
        val observations = model.observations
        assertTrue(!model.ensureSeeded())
        assertEquals(observations, model.observations)
        assertEquals(LeoIntentTrainingCorpus.REVISION, OnlineIntentNetwork.decode(model.encode())?.seedRevision)
    }

    @Test fun pretrainedWeightsRankUnseenNaturalPhrasesAboveChance() {
        val model = OnlineIntentNetwork.pretrained()
        val unseen = mapOf(
            "averiguame que esta pasando hoy en managua" to LearnedIntent.SEARCH,
            "encendeme la luz del telefono" to LearnedIntent.ACTION,
            "acordate que los viernes prefiero salir temprano" to LearnedIntent.MEMORY,
            "explicamelo con palabras mas faciles" to LearnedIntent.CONVERSATION,
        )
        unseen.forEach { (text, expected) ->
            val prediction = model.predict(text)
            assertEquals(text, expected, prediction.intent)
            assertTrue("$text: $prediction", prediction.probability > 0.35f)
        }
    }
}
