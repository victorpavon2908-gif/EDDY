package com.eddy.assistant.voice

import com.k2fsa.sherpa.onnx.KeywordSpotter
import java.io.File
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
}
