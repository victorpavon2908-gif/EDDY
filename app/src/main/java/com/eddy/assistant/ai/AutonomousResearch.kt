package com.eddy.assistant.ai

import com.eddy.assistant.brain.WebQueryRouter
import com.eddy.assistant.memory.MemoryLearning
import java.net.URI

/** Bounded research inside an activated turn, never a background crawler. */
object AutonomousResearch {
    fun offlineOnly(message: String): Boolean = Regex("\\b(?:sin (?:usar )?internet|sin conexion|solo (?:en )?local|no (?:uses|usar) internet)\\b")
        .containsMatchIn(MemoryLearning.key(message))

    fun allowedFor(message: String): Boolean {
        val text = MemoryLearning.key(message)
        if (Regex("\\b(?:sin (?:usar )?internet|no (?:busques|consultes|investigues)|no uses (?:internet|la web)|solo (?:local|sin conexion))\\b").containsMatchIn(text)) return false
        if (Regex("^no (?:quiero|necesito).*(?:busc|consult|investig)").containsMatchIn(text)) return false
        if (WebQueryRouter.explicitQuery(message) != null) return true
        if (Regex("\\b(?:me siento|estoy (?:triste|feliz|cansado|cansada)|me llamo|mi nombre|mi familia|mi contrasena|mi clave)\\b").containsMatchIn(text)) return false
        return WebQueryRouter.needsCurrentInformation(message) ||
            Regex("^(?:que|quien|quienes|cuando|donde|cual|cuales|cuanto|cuantos|como|por que|es cierto|verifica|compar[aá]|recomienda|recomendame)\\b").containsMatchIn(text)
    }

    fun uncertain(answer: String): Boolean {
        val text = MemoryLearning.key(answer)
        return text.isBlank() || Regex("\\b(?:no (?:lo )?se|no (?:tengo|dispongo de) (?:esa |la )?informacion|no puedo (?:confirmar|verificar|asegurar)|no estoy segur[oa]|necesito verificar|desconozco)\\b").containsMatchIn(text)
    }

    /** Different links on the same host do not establish independent corroboration.
     * Search redirect hosts are opaque: do not count their redirect URLs as publishers.
     */
    fun publisherCount(urls: List<String>): Int = urls.mapNotNull { url ->
        runCatching { URI(url) }.getOrNull()?.takeIf { it.scheme == "https" }?.host
            ?.lowercase()?.removePrefix("www.")?.takeUnless { it.endsWith("vertexaisearch.cloud.google.com") }
    }.distinct().size

    fun evidenceNote(urls: List<String>): String = when {
        urls.isEmpty() -> "No hay fuentes web verificables."
        publisherCount(urls) < 2 -> "La diversidad de fuentes no quedó confirmada."
        else -> ""
    }
}
