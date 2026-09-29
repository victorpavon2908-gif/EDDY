package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class OfflineVoiceSelectorTest {
    private fun voice(name: String, network: Boolean = false, country: String = "MX", features: Set<String> = emptySet()) =
        OfflineVoiceSelector.Candidate(name, "es", country, 400, 200, network, features)

    @Test fun savedOfflineVoiceWinsEvenWhenANewHigherRankedVoiceAppears() {
        val voices = listOf(voice("old", country = "ES"), voice("new", country = "NI"))
        assertEquals("old", OfflineVoiceSelector.ranked(voices, "old").first().name)
    }

    @Test fun enumerationOrderDoesNotChangeTheChosenVoice() {
        val voices = listOf(voice("b"), voice("a"))
        assertEquals(OfflineVoiceSelector.ranked(voices, null), OfflineVoiceSelector.ranked(voices.reversed(), null))
    }

    @Test fun unavailableOrNetworkVoicesCannotChangeOutputWithConnectivity() {
        val voices = listOf(voice("network", true), voice("missing", features = setOf("notInstalled")), voice("local"))
        assertEquals(listOf("local"), OfflineVoiceSelector.ranked(voices, "network").map { it.name })
        assertTrue(OfflineVoiceSelector.ranked(listOf(voice("network", true)), null).isEmpty())
    }

    @Test fun femaleAndWomanAreNotMistakenForMaleAndMan() {
        assertEquals("z-male", OfflineVoiceSelector.ranked(listOf(voice("a-female"), voice("z-male")), null).first().name)
        assertEquals("z-man", OfflineVoiceSelector.ranked(listOf(voice("a-woman"), voice("z-man")), null).first().name)
    }
}
