package com.niko.assistant.localai

import android.content.Context
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Lazy, checksum-verified loader for Leo's bundled pure-Kotlin conversational checkpoint. */
class LeoMicroGptAsset(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var core: LeoMicroGptCore? = null
    @Volatile private var failed = false

    val isAvailable: Boolean get() = !failed

    @Synchronized
    fun prewarm(): Boolean = load() != null

    fun reply(message: String): String? {
        val family = LeoMicroGptGate.classify(message) ?: return null
        return load()?.generate(message, family)?.text
    }

    @Synchronized
    private fun load(): LeoMicroGptCore? {
        core?.let { return it }
        if (failed) return null
        return runCatching {
            val output = ByteArrayOutputStream(EXPECTED_BYTES)
            ASSET_PARTS.forEach { assetName ->
                appContext.assets.open(assetName).use { input -> input.copyTo(output) }
            }
            val bytes = output.toByteArray()
            require(bytes.size == EXPECTED_BYTES) { "MicroGPT v2 checkpoint length mismatch" }
            require(sha256(bytes) == V2_SHA256) { "MicroGPT v2 checkpoint checksum mismatch" }
            LeoMicroGptCore(bytes).also { core = it }
        }.getOrElse {
            failed = true
            null
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val V2_SHA256 = "df72b6a8ab27d8f8703079006581b135e705b2805c60f75cf275be6476ad6204"
        const val EXPECTED_BYTES = 117_866
        private val ASSET_PARTS = (1..29).map { "leo-microgpt-v2.bundle.part%02d".format(it) }
    }
}
