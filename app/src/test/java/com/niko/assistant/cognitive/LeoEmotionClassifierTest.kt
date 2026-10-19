package com.niko.assistant.cognitive

import org.junit.Assert.*
import org.junit.Test

class LeoEmotionClassifierTest {
    @Test fun allSupportedLabelsRecognizeExplicitFirstPersonStatements() {
        val examples = listOf(
            "estoy muy feliz", "me siento triste", "estoy entusiasmada", "me siento frustrada",
            "estoy enojado", "tengo mucho miedo", "estoy asombrada", "me siento confundido",
            "siento mucha vergüenza", "me siento insegura", "tengo curiosidad", "estoy aburrida",
            "estoy bastante cansado", "estoy preocupada", "tengo risa", "siento cariño",
            "estoy orgulloso", "me siento satisfecha", "estoy decepcionada", "estoy nervioso"
        )
        LeoEmotionClassifier.Emotion.values().zip(examples).forEach { (label, input) ->
            assertEquals(input, label, LeoEmotionClassifier.classify(input)?.emotion)
        }
    }
    @Test fun aMentionAboutOthersOrWorkIsNotTheUsersEmotion() {
        listOf("tengo un amigo triste", "estoy trabajando con alguien enojado",
            "siento que ella está triste", "tengo un libro sobre miedo", "estoy viendo una película triste",
            "me siento como el personaje triste", "si estoy triste avisame", "estoy triste?",
            "¿Estoy triste?", "no estoy triste", "estoy sin miedo", "estoy triste pero feliz",
            "estoy triste aunque alegre", "me siento quizás triste", "pon cara de triste",
            "él está enojado", "me dijo: estoy triste", "estoy pensando en la alegría").forEach {
            assertNull(it, LeoEmotionClassifier.classify(it))
        }
    }
    @Test fun wakeNameAndFollowingTaskDoNotHideAnAssertedFeeling() {
        assertEquals(LeoEmotionClassifier.Emotion.FRUSTRATION,
            LeoEmotionClassifier.classify("LEO, me siento frustrado, ¿me ayudás con este error?")?.emotion)
        assertTrue(LeoEmotionClassifier.needsGentleResponse("Estoy preocupada; revisá este informe"))
        assertEquals(LeoEmotionClassifier.Emotion.FEAR, LeoEmotionClassifier.classify("Me da miedo equivocarme")?.emotion)
    }
    @Test fun laterMentionsCannotChangeTheInitialFeeling() {
        assertEquals(LeoEmotionClassifier.Emotion.JOY,
            LeoEmotionClassifier.classify("estoy feliz por mi amigo triste")?.emotion)
        assertEquals(LeoEmotionClassifier.Emotion.TIREDNESS,
            LeoEmotionClassifier.classify("estoy cansado, mi amiga está muy feliz")?.emotion)
        assertNull(LeoEmotionClassifier.classify("estoy trabajando, mi amiga está triste"))
    }
}
