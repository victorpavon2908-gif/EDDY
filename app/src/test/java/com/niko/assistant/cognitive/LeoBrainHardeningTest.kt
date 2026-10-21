package com.niko.assistant.cognitive

import org.junit.Assert.*
import org.junit.Test

class LeoBrainHardeningTest {
    @Test fun naturalFirstPersonEmotionStillAbstainsOnNegationAndQuotes() {
        for (input in listOf("ando triste", "me encuentro muy cansado", "me siento un poquito preocupado")) {
            assertTrue(input, LeoEmotionClassifier.needsGentleResponse(input))
        }
        for (input in listOf("ando feliz", "no ando triste", "mi amigo anda triste", "pon cara de triste", "ando triste pero ya se me pasó", "ando quizás triste")) {
            assertFalse(input, LeoEmotionClassifier.needsGentleResponse(input))
        }
    }
    @Test fun repetitionDoesNotRewritePreferenceAndNaturalQueryRetrievesIt() {
        var writes = 0
        val values = mutableMapOf<String, String>()
        val memory = LeoPersonalMemory(object : LeoPersonalMemory.Store {
            override fun read(key: String) = values[key]
            override fun write(key: String, value: String) { writes++; values[key] = value }
            override fun delete(key: String) { values.remove(key) }
        })
        memory.reply("mi bebida favorita es café")
        memory.reply("mi bebida favorita es el café")
        assertEquals(1, writes)
        assertTrue(memory.reply("¿Qué bebida me gusta más?")!!.contains("café"))
        memory.reply("mi bebida favorita es té")
        assertEquals(2, writes)
        assertFalse(memory.reply("¿Qué bebida me gusta más?")!!.contains("café"))
    }
    @Test fun simpleCodeTemplateDoesNotPretendToMeetAdvancedRequirements() {
        val code = LeoCodeIntelligence()
        assertNotNull(code.project("crea una calculadora en Python"))
        for (input in listOf("crea una calculadora científica en Python", "crea una calculadora con interfaz en Python",
            "crea una calculadora financiera en Python", "no quiero que crees nada, crea una calculadora Python es un ejemplo")) {
            assertNull(input, code.project(input))
        }
    }
}
