package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechProsodyTest {
    @Test fun shortRepliesStayInOneFastBlock() {
        assertEquals(listOf("Listo. Ya abrí WhatsApp."), SpeechProsody.fastStartChunks("Listo. Ya abrí WhatsApp."))
    }

    @Test fun longRepliesUseAShortFirstBlockAndLargerFollowingBlocks() {
        val text = "Ya encontré la información que me pediste y te la voy a explicar de forma clara. Después puedo seguir con el siguiente punto sin que tengás que volver a llamarme."
        val chunks = SpeechProsody.fastStartChunks(text, firstLimit = 48, nextLimit = 96)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.first().length <= 48)
        assertTrue(chunks.drop(1).all { it.length <= 96 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test fun mediumRepliesDoNotCreateATinyTail() {
        val text = "Esta respuesta es suficientemente corta para sonar continua y sin un corte final raro."
        val chunks = SpeechProsody.fastStartChunks(text, firstLimit = 48, nextLimit = 96)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks.single())
    }
}
