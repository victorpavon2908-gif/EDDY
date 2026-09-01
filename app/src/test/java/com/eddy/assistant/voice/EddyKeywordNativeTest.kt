package com.eddy.assistant.voice

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
class EddyKeywordNativeTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun config(): com.k2fsa.sherpa.onnx.KeywordSpotterConfig {
        val modelDirectory = System.getenv("EDDY_NATIVE_KWS_MODELS")
        assumeTrue("Run scripts/prepare_kws_native_test.py to enable native testing", !modelDirectory.isNullOrBlank())
        return EddyKeywordConfig.create(File(modelDirectory!!), temporary.root)
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
        repeat(2) {
            val spotter = KeywordSpotter(config = configuration)
            try {
                val stream = spotter.createStream()
                try {
                    // Exercise the model, native stream and result bindings, not just data classes.
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

    @Test fun recognizesSoftDAndKeepsExistingCalls() {
        val spotter = KeywordSpotter(config = config())
        try {
            for (name in listOf("eddy", "hey_edi", "edi_fast")) {
                assertTrue("Missed wake call: $name", detects(spotter, name))
            }
        } finally { spotter.release() }
    }

    @Test fun similarWordsAndUnaddressedCommandsDoNotWakeEddy() {
        val spotter = KeywordSpotter(config = config())
        try {
            for (name in listOf("pedi", "medio", "nadie", "radio", "dia", "ella", "luz", "edita", "freddy", "pedir", "edison")) {
                assertFalse("False activation: $name", detects(spotter, name))
            }
        } finally { spotter.release() }
    }

    @Test fun audioCorpusImprovesRecallWithoutAddingFalseActivations() {
        val current = config()
        val oldKeywords = temporary.newFile("previous-keywords.txt").apply {
            writeBytes(checkNotNull(EddyKeywordNativeTest::class.java.getResourceAsStream("/voice/wake/previous-keywords.txt")).use { it.readBytes() })
        }
        val previous = KeywordSpotter(config = current.copy(keywordsFile = oldKeywords.absolutePath))
        try {
            val spotter = KeywordSpotter(config = current)
            try {
                val positives = listOf("eddy", "edi", "hey_edi", "oye_eddy", "edi_fast")
                val negatives = listOf("pedi", "medio", "nadie", "radio", "dia", "ella", "luz", "edificio", "edita", "le_di", "freddy", "pedir", "ayer_pedi", "edison")
                val before = (positives + negatives).filter { detects(previous, it) }.toSet()
                val after = (positives + negatives).filter { detects(spotter, it) }.toSet()
                assertTrue("Previously recognized calls must still work", after.containsAll(before.intersect(positives.toSet())))
                assertTrue("Must recognize at least one additional pronunciation", after.intersect(positives.toSet()).size > before.intersect(positives.toSet()).size)
                assertTrue("Must not introduce false activations", before.containsAll(after.intersect(negatives.toSet())))
                // Report limitations, rather than treating missed calls or old false wakes as correct behavior.
                println("Wake corpus: positives ${before.intersect(positives.toSet()).size} -> ${after.intersect(positives.toSet()).size}/${positives.size}; false activations ${before.intersect(negatives.toSet()).size} -> ${after.intersect(negatives.toSet()).size}/${negatives.size}")
                println("Still missed: ${positives - after}; remaining false activations: ${after.intersect(negatives.toSet())}")
            } finally { spotter.release() }
        } finally { previous.release() }
    }

    private fun detects(spotter: KeywordSpotter, name: String): Boolean {
        // Synthetic Spanish speech; raw mono signed PCM16 LE at 16 kHz.
        val bytes = checkNotNull(javaClass.getResourceAsStream("/voice/wake/$name.pcm"))
            .use { it.readBytes() }
        val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        // Match the continuous microphone, including silence before/after each utterance.
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
                if (spotter.getResult(stream).keyword == "EDDY") return true
            }
            return false
        } finally { stream.release() }
    }
}
