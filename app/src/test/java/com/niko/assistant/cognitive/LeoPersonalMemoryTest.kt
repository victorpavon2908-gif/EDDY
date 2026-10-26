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
    @Test fun questionsAndQualifiedForgetCommandsNeverDelete() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es pizza")
        listOf("¿Olvida mi comida favorita?", "olvida mi comida favorita pero no todavía",
            "olvida mi comida favorita es un ejemplo", "no olvides mi comida favorita").forEach {
            memory.reply(it)
            assertEquals(it, "pizza", store.values["food"])
        }
        memory.reply("LEO, olvidá mi comida favorita, por favor")
        assertFalse(store.values.containsKey("food"))
    }
    @Test fun uncertainAndCompoundFactsAskWithoutChangingMemory() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es pizza")
        listOf("mi comida favorita es quizás lasaña", "mi comida favorita es arroz pero no estoy seguro",
            "mi comida favorita es pasta y mi bebida favorita es café").forEach {
            assertTrue(memory.reply(it)!!.contains("No cambié"))
            assertNull(LeoPersonalMemory.parse(it))
            assertTrue(LeoPersonalMemory.isStatement(it))
            assertEquals(mapOf("food" to "pizza"), store.values)
        }
    }
    @Test fun unsafeOrUncertainReplacementPreservesPreviousValue() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi bebida favorita es café")
        listOf("ya no me gusta café ahora prefiero quizás té",
            "ya no me gusta café ahora prefiero contraseña secreta").forEach {
            assertTrue(memory.reply(it)!!.contains("No cambié"))
            assertEquals("café", store.values["drink"])
        }
    }
    @Test fun ordinaryCompoundFoodNamesRemainSupported() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi comida favorita es arroz y frijoles")
        assertEquals("arroz y frijoles", store.values["food"])
    }
    @Test fun learnsNewCategoriesAndUpdatesAliasesWithoutDuplicateCurrentFacts() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("LEO, recordá que mi pasatiempo favorito es dibujar")
        memory.reply("mi lenguaje de programación preferido es Kotlin")
        memory.reply("ahora mi pasatiempo preferido es cocinar")
        assertEquals("cocinar", store.values["hobby"])
        assertEquals("Kotlin", store.values["programming_language"])
        assertTrue(memory.reply("¿Cuál es mi pasatiempo favorito?")!!.contains("cocinar"))
        assertTrue(memory.reply("¿En qué lenguaje programo?")!!.contains("Kotlin"))
        assertFalse(memory.localContext().contains("dibujar"))
        assertTrue(memory.localContext().contains("Kotlin"))
        memory.reply("olvidá mi pasatiempo favorito")
        assertFalse(memory.localContext().contains("cocinar"))
        assertEquals(1, store.values.size)
    }
    @Test fun supportsNameColorAndSummaryQueries() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        memory.reply("mi nombre es Mario")
        memory.reply("mi color favorito es azul")
        assertEquals("Mario", store.values["name"])
        assertEquals("azul", store.values["color"])
        assertTrue(memory.reply("¿Cómo me llamo?")!!.contains("Mario"))
        val summary = memory.reply("¿Cuáles son mis preferencias?")!!
        assertTrue(summary.contains("Mario"))
        assertTrue(summary.contains("azul"))
    }
    @Test fun humorBoundaryIsExplicitPersistentAndReversible() {
        val store = Store()
        val memory = LeoPersonalMemory(store)
        assertNull(memory.humorAllowed())
        memory.reply("no hagas bromas sobre mí")
        assertEquals(false, LeoPersonalMemory(store).humorAllowed())
        memory.reply("¿Podés hacer bromas sobre mí?")
        assertEquals(false, memory.humorAllowed())
        memory.reply("permito bromas sobre mí")
        assertEquals(true, memory.humorAllowed())
        memory.reply("olvida mi preferencia de humor")
        assertNull(memory.humorAllowed())
    }
    @Test fun emotionalStatementsNeverBecomePersonalPreferences() {
        val store = Store(); val memory = LeoPersonalMemory(store)
        listOf("estoy triste", "tengo miedo", "mi amiga está enojada").forEach { memory.reply(it) }
        assertTrue(store.values.isEmpty())
    }
}
