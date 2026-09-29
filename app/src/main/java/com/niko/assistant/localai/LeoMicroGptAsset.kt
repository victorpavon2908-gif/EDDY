package com.niko.assistant.localai

import android.content.Context
import java.security.MessageDigest
import java.util.Base64

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
            val encoded = buildString {
                ASSET_PARTS.forEach { assetName ->
                    append(appContext.assets.open(assetName).bufferedReader().use { it.readText() })
                }
            }
            val bytes = Base64.getDecoder().decode(encoded)
            require(sha256(bytes) == LeoMicroGptCore.SHA256) { "MicroGPT checkpoint checksum mismatch" }
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
        private val ASSET_PARTS = buildList {
            (1..8).forEach { add("leo-microgpt-v1.bundle.b64.part%02d".format(it)) }
            (1..10).forEach { add("leo-microgpt-v1.bundle.b64.tail%03d".format(it)) }
            (6..15).forEach { add("leo-microgpt-v1.bundle.b64.pair%02d".format(it)) }
            (31..36).forEach { add("leo-microgpt-v1.bundle.b64.tail%03d".format(it)) }
        }
    }
}
