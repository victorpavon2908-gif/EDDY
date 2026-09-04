package com.niko.assistant.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class OnlineIntentNetworkTest {
    private val samples = mapOf(
        LearnedIntent.SEARCH to listOf("noticias de nicaragua hoy", "precio actual del dolar", "clima en managua manana"),
        LearnedIntent.ACTION to listOf("enciende la linterna", "abre la camara", "pone una alarma a las ocho"),
        LearnedIntent.MEMORY to listOf("recuerda que me gusta el cafe", "mi nombre es manuel", "prefiero respuestas cortas"),
        LearnedIntent.CONVERSATION to listOf("hola como estas", "buenas noches", "muchas gracias amigo"),
    )

    @Test fun newbornNetworkAbstainsAndIsActuallyRandomlyInitialized() {
        val first = OnlineIntentNetwork(1)
        val second = OnlineIntentNetwork(2)
        assertFalse(first.encode().contentEquals(second.encode()))
        assertFalse(first.predict("noticias de hoy").reliable)
        assertEquals(0L, first.observations)
    }

    @Test fun supervisedUpdatesLearnAllFourClassesAndSurviveRestart() {
        val model = OnlineIntentNetwork(7)
        val before = model.encode()
        repeat(25) { samples.forEach { (label, texts) -> texts.forEach { model.learn(it, label) } } }
        assertFalse(before.contentEquals(model.encode()))
        val restored = OnlineIntentNetwork.decode(model.encode())!!
        samples.forEach { (label, texts) -> texts.forEach { text ->
            val prediction = restored.predict(text)
            assertEquals(text, label, prediction.intent)
            assertTrue("$text: $prediction", prediction.reliable)
            assertEquals(model.predict(text), prediction)
        } }
    }

    @Test fun replayProtectsEarlierClassesDuringRepeatedNewCommands() {
        val model = OnlineIntentNetwork(7)
        repeat(25) { samples.forEach { (label, texts) -> texts.forEach { model.learn(it, label) } } }
        repeat(160) { model.learn("enciende la linterna", LearnedIntent.ACTION) }
        samples.getValue(LearnedIntent.SEARCH).forEach { assertEquals(LearnedIntent.SEARCH, model.predict(it).intent) }
        assertTrue(model.examples <= 64)
        assertFalse(model.predict("").reliable)
    }

    @Test fun corruptedCheckpointIsRejectedWithoutPretendingItWasLearned() {
        val bytes = OnlineIntentNetwork(7).encode()
        bytes[32] = (bytes[32].toInt() xor 7).toByte()
        assertNull(OnlineIntentNetwork.decode(bytes))
        assertNull(OnlineIntentNetwork.decode(byteArrayOf(1, 2, 3)))
    }

    @Test fun credentialsAndLongPrivateNumbersNeverBecomeTrainingReplay() {
        val model = OnlineIntentNetwork(7)
        model.learn("mi contraseña es abc123", LearnedIntent.MEMORY)
        model.learn("llama al 88881234", LearnedIntent.ACTION)
        assertEquals(0L, model.observations)
        assertEquals(0, model.examples)
    }

    @Test fun versionOneCheckpointsRemainReadableForExistingInstallations() {
        val model = OnlineIntentNetwork(7)
        model.learn("noticias de hoy", LearnedIntent.SEARCH)
        val currentBody = model.encode().dropLast(8).toByteArray()
        val legacyBody = ByteArray(currentBody.size - 4)
        currentBody.copyInto(legacyBody, destinationOffset = 0, startIndex = 0, endIndex = 16)
        currentBody.copyInto(legacyBody, destinationOffset = 16, startIndex = 20)
        ByteBuffer.wrap(legacyBody).putInt(4, 1)
        val legacy = ByteArrayOutputStream().also { output ->
            output.write(legacyBody)
            DataOutputStream(output).writeLong(CRC32().apply { update(legacyBody) }.value)
        }.toByteArray()

        val restored = OnlineIntentNetwork.decode(legacy)
        assertEquals(model.observations, restored?.observations)
        assertEquals(0, restored?.seedRevision)
    }
}
