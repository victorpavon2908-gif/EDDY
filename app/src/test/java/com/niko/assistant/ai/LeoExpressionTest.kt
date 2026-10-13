package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class LeoExpressionTest {
    @Test fun naturalCommandsShareOneExpressionVocabulary() {
        assertEquals(LeoExpression.SURPRISE, LeoExpression.requested("LEO, hacé una expresión de asombro"))
        assertEquals(LeoExpression.FEAR, LeoExpression.requested("Poné cara de miedo"))
        assertEquals(LeoExpression.LAUGH, LeoExpression.requested("Hacé tal exprecion risa"))
        assertEquals(LeoExpression.WRY, LeoExpression.requested("Haz una cara de burla, porfa"))
        assertEquals(LeoExpression.ANGRY, LeoExpression.requested("Ponete cara de enojo"))
        assertEquals(LeoExpression.SAD, LeoExpression.requested("Mostrá una expresión de tristeza"))
    }
    @Test fun doesNotMistakeDistressOrQuestionsForPerformanceRequests() {
        for (input in listOf("Tengo miedo", "Estoy triste", "No hagas cara de burla", "Qué significa asombro", "Mi hijo se ríe")) {
            assertNull(LeoExpression.requested(input))
            assertNull(LeoPlayfulDialogue().reply(input))
        }
    }
    @Test fun unknownExpressionsAreNotPretended() {
        val reply = LeoPlayfulDialogue().reply("Hacé cara de nostalgia").orEmpty()
        assertTrue(reply.contains("todavía no"))
    }
    @Test fun selfJokesVaryAndSeriousModeStopsGrumpyGreetings() {
        val dialogue = LeoPlayfulDialogue()
        assertNotEquals(dialogue.reply("Burlate de mí"), dialogue.reply("Burlate de mí"))
        assertNotNull(dialogue.reply("Respondeme de malas"))
        assertTrue(dialogue.greeting().contains("modo ahorro"))
        dialogue.reply("Sin bromas")
        assertEquals("Decime, te escucho.", dialogue.greeting())
    }
    @Test fun ordinaryTasksKeepTheirOwnRouting() {
        assertNull(LeoPlayfulDialogue().reply("Abrí la calculadora"))
        assertNull(LeoPlayfulDialogue().reply("Buscá noticias sobre humor"))
    }
}
