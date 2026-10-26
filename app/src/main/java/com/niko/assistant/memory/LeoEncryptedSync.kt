package com.niko.assistant.memory

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sincronización Cifrada Opcional entre Teléfono y Complemento Web (AES-256-GCM + PBKDF2).
 *
 * Permite exportar e importar las preferencias personales, reglas autodidactas y documentos RAG
 * entre dispositivos mediante una frase de paso del usuario, sin servidores en la nube
 * ni terceros. Usa codificación binaria/Base64 pura para ser 100 % compatible con Android y JVM.
 */
object LeoEncryptedSync {
    private const val HEADER = "LEOSYNC1"
    private const val PBKDF2_ITERATIONS = 60_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    data class SyncPayload(
        val preferences: Map<String, String>,
        val autodidactJson: String = "{}",
        val ragDocumentsJson: String = "[]",
        val exportedAtMs: Long = System.currentTimeMillis(),
    )

    fun encrypt(payload: SyncPayload, passphrase: String): String {
        val cleanPass = passphrase.trim()
        require(cleanPass.length >= 6) { "La frase de paso debe tener al menos 6 caracteres." }

        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun b64(text: String): String = encoder.encodeToString(text.toByteArray(StandardCharsets.UTF_8))

        val prefsEncoded = payload.preferences.entries.joinToString(",") { (k, v) ->
            "${b64(k)}:${b64(v)}"
        }
        val bodyStr = listOf(
            payload.exportedAtMs.toString(),
            prefsEncoded,
            b64(payload.autodidactJson),
            b64(payload.ragDocumentsJson),
        ).joinToString("|")

        val plainBytes = bodyStr.toByteArray(StandardCharsets.UTF_8)
        val sha256 = sha256Hex(plainBytes)
        val envelopeBytes = "$sha256\n$bodyStr".toByteArray(StandardCharsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(cleanPass, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(HEADER.toByteArray(StandardCharsets.UTF_8))
        val cipherBytes = cipher.doFinal(envelopeBytes)

        return "$HEADER.${encoder.encodeToString(salt)}.${encoder.encodeToString(iv)}.${encoder.encodeToString(cipherBytes)}"
    }

    fun decrypt(bundle: String, passphrase: String): SyncPayload {
        val cleanPass = passphrase.trim()
        require(cleanPass.length >= 6) { "Frase de paso inválida." }
        val parts = bundle.trim().split('.')
        require(parts.size == 4 && parts[0] == HEADER) { "Formato de paquete LEOSYNC1 inválido." }

        val decoder = Base64.getUrlDecoder()
        fun unb64(encoded: String): String =
            if (encoded.isEmpty()) "" else String(decoder.decode(encoded), StandardCharsets.UTF_8)

        val salt = decoder.decode(parts[1])
        val iv = decoder.decode(parts[2])
        val cipherBytes = decoder.decode(parts[3])
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && cipherBytes.size > 16) {
            "Parámetros criptográficos corruptos."
        }

        val key = deriveKey(cleanPass, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(HEADER.toByteArray(StandardCharsets.UTF_8))
        val decrypted = cipher.doFinal(cipherBytes)

        val envelopeStr = String(decrypted, StandardCharsets.UTF_8)
        val newlineIdx = envelopeStr.indexOf('\n')
        require(newlineIdx > 0) { "Sobre criptográfico malformado." }
        val expectedHash = envelopeStr.substring(0, newlineIdx)
        val bodyStr = envelopeStr.substring(newlineIdx + 1)
        val actualHash = sha256Hex(bodyStr.toByteArray(StandardCharsets.UTF_8))
        check(expectedHash == actualHash) { "Fallo de verificación SHA-256 del paquete sincronizado." }

        val fields = bodyStr.split('|', limit = 4)
        require(fields.size == 4) { "Estructura de datos de sincronización incompleta." }
        val exportedAtMs = fields[0].toLongOrNull() ?: 0L
        val prefs = linkedMapOf<String, String>()
        if (fields[1].isNotBlank()) {
            for (pair in fields[1].split(',')) {
                val kv = pair.split(':', limit = 2)
                if (kv.size == 2) {
                    prefs[unb64(kv[0])] = unb64(kv[1])
                }
            }
        }
        return SyncPayload(
            preferences = prefs,
            autodidactJson = unb64(fields[2]),
            ragDocumentsJson = unb64(fields[3]),
            exportedAtMs = exportedAtMs,
        )
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
