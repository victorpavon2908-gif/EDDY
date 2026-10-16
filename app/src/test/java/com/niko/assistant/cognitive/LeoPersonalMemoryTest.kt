package com.niko.assistant.cognitive

import org.junit.Assert.*
import org.junit.Test

class LeoPersonalMemoryTest {
    private class Store : LeoPersonalMemory.Store {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun delete(key: String) { values.remove(key) }
    }
    @Test fun preferenceIsReplacedRatherThanAccumulated() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("LEO, mi comida favorita es la pizza")
        assertTrue(memory.reply("¿Qué comida me gusta más?")!!.contains("pizza"))
        memory.reply("LEO, ya no me gusta la pizza, ahora prefiero la lasaña")
        assertEquals(mapOf("food" to "la lasaña"), store.values)
        assertFalse(memory.reply("¿Cuál es mi comida favorita?")!!.contains("pizza"))
    }
    @Test fun suggestionsUseOnlyTheCurrentKnownPreference() {
        val memory = LeoPersonalMemory(Store())
        assertTrue(memory.reply("que me recomiendas comer")!!.contains("Todavía"))
        memory.reply("mi comida favorita es pizza")
        memory.reply("ya no me gusta pizza ahora prefiero lasaña")
        val suggestion = memory.reply("que me recomiendas comer")!!
        assertTrue(suggestion.contains("lasaña"))
        assertFalse(suggestion.contains("pizza"))
    }
    @Test fun updatesDrinksAndPreservesSpelling() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("Mi bebida favorita es café")
        memory.reply("ya no me gusta el café, ahora prefiero té de limón")
        assertEquals("té de limón", store.values["drink"])
    }
    @Test fun retractsPreferenceWithoutRequiringReplacement() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es la pizza")
        memory.reply("ya no me gusta la pizza")
        assertFalse(store.values.containsKey("food"))
    }
    @Test fun ambiguousCorrectionNeedsClarification() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es chocolate")
        memory.reply("mi bebida favorita es chocolate")
        assertTrue(memory.reply("ya no me gusta chocolate ahora prefiero vainilla")!!.contains("¿"))
        assertEquals("chocolate", store.values["food"])
        assertEquals("chocolate", store.values["drink"])
    }
    @Test fun unrelatedCorrectionCannotOverwriteFood() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es pizza")
        assertNull(memory.reply("ya no me gusta Madrid, ahora prefiero Managua"))
        assertEquals("pizza", store.values["food"])
    }
    @Test fun forgetAndUnknownAreExplicit() {
        val memory = LeoPersonalMemory(Store())
        memory.reply("mi bebida favorita es té")
        memory.reply("olvida mi bebida favorita")
        assertTrue(memory.reply("cual es mi bebida favorita")!!.contains("Todavía"))
    }
    @Test fun questionsAndSensitiveValuesAreNotFacts() {
        assertNull(LeoPersonalMemory.parse("¿Mi comida favorita es pizza?"))
        assertNull(LeoPersonalMemory.parse("mi bebida favorita es contraseña secreta"))
        assertNull(LeoPersonalMemory.parse("mi comida favorita es 123456"))
    }
}
