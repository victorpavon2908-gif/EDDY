package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class FaithfulSpeechTranscriberTest {
    private fun audio(level: Float = 0.04f, seconds: Int = 1) =
        FloatArray(16_000 * seconds) { if (it % 2 == 0) level else -level }

    @Test fun clearAudioUsesExactlyOneDecodeAndKeepsLiteralWords() {
        val original = audio()
        var passes = 0
        val transcriber = FaithfulSpeechTranscriber(
            primaryDecoder = { assertSame(original, it); passes++; "Nico, no, no. ¡Mañana!" },
            alternateDecoder = { error("No second pass for clear speech") },
            denoiser = { error("Never discard clear original audio") },
        )
        assertEquals("Nico, no, no. ¡Mañana!", transcriber.transcribe(original).text)
        assertEquals(1, passes)
    }

    @Test fun quietAudioGetsAnIndependentReadOfTheSameWaveform() {
        val original = audio(0.002f)
        var passes = 0
        val transcriber = FaithfulSpeechTranscriber(
            primaryDecoder = { passes++; "abrí WhatsApp" },
            alternateDecoder = { assertSame(original, it); passes++; "¡Abrí WhatsApp!" },
        )
        assertEquals("abrí WhatsApp", transcriber.transcribe(original).text)
        assertEquals(2, passes)
    }

    @Test fun disagreementAboutANegationNeverBecomesAnAction() {
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { "no abras WhatsApp" }, alternateDecoder = { "abre WhatsApp" },
        ).transcribe(audio(0.002f))
        assertTrue(result.needsClarification)
        assertEquals("", result.text)
    }

    @Test fun disagreementAboutADigitNeverBecomesAnAction() {
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { "llamá al 88887777" }, alternateDecoder = { "llamá al 88887778" },
        ).transcribe(audio(0.002f))
        assertTrue(result.needsClarification)
        assertEquals("", result.text)
    }

    @Test fun denoiserCannotDestroyTheOriginalOrCauseAnEmptyAudioDecode() {
        val original = audio()
        val before = original.copyOf()
        var passes = 0
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { passes++; "<unk>" },
            denoiser = { it.fill(0f); it },
        ).transcribe(original)
        assertArrayEquals(before, original, 0f)
        assertEquals(1, passes)
        assertEquals("", result.text)
    }

    @Test fun guardedDenoisingCanRecoverAnOtherwiseUnreadableTurn() {
        var passes = 0
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { if (++passes == 1) "<unk>" else "escribí a Nico" },
            denoiser = { samples -> FloatArray(samples.size) { samples[it] * 0.8f } },
        ).transcribe(audio())
        assertEquals("escribí a Nico", result.text)
        assertEquals(2, passes)
    }

    @Test fun failedOptionalModelKeepsAUsablePrimary() {
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { "abrí la cámara" }, alternateDecoder = { throw IllegalStateException("unavailable") },
        ).transcribe(audio(0.002f))
        assertEquals("abrí la cámara", result.text)
        assertFalse(result.needsClarification)
    }

    @Test fun decoderFailureCanRecoverWithoutKillingMicrophoneCapture() {
        val result = FaithfulSpeechTranscriber(
            primaryDecoder = { throw IllegalStateException("decode failed") }, alternateDecoder = { "abrí WhatsApp" },
        ).transcribe(audio())
        assertEquals("abrí WhatsApp", result.text)
    }

    @Test fun missingMostWordsWithoutABetterReadRequestsRepetition() {
        val result = FaithfulSpeechTranscriber(primaryDecoder = { "WhatsApp" }).transcribe(audio(seconds = 5))
        assertEquals("", result.text)
    }

    @Test fun emptyAudioNeverReachesAnyDecoder() {
        val result = FaithfulSpeechTranscriber(primaryDecoder = { error("empty audio") }).transcribe(floatArrayOf())
        assertEquals("", result.text)
    }
}
