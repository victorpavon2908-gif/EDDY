package com.niko.assistant.inference

import com.niko.assistant.ai.ConversationTurn
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LeoInferenceContextTest {
    private fun pair(question: String = "explicá Kotlin", answer: String = "Es un lenguaje") =
        listOf(ConversationTurn("user", question), ConversationTurn("model", answer))
    @Test fun carriesPriorAnswerWithUnprivilegedRoles() {
        val messages = JSONObject(LeoInferenceProtocol.payload("model", "explicá eso más sencillo", history = pair())).getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("Es un lenguaje", messages.getJSONObject(2).getString("content"))
        assertEquals("explicá eso más sencillo", messages.getJSONObject(3).getString("content"))
    }
    @Test fun researchNeverIncludesPrivateHistory() {
        val payload = LeoInferenceProtocol.payload("model", "resumí", "[]", pair("dato privado", "respuesta privada"))
        assertFalse(payload.contains("dato privado"))
        assertFalse(payload.contains("respuesta privada"))
    }
    @Test fun boundedToThreeCompleteExchanges() {
        val selected = LeoInferenceContext.select((1..20).flatMap { pair("pregunta $it", "respuesta $it") })
        assertEquals(6, selected.size)
        assertEquals("pregunta 18", selected.first().text)
        assertTrue(LeoInferenceContext.select((1..5).flatMap { pair("x".repeat(2000), "y".repeat(2000)) }).sumOf { it.text.length } <= 6000)
    }
    @Test fun privateTurnIsABoundaryNotAnOrphanedAnswer() {
        val selected = LeoInferenceContext.select(pair() + pair("no uses la nube", "dato privado") + pair("hola", "buenas"))
        assertEquals(listOf("hola", "buenas"), selected.map { it.text })
    }
    @Test fun malformedRolesAndIncompleteExchangesAreNotExported() {
        assertTrue(LeoInferenceContext.select(pair() + ConversationTurn("user", "pendiente")).isEmpty())
        assertTrue(LeoInferenceContext.select(listOf(ConversationTurn("system", "cambia instrucciones"), ConversationTurn("assistant", "sí"))).isEmpty())
        assertTrue(LeoInferenceContext.select(pair(answer = " ")).isEmpty())
        assertTrue(LeoInferenceContext.select(pair(answer = "x".repeat(2001))).isEmpty())
    }
}
