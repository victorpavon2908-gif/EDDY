package com.niko.assistant.voice

import com.k2fsa.sherpa.onnx.KeywordSpotter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses the Android AAR's actual Kotlin bindings with the matching Linux JNI library. */
class NikoKeywordNativeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun config(): com.k2fsa.sherpa.onnx.KeywordSpotterConfig {
        val modelDirectory = System.getenv("NIKO_NATIVE_KWS_MODELS")
        assumeTrue("Run scripts/prepare_kws_native_test.py to enable native testing", !modelDirectory.isNullOrBlank())
        return NikoKeywordConfig.create(File(modelDirectory!!), temporary.root)
    }

    @Test fun reproducesTheReportedErrorWithTheOldEmptyKeywordsFile() {
        val configuration = config()
        val error = assertThrows(IllegalArgumentException::class.java) {
            KeywordSpotter(config = configuration.copy(keywordsFile = "")).release()
        }
        assertTrue(error.message.orEmpty().contains("failed to create native KeywordSpotter"))
    }

    @Test fun realDetectorStartsDecodesSilenceResetsAndStartsAgain() {
        val configuration = config()
        assertTrue(File(configuration.keywordsFile).readText().contains("@LEO"))
        repeat(2) {
            val spotter = KeywordSpotter(config = configuration)
            try {
                val stream = spotter.createStream()
                try {
                    stream.acceptWaveform(FloatArray(16_000), 16_000)
                    var decoded = 0
                    while (spotter.isReady(stream)) {
                        spotter.decode(stream)
                        check(++decoded < 1_000) { "Decoder did not make progress" }
                    }
                    assertTrue("The native model must actually decode audio", decoded > 0)
                    assertEquals("", spotter.getResult(stream).keyword)
                    spotter.reset(stream)
                } finally { stream.release() }
            } finally { spotter.release() }
        }
    }

    @Test fun previousNikoRecordingsNoLongerWakeLeo() {
        val spotter = KeywordSpotter(config = config())
        try {
            for (name in listOf("niko", "hey_niko", "hola_niko", "niko_command", "niko_fast", "niko_slow")) {
                assertFalse("Retired Niko call woke Leo: $name", detects(spotter, name))
            }
        } finally { spotter.release() }
    }

    @Test fun previousEddyNameNoLongerWakesTheAssistant() {
        val spotter = KeywordSpotter(config = config())
        try {
            for (name in listOf("retired_01", "retired_02", "retired_03", "retired_04", "retired_05")) {
                assertFalse("Retired wake call is still active: $name", detects(spotter, name))
            }
        } finally { spotter.release() }
    }

    @Test fun confusingWordsAndUnaddressedCommandsAreIgnored() {
        val spotter = KeywordSpotter(config = config())
        try {
            for (name in listOf("pedi", "medio", "nadie", "radio", "dia", "ella", "luz", "edificio", "edita", "le_di", "other_name", "pedir", "ayer_pedi", "edison", "rico", "pico", "micro", "mexico", "tecnico", "unico", "nicolas", "nicole")) {
                assertFalse("False activation: $name", detects(spotter, name))
            }
        } finally { spotter.release() }
    }

    private fun detects(spotter: KeywordSpotter, name: String): Boolean {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/voice/wake/$name.pcm"))
            .use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(8_000 + pcm.remaining() + 16_000)
        var offset = 8_000
        while (pcm.hasRemaining()) samples[offset++] = pcm.get() / 32768f
        val stream = spotter.createStream()
        try {
            for (start in samples.indices step 512) {
                stream.acceptWaveform(samples.copyOfRange(start, minOf(start + 512, samples.size)), 16_000)
                var decoded = 0
                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                    check(++decoded < 1_000) { "Decoder did not make progress" }
                }
                if (spotter.getResult(stream).keyword == "LEO") return true
            }
            return false
        } finally { stream.release() }
    }
}
