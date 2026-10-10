package com.niko.assistant.voice

import android.Manifest
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LeoPlatformDuplexTest {
    private lateinit var engine: LeoPlatformVoiceEngine
    private val commands = mutableListOf<String>()
    private var interrupts = 0
    private var stops = 0
    private var fatalErrors = 0
    private val interrupt: () -> Unit = { interrupts++ }
    private val stop: () -> Unit = { stops++ }
    private fun idle(ms: Long = 250) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun phrase(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
    private fun current() = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = "test.recognizer"; name = "TestRecognizer" }
        })
        LeoRealtimeTurnBus.beginSpeechReference("test", "El reporte está casi listo")
        LeoRealtimeTurnBus.registerTurnInterrupter(interrupt)
        LeoRealtimeTurnBus.registerSpeechStopper(stop)
        engine = LeoPlatformVoiceEngine(context, onWake = {}, onCommand = commands::add, onFatal = { fatalErrors++ })
        assertTrue(engine.start())
        idle()
        current().triggerOnReadyForSpeech(Bundle())
    }
    @After fun cleanup() {
        engine.stop(); idle()
        LeoRealtimeTurnBus.unregisterTurnInterrupter(interrupt)
        LeoRealtimeTurnBus.unregisterSpeechStopper(stop)
        LeoRealtimeTurnBus.endSpeechReference("test")
    }
    @Test fun partialWakeStopsProducerAndTtsOnceAndKeepsInlineCommand() {
        val recognizer = current()
        engine.setAssistantSpeaking(true)
        recognizer.triggerOnPartialResults(phrase("Leo"))
        recognizer.triggerOnPartialResults(phrase("Leo buscá otra cosa"))
        assertEquals(1, interrupts); assertEquals(1, stops)
        recognizer.triggerOnResults(phrase("Leo buscá otra cosa"))
        assertEquals(listOf("buscá otra cosa"), commands)
    }
    @Test fun wakeAloneOpensNextCommandSession() {
        engine.setAssistantSpeaking(true)
        current().triggerOnResults(phrase("Leo"))
        idle()
        current().triggerOnResults(phrase("continuemos con el reporte"))
        assertEquals(listOf("continuemos con el reporte"), commands)
    }
    @Test fun echoNeverBecomesCommandWhileSpeaking() {
        engine.setAssistantSpeaking(true)
        current().triggerOnPartialResults(phrase("El reporte está casi listo"))
        current().triggerOnResults(phrase("El reporte está casi listo"))
        assertEquals(0, interrupts); assertTrue(commands.isEmpty())
        idle(); assertFalse(current().isDestroyed)
    }
    @Test fun completedPlaybackRejectsOldSessionCallbacks() {
        val old = current()
        engine.setAssistantSpeaking(true)
        engine.setAssistantSpeaking(false, true)
        idle()
        old.triggerOnResults(phrase("Leo borrá mi memoria"))
        assertTrue(commands.isEmpty())
        current().triggerOnResults(phrase("qué hora es"))
        assertEquals(listOf("qué hora es"), commands)
    }
    @Test fun interruptionAlsoWorksDuringReasoningAndAfterNoMatch() {
        engine.setAssistantBusy(true)
        current().triggerOnPartialResults(phrase("Leo"))
        current().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle()
        current().triggerOnResults(phrase("mejor buscá otra cosa"))
        assertEquals(1, interrupts)
        assertEquals(listOf("mejor buscá otra cosa"), commands)
    }
    @Test fun missingReadinessFallsBackInsteadOfRestartingForever() {
        current().triggerOnError(SpeechRecognizer.ERROR_NO_MATCH)
        idle(14_000)
        assertFalse(engine.isRunning)
        assertEquals(1, fatalErrors)
    }
    @Test fun stopDiscardsLateResults() {
        val old = current()
        engine.stop()
        old.triggerOnResults(phrase("Leo abrí WhatsApp"))
        assertTrue(commands.isEmpty())
    }
}
