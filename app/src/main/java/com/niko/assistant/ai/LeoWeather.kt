package com.niko.assistant.ai

import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

/** Parses provider data by requested day; missing values are never invented. */
internal object LeoWeather {
    private fun key(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun isQuery(query: String): Boolean {
        val q = key(query)
        if (Regex("^(?:que es|que significa|define|definicion de|como funciona|como se mide|explicame)\\b").containsMatchIn(q)) return false
        if ("temperatura" in q && Regex("\\b(?:cpu|procesador|telefono|motor|horno|bebe|cuerpo|fiebre|basal|agua|ebullicion|coccion)\\b").containsMatchIn(q)) return false
        if (Regex("\\b(?:clima (?:laboral|organizacional)|temperatura (?:del? |de la )?(?:cpu|procesador|telefono|motor|horno|bebe|cuerpo))\\b").containsMatchIn(q)) return false
        return Regex("\\b(?:clima|temperatura|pronostico|va a llover|llovera|esta lloviendo|lluvia|sensacion termica)\\b").containsMatchIn(q) ||
            Regex("\\b(?:que tiempo hace|como esta el tiempo|estado del tiempo|tiempo en)\\b").containsMatchIn(q)
    }

    fun dayOffset(query: String): Int? {
        val q = key(query)
        if (Regex("\\b\\d{4}\\b").containsMatchIn(q)) return null
        if (Regex("\\b(?:esta manana|por la manana|en la manana|temprano|madrugada|noche|tarde|dias|mes|meses|horas|semana|lunes|martes|miercoles|jueves|viernes|sabado|domingo|a las|a la|hasta|por hora|cada hora|esta noche|esta tarde|ayer|historico|historia|anual|ano|fin de semana)\\b").containsMatchIn(q)) return null
        return when {
            "pasado manana" in q -> 2
            Regex("\\bmanana\\b").containsMatchIn(q) -> 1
            else -> 0
        }
    }

    fun location(query: String): String {
        val q = key(query)
        val en = Regex("\\ben\\s+(.+)$").find(q)?.groupValues?.get(1)
        val afterWeather = Regex("\\b(?:clima|temperatura|pronostico|tiempo)\\s+(?:(?:de|para|por)\\s+)?(.+)$").find(q)?.groupValues?.get(1)
        val city = (en ?: afterWeather).orEmpty()
            .replace(Regex("(?:^|\\s)(?:hoy|ahora|manana|pasado manana|por favor)(?:\\s.*)?$"), "").trim()
        return city.takeIf { it.length in 3..80 && it !in setOf("hace", "hay", "actual", "actualmente", "hace hoy") } ?: "Managua"
    }

    private fun number(obj: JSONObject?, name: String): String? = obj?.optString(name)?.trim()?.takeIf {
        it.toDoubleOrNull()?.isFinite() == true
    }

    fun parse(query: String, location: String, raw: String): NikoAiReply? = runCatching {
        val offset = dayOffset(query) ?: return null
        val root = JSONObject(raw)
        val current = root.optJSONArray("current_condition")?.optJSONObject(0)
        val day = root.optJSONArray("weather")?.optJSONObject(offset)
        val label = when (offset) { 1 -> "mañana"; 2 -> "pasado mañana"; else -> "hoy" }
        val temp = number(current, "temp_C")
        val max = number(day, "maxtempC")
        val min = number(day, "mintempC")
        if (offset > 0 && (max == null || min == null)) return null
        if (offset == 0 && temp == null) return null
        val city = location.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val hourly = day?.optJSONArray("hourly")
        val rain = (0 until (hourly?.length() ?: 0)).mapNotNull {
            number(hourly?.optJSONObject(it), "chanceofrain")?.toDoubleOrNull()?.takeIf { p -> p in 0.0..100.0 }
        }.maxOrNull()
        val condition = current?.optJSONArray("lang_es")?.optJSONObject(0)?.optString("value")
            ?.takeIf(String::isNotBlank) ?: current?.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value")?.takeIf(String::isNotBlank)
        val date = day?.optString("date")?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
        val content = buildString {
            if (offset == 0) {
                appendLine("Clima actual en $city: $temp°C.")
                number(current, "FeelsLikeC")?.let { appendLine("Sensación térmica: $it °C.") }
                condition?.let { appendLine("Condición: ${it.take(120).replaceFirstChar(Char::uppercase)}.") }
                number(current, "humidity")?.let { appendLine("Humedad: $it %.") }
                number(current, "windspeedKmph")?.let { appendLine("Viento: $it km/h.") }
            }
            if (max != null && min != null) appendLine("Pronóstico para $label${date?.let { " ($it)" }.orEmpty()} en $city: mínima $min °C y máxima $max °C.")
            if (rain != null) appendLine("Probabilidad máxima de lluvia en los intervalos disponibles para $label: ${rain.toInt()} %.")
            else if (Regex("llov|lluvia").containsMatchIn(key(query))) appendLine("La fuente no incluyó probabilidades de lluvia para $label.")
        }.trim()
        val encoded = URLEncoder.encode(location, "UTF-8")
        NikoAiReply(content + " [1]", true, listOf(NikoWebSource("[1] Clima y pronóstico · wttr.in", "https://wttr.in/$encoded")),
            evidence = "Datos del proveedor para el período indicado; un pronóstico no garantiza el tiempo que hará.",
            spokenText = content.replace("°C", " grados Celsius").replace("km/h", "kilómetros por hora").replace("%", "por ciento"),
            suggestions = listOf("Clima actual en $city", "Pronóstico para mañana en $city", "¿Va a llover hoy en $city?"),
            passages = listOf(NikoRetrievedPassage(1, content)), researchContext = content)
    }.getOrNull()
}
