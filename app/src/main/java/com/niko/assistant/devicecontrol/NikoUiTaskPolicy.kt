package com.niko.assistant.devicecontrol

import java.text.Normalizer
import java.util.Locale

/** Safety boundary for natural-language requests that manipulate the visible Android UI. */
object NikoUiTaskPolicy {
    data class Decision(val allowed: Boolean, val message: String = "")

    fun evaluate(request: String): Decision {
        val normalized = normalize(request)
            .replace(Regex("^(?:leo|niko|nico)\\s+"), "")
            .replace(Regex("^(?:por favor|porfa)\\s+"), "")
        if (normalized.isBlank()) return Decision(false, "No recibí una tarea para la pantalla.")
        if (PROTECTED_QUESTION.containsMatchIn(normalized)) {
            return Decision(false, "Eso parece una pregunta, no una orden para tocar la pantalla.")
        }
        if (HIGH_RISK_REQUEST.containsMatchIn(normalized)) {
            return Decision(false, "Esa acción es sensible y necesita que la hagás directamente en el teléfono.")
        }
        if (!UI_ACTION.containsMatchIn(normalized) && !DIRECT_BUTTON_ACTION.containsMatchIn(normalized)) {
            return Decision(false, "No identifiqué una acción concreta sobre la pantalla.")
        }
        return Decision(true)
    }

    fun looksLikeExplicitUiTask(request: String): Boolean = evaluate(request).allowed

    /**
     * Node-level safety check. Generic pages such as "Permisos" or "Seguridad" are safe
     * to open; controls that approve, send, pay or alter protected state are not.
     */
    fun isSensitiveControl(value: String): Boolean {
        val normalized = normalize(value)
        if (normalized.isBlank()) return false
        return SENSITIVE_CONTROL.containsMatchIn(normalized)
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val PROTECTED_QUESTION = Regex(
        "^(?:como|por que|explica|explicame|decime como|dime como|podes decirme como|podrias decirme como|" +
            "que pasa si|no|nunca|jamas)\\b",
    )
    private val UI_ACTION = Regex(
        "\\b(?:toca|tocar|presiona|presionar|pulsa|pulsar|selecciona|seleccionar|elegi|elegir|escoge|escoger|" +
            "escribi|escribe|escribir|rellena|rellenar|borra|borrar|limpia|limpiar|desliza|deslizar|" +
            "subi|sube|baja|bajar|segui|sigue|continua|continuar|cerra|cierra|cerrar|busca|buscar|" +
            "navega|navegar|anda|entra|activa|activar|desactiva|desactivar|marca|marcar|desmarca|desmarcar)\\b",
    )
    private val DIRECT_BUTTON_ACTION = Regex("\\bdale (?:al|a el) (?:boton|control|opcion)\\b")

    private val HIGH_RISK_REQUEST = Regex(
        "\\b(?:contrasena|password|pin|codigo 2fa|codigo de verificacion|codigo de seguridad|" +
            "transferir|transferencia|enviar dinero|pagar|pago|pagos|comprar|compra|compras|publicar|" +
            "subir publicacion|enviar el mensaje|manda el mensaje|mandar el mensaje|eliminar cuenta|" +
            "borrar cuenta|borrar todos los datos|restablecer de fabrica|desinstalar|cambiar permisos|" +
            "dar permiso|conceder permiso|modificar seguridad|cambiar seguridad|seguridad del telefono|desbloquear)\\b",
    )

    private val SENSITIVE_CONTROL = Regex(
        "\\b(?:pagar|pago|pay|payment|transferir|transfer|enviar dinero|send money|comprar|purchase|" +
            "confirmar compra|confirm purchase|finalizar compra|checkout|publicar|publica|postear|post|" +
            "enviar|send|mandar|confirmar envio|permitir|allow|denegar|deny|conceder|grant permission|" +
            "usar mientras|while using|solo esta vez|only this time|contrasena|password|pin|2fa|" +
            "codigo de verificacion|verification code|codigo de seguridad|security code|eliminar cuenta|" +
            "delete account|borrar cuenta|restablecer de fabrica|factory reset|desinstalar|uninstall|" +
            "cambiar contrasena|change password|desbloquear|unlock|confirmar transferencia)\\b",
    )
}
