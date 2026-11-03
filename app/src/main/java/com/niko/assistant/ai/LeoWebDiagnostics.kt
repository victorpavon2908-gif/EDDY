package com.niko.assistant.ai

/** A small user-visible report, with no query, URL path, credentials or device identifiers. */
object LeoWebDiagnostics {
    fun describe(events: List<LeoNativeWebSearch.FetchDiagnostic>): String {
        if (events.isEmpty()) return "No llegó una respuesta de las fuentes dentro del plazo de la prueba."
        return events.groupBy { it.host }.entries.take(8).joinToString("\n") { (host, items) ->
            val state = when {
                items.any { it.bytes > 0 && it.status in 200..299 } -> "recibí contenido; su pertinencia se comprueba por separado"
                items.any { it.status == 429 } -> "límite de consultas"
                items.any { it.status == 403 } -> "acceso rechazado"
                items.any { it.failure in setOf("UnknownHostException", "ConnectException") } -> "sin conexión con este proveedor"
                items.any { it.failure in setOf("SSLHandshakeException", "SSLPeerUnverifiedException") } -> "falló la conexión segura"
                items.any { it.failure == "TransportBusy" } -> "conexiones anteriores pendientes"
                items.any { it.failure in setOf("SocketTimeoutException", "RequestTimeout") } -> "tiempo de espera agotado"
                items.any { it.status in 200..299 } -> "respondió, pero no terminó de enviar el contenido"
                else -> "no se recibió contenido utilizable"
            }
            "$host: $state"
        }
    }
}
