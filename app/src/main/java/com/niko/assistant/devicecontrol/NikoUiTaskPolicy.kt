package com.niko.assistant.devicecontrol

import java.text.Normalizer
import java.util.Locale

/** Safety boundary for natural-language requests that manipulate the visible Android UI. */
object NikoUiTaskPolicy {
    data class Decision(val allowed: Boolean, val message: String = "")

    fun evaluate(request: String): Decision {
        val normalized = normalize(request)
        if (normalized.isBlank()) return Decision(false, "No recibí una tarea para la pantalla.")
        if (PROTECTED_QUESTION.containsMatchIn(normalized)) {
            return Decision(false, "Eso parece una pregunta, no una orden para tocar la pantalla.")
        }
        if (HIGH_RISK.containsMatchIn(normalized)) {
            return Decision(false, "Esa acción es sensible y necesita que la hagás directamente en el teléfono.")
        }
        if (!UI_ACTION.containsMatchIn(normalized)) {
            return Decision(false, "No identifiqué una acción concreta sobre la pantalla.")
        }
        return Decision(true)
    }

    fun looksLikeExplicitUiTask(request: String): Boolean = evaluate(request).allowed

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val PROTECTED_QUESTION = Regex(
        "^(?:como|por que|explica|explicame|decime como|dime como|que pasa si|no|nunca|jamas)\\b",
    )
    private val UI_ACTION = Regex(
        "\\b(?:toca|tocar|presiona|presionar|pulsa|pulsar|selecciona|seleccionar|elegi|elegir|escoge|escoger|" +
            "escribi|escribe|escribir|rellena|rellenar|borra|borrar|limpia|limpiar|desliza|deslizar|" +
            "subi|sube|baja|bajar|segui|sigue|continua|continuar|cerra|cierra|cerrar|busca|buscar|" +
            "activa|activar|desactiva|desactivar|marca|marcar|desmarca|desmarcar)\\b",
    )
    private val HIGH_RISK = Regex(
        "\\b(?:contrasena|password|pin|codigo 2fa|codigo de verificacion|codigo de seguridad|" +
            "transferir|transferencia|enviar dinero|pagar|pago|pagos|comprar|compra|compras|publicar|" +
            "subir publicacion|enviar el mensaje|manda el mensaje|mandar el mensaje|eliminar cuenta|" +
            "borrar cuenta|borrar todos los datos|restablecer de fabrica|desinstalar|cambiar permisos|" +
            "dar permiso|seguridad del telefono|desbloquear)\\b",
    )
}
