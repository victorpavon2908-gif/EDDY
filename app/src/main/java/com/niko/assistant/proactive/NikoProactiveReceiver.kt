package com.niko.assistant.proactive

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.niko.assistant.MainActivity
import com.niko.assistant.R

open class NikoProactiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val message = intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }?.let(UpgradeIdentity::reminderText)
            ?: "NIKO tiene una sugerencia para ti."
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 4_201)

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sugerencias de NIKO",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos proactivos basados en tus patrones de uso de NIKO."
        }
        manager.createNotificationChannel(channel)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_niko_notification)
            .setContentTitle("NIKO")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = UpgradeIdentity.proactiveChannel
        const val EXTRA_MESSAGE = UpgradeIdentity.proactiveMessageExtra
        const val EXTRA_NOTIFICATION_ID = UpgradeIdentity.proactiveIdExtra
    }
}
