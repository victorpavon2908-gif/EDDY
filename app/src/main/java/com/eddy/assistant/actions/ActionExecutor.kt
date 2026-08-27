package com.eddy.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import com.eddy.assistant.brain.SupportedApp

class ActionExecutor(private val context: Context) {

    fun openApp(app: SupportedApp): ActionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult(
                success = false,
                spokenMessage = "No encuentro ${app.displayName} instalado en este teléfono."
            )

        return launch(
            launchIntent,
            successMessage = "Abriendo ${app.displayName}.",
            failureMessage = "No pude abrir ${app.displayName}.",
        )
    }

    fun openCamera(): ActionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return launch(
            intent,
            successMessage = "Abriendo la cámara.",
            failureMessage = "No pude encontrar una aplicación de cámara disponible.",
        )
    }

    fun dial(number: String): ActionResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        return launch(
            intent,
            successMessage = "Preparando la llamada al $number.",
            failureMessage = "No pude abrir el marcador del teléfono.",
        )
    }

    fun composeMessage(number: String, message: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(number)}")
            if (message.isNotBlank()) putExtra("sms_body", message)
        }
        return launch(
            intent,
            successMessage = if (message.isBlank()) {
                "Abriendo un mensaje para $number."
            } else {
                "Preparé el mensaje para $number. Revísalo antes de enviarlo."
            },
            failureMessage = "No pude abrir una aplicación de mensajes.",
        )
    }

    fun setAlarm(hour: Int, minute: Int, label: String?): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }

        val formatted = String.format("%02d:%02d", hour, minute)
        return launch(
            intent,
            successMessage = "Preparando una alarma para las $formatted.",
            failureMessage = "No pude abrir la aplicación de alarmas.",
        )
    }

    fun openMaps(query: String): ActionResult {
        val geoIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(query)}"),
        )

        val result = launch(
            geoIntent,
            successMessage = "Buscando $query en el mapa.",
            failureMessage = "",
            returnFailureImmediately = false,
        )
        if (result.success) return result

        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"),
        )
        return launch(
            browserIntent,
            successMessage = "Buscando $query en el mapa.",
            failureMessage = "No pude abrir mapas ni el navegador.",
        )
    }

    private fun launch(
        intent: Intent,
        successMessage: String,
        failureMessage: String,
        returnFailureImmediately: Boolean = true,
    ): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ActionResult(true, successMessage)
        } catch (_: Exception) {
            ActionResult(false, if (returnFailureImmediately) failureMessage else "")
        }
    }
}

data class ActionResult(
    val success: Boolean,
    val spokenMessage: String,
)
