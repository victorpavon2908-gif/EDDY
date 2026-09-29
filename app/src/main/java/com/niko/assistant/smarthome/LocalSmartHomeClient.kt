package com.niko.assistant.smarthome

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import com.niko.assistant.actions.ActionResult
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalSmartHomeClient(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Entrada compatible con el planificador de acciones actual. La operación de red se
     * ejecuta en Dispatchers.IO, nunca directamente sobre el hilo de Android.
     */
    fun control(target: String, enabled: Boolean): ActionResult = runBlocking(Dispatchers.IO) {
        controlAsync(target, enabled)
    }

    suspend fun controlAsync(target: String, enabled: Boolean): ActionResult = withContext(Dispatchers.IO) {
        val baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().trim().trimEnd('/')
        val token = prefs.getString(KEY_TOKEN, "").orEmpty().trim()

        if (baseUrl.isBlank() || token.isBlank()) {
            return@withContext ActionResult(
                false,
                "Primero configurá tu casa inteligente. Decime: NIKO, configura casa inteligente.",
            )
        }

        val entityId = inferEntityId(target)
        val domain = entityId.substringBefore('.')
        val service = if (enabled) "turn_on" else "turn_off"
        val endpoint = "$baseUrl/api/services/$domain/$service"

        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 7_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
        } catch (_: Exception) {
            return@withContext ActionResult(false, "No pude conectar con tu casa inteligente.")
        }

        try {
            val payload = JSONObject().put("entity_id", entityId).toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = connection.responseCode
            when (code) {
                in 200..299 -> ActionResult(
                    true,
                    if (enabled) "De una, encendí $target." else "Listo, apagué $target.",
                )
                401, 403 -> ActionResult(
                    false,
                    "Home Assistant rechazó el acceso. Revisá el token en la configuración de casa inteligente.",
                )
                404 -> ActionResult(
                    false,
                    "No encontré $target en Home Assistant. Revisá el nombre de la entidad.",
                )
                else -> ActionResult(false, "La casa inteligente respondió con código $code.")
            }
        } catch (_: Exception) {
            ActionResult(
                false,
                "No pude comunicarme con tu casa inteligente. Revisá que estés conectado al mismo Wi‑Fi.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun inferEntityId(target: String): String {
        val direct = target.trim().lowercase(Locale.ROOT)
        if (Regex("^[a-z_]+\\.[a-z0-9_]+$").matches(direct)) return direct

        val normalized = normalize(target)
        val domain = when {
            containsAny(normalized, "luz", "lampara", "bombillo", "foco") -> "light"
            containsAny(normalized, "ventilador", "abanico") -> "fan"
            containsAny(normalized, "televisor", "tv") -> "media_player"
            else -> "switch"
        }

        val name = normalized
            .replace(Regex("\\b(?:luz|lampara|bombillo|foco|ventilador|abanico|enchufe|tomacorriente|switch|televisor|tv)\\b"), " ")
            .replace(Regex("\\b(?:la|el|los|las|de|del)\\b"), " ")
            .trim()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { normalized.replace(Regex("[^a-z0-9]+"), "_").trim('_') }

        return "$domain.$name"
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    companion object {
        const val PREFS_NAME = UpgradeIdentity.smartHomePreferences
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}
