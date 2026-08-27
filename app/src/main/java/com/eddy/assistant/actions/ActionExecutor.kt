package com.eddy.assistant.actions

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.eddy.assistant.brain.SupportedApp

class ActionExecutor(private val context: Context) {

    fun openApp(app: SupportedApp): ActionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult(
                success = false,
                spokenMessage = "No encuentro ${app.displayName} instalado en este teléfono."
            )

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return ActionResult(true, "Abriendo ${app.displayName}.")
    }

    fun openCamera(): ActionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ActionResult(true, "Abriendo la cámara.")
        } else {
            ActionResult(false, "No pude encontrar una aplicación de cámara disponible.")
        }
    }
}

data class ActionResult(
    val success: Boolean,
    val spokenMessage: String,
)
