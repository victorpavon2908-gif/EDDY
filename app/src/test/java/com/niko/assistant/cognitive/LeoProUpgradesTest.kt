package com.niko.assistant.cognitive

import com.niko.assistant.ai.LeoNativeWebSearch
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoRetrievedPassage
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.voice.FarFieldAudioEnhancer
import com.niko.assistant.voice.LeoDuplexPolicy
import com.niko.assistant.voice.OfflineVoiceSelector
import com.niko.assistant.voice.SpeechProsody
import com.niko.assistant.voice.TranscriptQuality
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoProUpgradesTest {

    // --- PILLAR 1: VOZ ---

    @Test
    fun prosodyPlanAdaptsContoursForQuestionsExclamationsAndEmpathy() {
        val base = SpeechProsody(speed = 1.00f, pitch = 0.94f)
        val text = "Tranquilo, vamos paso a paso. ¡Lo logramos al fin! ¿Querés revisar el siguiente punto?"
        val planned = SpeechProsody.planUtterance(text, base = base, firstLimit = 35, nextLimit = 50)
        assertTrue(planned.size >= 3)

        val empathetic = planned.first()
        val exclamation = planned.first { it.text.contains("!") }
        val question = planned.first { it.text.contains("?") }

        assertTrue("Empathetic opener should slow down", empathetic.speed < base.speed)
        assertTrue("Exclamation should lift pitch and speed", exclamation.pitch > base.pitch && exclamation.speed > base.speed)
        assertTrue("Question should lift pitch", question.pitch > base.pitch)
        assertTrue("Intermediate sentence should schedule a natural pause", empathetic.pauseAfterMs > 0L)
        assertEquals(0L, planned.last().pauseAfterMs)
    }

    @Test
    fun prosodyBlendMergesModeAndEmotionSmoothly() {
        val mode = SpeechProsody(speed = 1.15f, pitch = 0.89f)
        val emotion = SpeechProsody(speed = 0.92f, pitch = 0.94f)
        val blended = SpeechProsody.blend(mode, emotion, weight = 0.5f)
        assertTrue(blended.speed in 0.93f..1.14f)
        assertTrue(blended.pitch in 0.89f..0.94f)
    }

    @Test
    fun offlineVoiceSelectorPrioritizesModernNeuralVoices() {
        val standard = OfflineVoiceSelector.Candidate("es-mx-standard-a", "es", "MX", 400, 200, false)
        val chirp = OfflineVoiceSelector.Candidate("es-mx-chirp-hd", "es", "MX", 400, 200, false)
        val ranked = OfflineVoiceSelector.ranked(listOf(standard, chirp), savedName = null)
        assertEquals("es-mx-chirp-hd", ranked.first().name)
    }

    // --- PILLAR 2: ESCUCHA ---

    @Test
    fun farFieldSoftLimiterPreventsHardClippingAndHpfRemovesDcOffset() {
        // Linear below knee
        assertEquals(0.50f, FarFieldAudioEnhancer.softLimit(0.50f), 0.0001f)
        // Smoothly compressed above knee without exceeding 0.98
        val hot1 = FarFieldAudioEnhancer.softLimit(1.1f)
        val hot2 = FarFieldAudioEnhancer.softLimit(2.5f)
        assertTrue(hot1 in 0.82f..0.98f)
        assertTrue(hot2 in hot1..0.98f)
        assertTrue(FarFieldAudioEnhancer.softLimit(-3.0f) in -0.98f..-0.82f)

        // HPF strips constant DC rumble
        val dc = FloatArray(256) { 0.25f }
        val filtered = FarFieldAudioEnhancer.highPassFilter(dc)
        assertTrue(abs(filtered.last()) < 0.005f)
    }

    @Test
    fun duplexPolicyRejectsFuzzySpeakerBleedAndAllowsDoubleTalkBargeIn() {
        val spoken = "La temperatura actual en Managua es de treinta grados centígrados con cielo despejado"
        // ASR-distorted bleed from speaker (not an exact substring)
        assertTrue(LeoDuplexPolicy.isEcho("temperatura actual Managua treinta grado", spoken))
        assertFalse(LeoDuplexPolicy.canInterrupt("temperatura actual Managua treinta grado", spoken))

        // Genuine user barge-in phrases still interrupt cleanly
        assertTrue(LeoDuplexPolicy.canInterrupt("aguantá", spoken))
        assertTrue(LeoDuplexPolicy.canInterrupt("cambia de tema", spoken))

        // Double-Talk Detection: loud user voice overrides playback echo gate
        assertFalse(LeoDuplexPolicy.isAcousticEcho(micRms = 0.12f, playbackRms = 0.05f, spectralSimilarity = 0.65f))
        assertTrue(LeoDuplexPolicy.canInterruptFullDuplex("espera", spoken, micRms = 0.12f, playbackRms = 0.05f, spectralSimilarity = 0.65f))
    }

    @Test
    fun asrTranscriptRepairHandlesModernTechAndApps() {
        assertEquals(
            "Abrí ChatGPT y Google",
            TranscriptQuality.repairAsrTranscript("Abrí chat gpt y gugol"),
        )
        assertEquals(
            "Buscá en TikTok e Instagram",
            TranscriptQuality.repairAsrTranscript("Buscá en tic toc e instagran"),
        )
    }

    // --- PILLAR 3: SENTIMIENTOS ---

    @Test
    fun emotionClassifierEstimatesIntensityHandlesIdiomsAndKeepsAbstentions() {
        val highJoy = LeoEmotionClassifier.classify("estoy súper feliz!")
        val mildJoy = LeoEmotionClassifier.classify("estoy algo feliz")
        assertNotNull(highJoy)
        assertNotNull(mildJoy)
        assertEquals(LeoEmotionClassifier.Emotion.JOY, highJoy!!.emotion)
        assertTrue("High intensifier + exclamation should beat mild", highJoy.intensity > mildJoy!!.intensity)

        assertEquals(LeoEmotionClassifier.Emotion.SADNESS, LeoEmotionClassifier.classify("no me siento bien")?.emotion)
        assertEquals(LeoEmotionClassifier.Emotion.TIREDNESS, LeoEmotionClassifier.classify("ya no aguanto más")?.emotion)
        assertEquals(LeoEmotionClassifier.Emotion.FRUSTRATION, LeoEmotionClassifier.classify("qué frustración")?.emotion)
        assertEquals(LeoEmotionClassifier.Emotion.JOY, LeoEmotionClassifier.classify("estoy tuani")?.emotion)

        // Safety abstentions preserved
        assertNull(LeoEmotionClassifier.classify("no estoy triste"))
        assertNull(LeoEmotionClassifier.classify("estoy triste pero feliz"))
        assertNull(LeoEmotionClassifier.classify("¿Estoy feliz?"))
    }

    @Test
    fun emotionalHeartTracksSessionEmaEscalationAndVariesEmpatheticPrefixes() {
        val heart = LeoEmotionalHeart()
        val s1 = heart.observe("estoy preocupado por la entrega")
        assertNotNull(s1)
        assertTrue(heart.currentValence() < 0.0)
        assertTrue(heart.needsCalmVoice())

        // Escalation from worry (-0.60, 0.75) to anger (-0.85, 0.90)
        val s2 = heart.observe("estoy furioso con este fallo")
        assertNotNull(s2)
        assertEquals(LeoEmotionalHeart.Trajectory.ESCALATION, s2!!.trajectory)
        assertTrue(s2.empatheticPrefix.contains("Bajemos un cambio"))

        // Non-repetitive prefix rotation on consecutive frustration turns
        val heart2 = LeoEmotionalHeart()
        val p1 = heart2.observe("estoy frustrado")!!.empatheticPrefix
        val p2 = heart2.observe("sigo frustrado")!!.empatheticPrefix
        assertNotEquals(p1, p2)
    }

    // --- PILLAR 4: BÚSQUEDA ---

    @Test
    fun nativeWebSearchStripsNavigationChromeAndMatchesSpanishPlurals() {
        val html = """
            <html><body>
              <nav><p>Inicio Contacto Mapa del sitio Suscribirse ahora</p></nav>
              <article>
                <p>El volcán Masaya es uno de los volcanes más activos de Nicaragua y se ubica cerca de Managua.</p>
              </article>
              <footer><p>Copyright 2026 Todos los derechos reservados aviso legal</p></footer>
            </body></html>
        """.trimIndent()
        val extracted = LeoNativeWebSearch.extractReadableText(html, "volcanes de Nicaragua")
        assertTrue(extracted.contains("volcán Masaya"))
        assertFalse(extracted.contains("Mapa del sitio"))
        assertFalse(extracted.contains("aviso legal"))

        val hit = LeoNativeWebSearch.Hit(
            title = "Volcán activo en Nicaragua",
            url = "https://es.wikipedia.org/wiki/Volcan_Masaya",
            snippet = "El volcán Masaya se encuentra en Nicaragua.",
        )
        assertTrue(LeoNativeWebSearch.isRelevantTo("volcanes de Nicaragua", hit))
    }

    @Test
    fun researchSynthesisRewardsCorroborationAndAvoidsNearDuplicateSentences() {
        val reply = NikoAiReply(
            text = "Base",
            webUsed = true,
            sources = listOf(
                NikoWebSource("[1] Fuente Uno", "https://uno.example/solar"),
                NikoWebSource("[2] Fuente Dos", "https://dos.example/solar"),
            ),
            passages = listOf(
                NikoRetrievedPassage(
                    1,
                    "Los paneles solares bifaciales aprovechan la luz en ambas caras y aumentan la producción en 18 por ciento. Además requieren poco mantenimiento anual.",
                    true,
                ),
                NikoRetrievedPassage(
                    2,
                    "Los paneles solares bifaciales aprovechan la luz en ambas caras y aumentan la producción en 18 por ciento. Su vida útil supera los 25 años en climas tropicales.",
                    true,
                ),
            ),
        )
        val synth = LeoResearchSynthesis.synthesizeLocalExtractive(reply, "paneles solares bifaciales")
        assertTrue(synth.text.contains("18 por ciento"))
        assertTrue(synth.text.contains("25 años") || synth.text.contains("mantenimiento"))
        // Should not repeat the exact duplicate sentence twice
        assertEquals(1, Regex("18 por ciento").findAll(synth.text).count())
    }
}
