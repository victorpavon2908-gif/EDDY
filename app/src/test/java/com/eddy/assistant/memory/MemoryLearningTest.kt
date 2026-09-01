package com.eddy.assistant.memory

import org.junit.Assert.*
import org.junit.Test

class MemoryLearningTest {
    @Test fun teachesExplicitNotesAndPreservesAccents() {
        assertEquals("prefiero respuestas cortas", MemoryLearning.note("recordá que prefiero respuestas cortas"))
        assertEquals("me gusta el café", MemoryLearning.note("recuerda que me gusta el café"))
        assertNull(MemoryLearning.note("no recuerdes que vivo en Managua"))
        assertNull(MemoryLearning.note("explicá cómo recordar que algo pasó"))
    }

    @Test fun teachesOnlyExplicitPersonalAnswers() {
        assertEquals(MemoryLearning.Lesson("mi bebida", "café sin azúcar"), MemoryLearning.lesson("cuando te pregunte mi bebida, respondé café sin azúcar"))
        assertNull(MemoryLearning.lesson("no aprendas que cuando te pregunte mi bebida responde café"))
        assertEquals(MemoryLearning.key("¿Mi bebida?"), MemoryLearning.key("mi bebida"))
        assertNotEquals(MemoryLearning.key("mi bebida"), MemoryLearning.key("no quiero mi bebida"))
    }

    @Test fun negationsAndQuestionsDoNotBecomePositiveFacts() {
        assertEquals(mapOf("dislikes" to "el café"), MemoryLearning.facts("no me gusta el café"))
        assertTrue(MemoryLearning.facts("no vivo en Managua").isEmpty())
        assertTrue(MemoryLearning.facts("él dice que me llamo Pedro").isEmpty())
        assertTrue(MemoryLearning.facts("¿me llamo Pedro?").isEmpty())
        assertEquals(mapOf("name" to "Manuel"), MemoryLearning.facts("me llamo Manuel"))
        assertEquals(mapOf("prefers" to "respuestas cortas"), MemoryLearning.facts("recordá que prefiero respuestas cortas"))
    }
}
