package com.niko.assistant.voice

import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NikoTextToSpeechRecoveryTest {
    private class Engine : LeoSpeechEngine {
        lateinit var listener: UtteranceProgressListener
        val spoken = mutableListOf<Pair<String, String>>()
        var fail = false
        var closed = false
        override fun prepare(savedVoice: String?, savedEngine: String?) = LeoSpeechEngine.Selection("fixture", "es-fixture", "es-NI")
        override fun listen(listener: UtteranceProgressListener): Boolean { this.listener = listener; return true }
        override fun setProsody(speed: Float, pitch: Float) = true
        override fun speak(text: String, queueMode: Int, id: String): Int {
            spoken += text to id
            return if (fail) TextToSpeech.ERROR else TextToSpeech.SUCCESS
        }
        override fun stop() {}
        override fun shutdown() { closed = true }
    }
    private class Fixture(var status: Int = TextToSpeech.SUCCESS, var callbacks: Boolean = true) {
        val engines = mutableListOf<Engine>()
        val ready = mutableListOf<Boolean>()
        val voice = NikoTextToSpeech(RuntimeEnvironment.getApplication(), factory = { _, _, callback ->
            Engine().also { engines += it; if (callbacks) callback(status) }
        }, onReady = { ready += it })
    }
    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun queueFailureRecoversWithoutReplayingObsoleteSpeech() {
        val f = Fixture()
        try {
            idle(); assertTrue(f.voice.isReady)
            f.engines.first().fail = true
            assertFalse(f.voice.speak("Primera respuesta"))
            assertFalse(f.voice.isReady)
            idle(500)
            assertTrue(f.voice.isReady)
            assertEquals(2, f.engines.size)
            assertTrue(f.engines.first().closed)
            assertTrue(f.engines.last().spoken.isEmpty())
            assertTrue(f.voice.speak("Respuesta nueva"))
            assertEquals("Respuesta nueva", f.engines.last().spoken.single().first)
        } finally { f.voice.shutdown(); idle() }
    }
    @Test fun asyncErrorRecoversAndOldEngineCallbacksCannotBreakTheNewOne() {
        val f = Fixture()
        try {
            idle(); assertTrue(f.voice.speak("Respuesta antigua"))
            val old = f.engines.first()
            val id = old.spoken.first().second
            old.listener.onError(id); idle(); idle(500)
            assertTrue(f.voice.isReady)
            assertTrue(f.voice.speak("Respuesta actual"))
            old.listener.onError(id); old.listener.onDone(id); idle()
            assertTrue(f.voice.isReady)
            assertEquals(2, f.engines.size)
        } finally { f.voice.shutdown(); idle() }
    }
    @Test fun initializationWithoutCallbackTimesOutAndRecovers() {
        val f = Fixture(callbacks = false)
        try {
            idle(10_000)
            assertFalse(f.voice.isReady)
            f.callbacks = true
            idle(500)
            assertTrue(f.voice.isReady)
            assertEquals(2, f.engines.size)
        } finally { f.voice.shutdown(); idle() }
    }
    @Test fun retriesAreBoundedAndShutdownCancelsFutureRecovery() {
        val f = Fixture(status = TextToSpeech.ERROR)
        idle(20_000)
        assertFalse(f.voice.isReady)
        assertEquals(4, f.engines.size)
        f.voice.shutdown()
        idle(60_000)
        assertEquals(4, f.engines.size)
        assertFalse(f.voice.ensureReady())
    }
}
