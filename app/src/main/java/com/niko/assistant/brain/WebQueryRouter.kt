package com.niko.assistant.brain

import java.text.Normalizer
import java.util.Locale

/** Decides whether an already activated request needs current, external information. */
object WebQueryRouter {
    private fun normalize(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").trim(' ', '¿', '¡')

    fun explicitQuery(input: String): String? {
        val text = normalize(input)
        val match = Regex("^(?:(?:podes|puedes|podrias|podria|quiero que|necesito que)\\s+)?(?:buscar|busca|buscame|busques|investiga|investigame|investigar|investigues|averigua|averiguame|averiguar|consulta|consultame|consultar|googlea)(?:\\s+(?:en|por)\\s+(?:internet|google|la web))?\\s+(.+)$")
            .find(text) ?: return null
        return match.groupValues[1].trim(' ', ',', '.', '?', '¿', ':').takeIf { it.isNotBlank() }
    }

    fun needsCurrentInformation(input: String): Boolean {
        val text = normalize(input)
        if (Regex("\\b(?:sin (?:usar )?internet|no (?:busques|consultes)|no uses (?:internet|la web))\\b").containsMatchIn(text)) return false
        if (Regex("^no (?:quiero|necesito).*(?:busc|consult|investig)").containsMatchIn(text)) return false
        if (explicitQuery(input) != null) return true
        if (Regex("\\b(?:me siento|estoy (?:triste|cansado|cansada|feliz|frustrado|frustrada))\\b").containsMatchIn(text)) return false

        val freshnessMarkers = listOf(
            "\\b(?:hoy|actualmente|ahora mismo|a esta hora|esta semana|ultima hora|mas reciente|reciente|recientemente|en vivo)\\b",
            "\\b(?:acaba de|acaban de|hace poco|hace unos minutos|hace unas horas|recien)\\b",
            "\\b(?:ultimo|ultima|ultimos|ultimas)\\s+(?:terremoto|sismo|noticia|reporte|resultado|dato|actualizacion|movimiento)\\b",
        )
        if (freshnessMarkers.any { Regex(it).containsMatchIn(text) }) return true

        return listOf(
            "\\b(?:noticias|pronostico|clima|cotizacion|tipo de cambio)\\b",
            "\\b(?:precio|cuesta|cuanto vale|resultado|marcador|horario|presidente|gan[oó])\\b",
            "\\b(?:terremoto|sismo|huracan|tormenta|eleccion|elecciones)\\b.*\\b(?:reciente|hoy|ahora|acaba|hace poco)\\b",
        ).any { Regex(it).containsMatchIn(text) }
    }
}
