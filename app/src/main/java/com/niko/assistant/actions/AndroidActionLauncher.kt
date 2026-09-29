package com.niko.assistant.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.niko.assistant.LeoApplication
import com.niko.assistant.R

/** Android may silently reject background starts. Offer a real notification action in that case. */
internal object AndroidActionLauncher {
    fun launch(context: Context, intent: Intent, success: String, failure: String): ActionResult = try {
        val visible = LeoApplication.foregroundActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        if (visible != null || Settings.canDrawOverlays(context)) {
            (visible ?: context).startActivity(intent)
            ActionResult(true, success)
        } else if (intent.resolveActivity(context.packageManager) == null) {
            ActionResult(false, failure)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            ActionResult(false, "Abrí LEO y repetí el comando para abrir esa app.")
        } else {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("leo_requested_actions", "Acciones pedidas a LEO", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 2301, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, "leo_requested_actions")
                .setSmallIcon(R.drawable.ic_niko_notification).setContentTitle("Abrir la app que pediste")
                .setContentText("Tocá para continuar con tu comando.").setContentIntent(open)
                .setAutoCancel(true).build()
            manager.notify(2301, notification)
            ActionResult(true, "Tocá la notificación de LEO para abrirla. Android requiere ese toque cuando estoy en segundo plano.")
        }
    } catch (_: Exception) { ActionResult(false, failure) }
}
