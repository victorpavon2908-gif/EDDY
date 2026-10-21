package com.niko.assistant.inference

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate opt-in configuration; legacy cloud credential retirement cannot affect it. */
class LeoInferenceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("leo_inference_v1", Context.MODE_PRIVATE)
    data class Config(val endpoint: String, val model: String, val key: String, val research: Boolean = false, val revision: String = "", val history: Boolean = false) {
        override fun toString() = "Config(model=$model, credential=redacted)"
    }
    val enabled: Boolean get() = prefs.getBoolean("consent", false)
    val researchAllowed: Boolean get() = enabled && prefs.getBoolean("research_consent", false)
    val endpoint: String get() = prefs.getString("endpoint", "").orEmpty()
    val model: String get() = prefs.getString("model", "").orEmpty()
    fun read(): Config? {
        val snapshot = prefs.all
        if (snapshot["consent"] != true) return null
        return runCatching {
            val endpoint = snapshot["endpoint"] as? String ?: return null
            val model = snapshot["model"] as? String ?: return null
            LeoInferenceProtocol.endpoint(endpoint)
            val encoded = snapshot["secret"] as? String ?: ""
            val secret = if (encoded.isBlank()) "" else {
                val parts = encoded.split(':'); require(parts.size == 2)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }
            Config(endpoint, model, secret, snapshot["research_consent"] == true, snapshot["revision"] as? String ?: "", snapshot["history_consent"] == true)
        }.getOrNull()
    }
    fun save(endpoint: String, model: String, secret: String, consent: Boolean, researchConsent: Boolean = false, historyConsent: Boolean = false) {
        LeoInferenceProtocol.endpoint(endpoint)
        require(LeoGoogleProvider.validCredential(endpoint, secret))
        require(model.isNotBlank() && model.length <= 160)
        require(secret.length <= 4096 && secret.none { it.isISOControl() })
        val encrypted = if (secret.isBlank()) "" else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(secret.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }
        check(prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim())
            .putString("secret", encrypted).putBoolean("consent", consent).putBoolean("research_consent", consent && researchConsent).putBoolean("history_consent", consent && historyConsent).putString("revision", java.util.UUID.randomUUID().toString()).commit())
    }
    fun isCurrent(config: Config): Boolean {
        val snapshot = prefs.all
        return snapshot["consent"] == true && (snapshot["revision"] as? String ?: "") == config.revision
    }
    fun disable() { check(prefs.edit().putBoolean("consent", false).putString("revision", java.util.UUID.randomUUID().toString()).commit()) }
    fun clear() { check(prefs.edit().clear().putString("revision", java.util.UUID.randomUUID().toString()).commit()) }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object { private const val ALIAS = "leo_optional_inference_v1" }
}
