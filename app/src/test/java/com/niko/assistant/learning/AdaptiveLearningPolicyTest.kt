package com.niko.assistant.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveLearningPolicyTest {
    @Test fun refusesSecretsAndRedactsLongNumbersBeforeTraining() {
        assertNull(AdaptiveLearningPolicy.example("mi contraseña es supersecreta"))
        assertNull(AdaptiveLearningPolicy.example("guardá mi API key abc123"))
        assertNull(AdaptiveLearningPolicy.example("Llamá al 88881234 mañana"))
        assertNull(AdaptiveLearningPolicy.example("Llamá al 8888-1234 mañana"))
        assertNull(AdaptiveLearningPolicy.example("mi número es 8888 1234"))
        assertNull(AdaptiveLearningPolicy.example("mi correo es victor@example.com"))
        assertEquals("pone una alarma a las numero", AdaptiveLearningPolicy.example("Poné una alarma a las 0800"))
    }
}
