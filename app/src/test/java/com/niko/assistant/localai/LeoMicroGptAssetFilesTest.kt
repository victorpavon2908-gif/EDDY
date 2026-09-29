package com.niko.assistant.localai

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the exact MicroGPT bytes that Gradle will package, not only Kotlin compilation. */
class LeoMicroGptAssetFilesTest {
    @Test fun packagedCheckpointParsesAndGeneratesUsefulConversation() {
        val assets = locateAssets()
        val encoded = partNames().joinToString("") { name ->
            File(assets, name).readText(Charsets.UTF_8)
        }
        val payload = Base64.getDecoder().decode(encoded)
        assertEquals(109_722, payload.size)
        assertEquals(ACTUAL_ASSET_SHA256, sha256(payload))

        val core = LeoMicroGptCore(payload)
        val cases = listOf(
            "hola leo" to LeoMicroGptGate.Family.GREETING,
            "cómo andás" to LeoMicroGptGate.Family.HOW_ARE_YOU,
            "no me inventés información" to LeoMicroGptGate.Family.DONT_INVENT,
            "hablame como nica" to LeoMicroGptGate.Family.NICARAGUAN_STYLE,
            "estoy cansado" to LeoMicroGptGate.Family.TIRED,
            "seguimos con lo que estábamos viendo" to LeoMicroGptGate.Family.CONTINUE,
        )
        cases.forEach { (input, family) ->
            val reply = core.generate(input, family)
            assertNotNull("MicroGPT failed for '$input'", reply)
            assertTrue("MicroGPT produced an empty reply for '$input'", reply!!.text.trim().length >= 4)
            assertTrue("MicroGPT leaked a control token for '$input'", !reply.text.contains("<f_") && !reply.text.contains("<eos>"))
        }
    }

    private fun locateAssets(): File {
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
            File(System.getProperty("user.dir"), "src/main/assets"),
            File(System.getProperty("user.dir"), "app/src/main/assets"),
        )
        return candidates.firstOrNull { File(it, "leo-microgpt-v1.bundle.b64.part01").isFile }
            ?: error("Could not locate app/src/main/assets from ${System.getProperty("user.dir")}")
    }

    private fun partNames(): List<String> = buildList {
        (1..8).forEach { add("leo-microgpt-v1.bundle.b64.part%02d".format(it)) }
        (1..10).forEach { add("leo-microgpt-v1.bundle.b64.tail%03d".format(it)) }
        (6..15).forEach { add("leo-microgpt-v1.bundle.b64.pair%02d".format(it)) }
        (31..36).forEach { add("leo-microgpt-v1.bundle.b64.tail%03d".format(it)) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ACTUAL_ASSET_SHA256 = "eb97f32cdde73e62c19ab0159de10503b687bbf0dce67a18d756e34fc075a851"
    }
}
