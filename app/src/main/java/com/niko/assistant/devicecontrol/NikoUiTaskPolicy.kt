package com.niko.assistant.devicecontrol

import java.text.Normalizer
import java.util.Locale

/** Safety boundary for natural-language requests that manipulate the visible Android UI. */
object NikoUiTaskPolicy {
    data class Decision(val allowed: Boolean, val message: String = "")

    data class ConfirmationDecision(
        val allowed: Boolean,
        val requiresConfirmation: Boolean,
        val confirmationPrompt: String = "",
        val message: String = "",
    )

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

    /**
     * Evalúa una orden de pantalla o cadena de acciones distinguiendo:
     * 1. Acciones prohibidas (contraseñas, PIN, pagos, borrado de cuenta): bloqueadas siempre.
     * 2. Acciones que requieren confirmación segura del usuario (p. ej. cerrar/borrar texto,
     *    cadenas de más de 2 pasos sobre formularios): solicitan confirmación explícita.
     * 3. Acciones de navegación/búsqueda seguras: permitidas directamente.
     */
    fun evaluateWithConfirmation(request: String, confirmedByUser: Boolean = false): ConfirmationDecision {
        val base = evaluate(request)
        if (!base.allowed) {
            return ConfirmationDecision(
                allowed = false,
                requiresConfirmation = false,
                message = base.message,
            )
        }
        val normalized = normalize(request)
        val chainedCount = request.split(Regex("(?i)\\s+(?:y\\s+luego|y\\s+despu[eé]s|luego)\\s+")).size
        val needsConfirm = CONFIRMABLE_MODIFICATION.containsMatchIn(normalized) || chainedCount >= 3
        if (needsConfirm && !confirmedByUser) {
            return ConfirmationDecision(
                allowed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Esta acción modificará controles o ejecutará $chainedCount pasos seguidos en pantalla. ¿Confirmás que querés continuar?",
                message = "Requiere confirmación explícita antes de tocar la pantalla.",
            )
        }
        return ConfirmationDecision(
            allowed = true,
            requiresConfirmation = false,
            message = "Acción verificada y autorizada.",
        )
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

    private val CONFIRMABLE_MODIFICATION = Regex(
        "\\b(?:borra|borrar|limpia|limpiar|desactiva|desactivar|cerra|cierra|cerrar)\\b",
    )

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
