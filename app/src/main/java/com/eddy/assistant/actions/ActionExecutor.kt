package com.eddy.assistant.actions

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.eddy.assistant.SmartHomeSettingsActivity
import com.eddy.assistant.brain.SupportedApp
import com.eddy.assistant.brain.SystemPanel
import com.eddy.assistant.brain.VolumeDirection

class ActionExecutor(private val context: Context) {

    fun openApp(app: SupportedApp): ActionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult(
                success = false,
                spokenMessage = "No encuentro ${app.displayName} instalado en este teléfono.",
            )

        return launch(
            launchIntent,
            successMessage = "De una, abriendo ${app.displayName}.",
            failureMessage = "No pude abrir ${app.displayName}.",
        )
    }

    fun openCamera(): ActionResult {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return launch(
            intent,
            successMessage = "De una, abriendo la cámara.",
            failureMessage = "No pude encontrar una aplicación de cámara disponible.",
        )
    }

    fun dial(number: String): ActionResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        return launch(
            intent,
            successMessage = "Listo, te abro la llamada al $number.",
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
                "Listo, te abro un mensaje para $number."
            } else {
                "Ya te dejé preparado el mensaje para $number."
            },
            failureMessage = "No pude abrir una aplicación de mensajes.",
        )
    }

    fun whatsappMessage(number: String?, message: String): ActionResult {
        val intent = if (!number.isNullOrBlank()) {
            val digits = number.filter(Char::isDigit)
            val international = if (digits.length == 8) "505$digits" else digits
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$international?text=${Uri.encode(message)}"),
            ).apply {
                setPackage("com.whatsapp")
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                setPackage("com.whatsapp")
            }
        }

        return launch(
            intent,
            successMessage = if (number.isNullOrBlank()) {
                "De una, te abro WhatsApp con el mensaje listo."
            } else {
                "Listo, te abro el chat de WhatsApp con el mensaje preparado."
            },
            failureMessage = "No pude abrir WhatsApp. Revisá que esté instalado.",
        )
    }

    fun playSpotify(query: String): ActionResult {
        if (query.isBlank()) return openApp(SupportedApp.SPOTIFY)

        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(SupportedApp.SPOTIFY.packageName)
            putExtra(SearchManager.QUERY, query)
        }
        val direct = launch(
            playIntent,
            successMessage = "De una, poniendo $query en Spotify.",
            failureMessage = "",
            returnFailureImmediately = false,
        )
        if (direct.success) return direct

        val searchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("spotify:search:${Uri.encode(query)}"),
        ).apply {
            setPackage(SupportedApp.SPOTIFY.packageName)
        }
        return launch(
            searchIntent,
            successMessage = "Te busqué $query en Spotify.",
            failureMessage = "No pude abrir Spotify o reproducir esa búsqueda.",
        )
    }

    fun setTorch(enabled: Boolean): ActionResult {
        return try {
            val manager = context.getSystemService(CameraManager::class.java)
                ?: return ActionResult(false, "No pude acceder a la linterna.")
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult(false, "Este teléfono no reporta una linterna disponible.")

            manager.setTorchMode(cameraId, enabled)
            ActionResult(
                true,
                if (enabled) "Listo, linterna encendida." else "Listo, linterna apagada.",
            )
        } catch (_: Exception) {
            ActionResult(false, "No pude cambiar la linterna ahorita.")
        }
    }

    fun setVolume(percent: Int): ActionResult {
        return try {
            val audio = context.getSystemService(AudioManager::class.java)
                ?: return ActionResult(false, "No pude acceder al volumen.")
            val safe = percent.coerceIn(0, 100)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val level = ((safe / 100.0) * max).toInt().coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
            ActionResult(true, "De una, volumen al $safe por ciento.")
        } catch (_: Exception) {
            ActionResult(false, "No pude cambiar el volumen.")
        }
    }

    fun adjustVolume(direction: VolumeDirection): ActionResult {
        return try {
            val audio = context.getSystemService(AudioManager::class.java)
                ?: return ActionResult(false, "No pude acceder al volumen.")
            when (direction) {
                VolumeDirection.UP -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI,
                )
                VolumeDirection.DOWN -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI,
                )
                VolumeDirection.MUTE -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI,
                )
            }
            val spoken = when (direction) {
                VolumeDirection.UP -> "Listo, subiendo el volumen."
                VolumeDirection.DOWN -> "Listo, bajando el volumen."
                VolumeDirection.MUTE -> "Listo, dejé el audio en silencio."
            }
            ActionResult(true, spoken)
        } catch (_: Exception) {
            ActionResult(false, "No pude cambiar el volumen.")
        }
    }

    fun setBrightness(percent: Int): ActionResult {
        val safe = percent.coerceIn(1, 100)
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
            val opened = launch(
                intent,
                successMessage = "Necesito que me permitás modificar ajustes del sistema. Activá el permiso y luego pedime el brillo otra vez.",
                failureMessage = "No pude abrir el permiso para controlar el brillo.",
            )
            return opened
        }

        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val level = ((safe / 100.0) * 255).toInt().coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level,
            )
            ActionResult(true, "Listo, brillo al $safe por ciento.")
        } catch (_: Exception) {
            ActionResult(false, "No pude cambiar el brillo.")
        }
    }

    fun openSystemPanel(panel: SystemPanel): ActionResult {
        val intent = when (panel) {
            SystemPanel.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
            SystemPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SystemPanel.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            SystemPanel.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            SystemPanel.NFC -> Intent(Settings.ACTION_NFC_SETTINGS)
            SystemPanel.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            SystemPanel.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
        }
        val label = when (panel) {
            SystemPanel.WIFI -> "Wi‑Fi"
            SystemPanel.BLUETOOTH -> "Bluetooth"
            SystemPanel.INTERNET -> "Internet"
            SystemPanel.LOCATION -> "ubicación"
            SystemPanel.NFC -> "NFC"
            SystemPanel.AIRPLANE -> "modo avión"
            SystemPanel.SETTINGS -> "configuración"
        }
        return launch(
            intent,
            successMessage = "Te abrí $label para que lo cambiés.",
            failureMessage = "No pude abrir los ajustes de $label.",
        )
    }

    fun batteryStatus(): ActionResult {
        val manager = context.getSystemService(BatteryManager::class.java)
            ?: return ActionResult(false, "No pude leer el nivel de batería.")
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) {
            ActionResult(true, "Tenés $level por ciento de batería.")
        } else {
            ActionResult(false, "No pude leer el porcentaje de batería.")
        }
    }

    fun vibrate(milliseconds: Long): ActionResult {
        return try {
            val duration = milliseconds.coerceIn(50L, 2_000L)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            } ?: return ActionResult(false, "No pude acceder al vibrador.")

            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            ActionResult(true, "Listo.")
        } catch (_: Exception) {
            ActionResult(false, "No pude hacer vibrar el teléfono.")
        }
    }

    fun searchWeb(query: String): ActionResult {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"),
        )
        return launch(
            intent,
            successMessage = "De una, buscando $query en internet.",
            failureMessage = "No pude abrir el navegador.",
        )
    }

    fun shareText(text: String): ActionResult {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Compartir con…")
        return launch(
            chooser,
            successMessage = "Listo, escogé dónde querés compartirlo.",
            failureMessage = "No pude abrir el menú para compartir.",
        )
    }

    fun openSmartHomeSettings(): ActionResult {
        return launch(
            Intent(context, SmartHomeSettingsActivity::class.java),
            successMessage = "Te abrí la configuración de tu casa inteligente.",
            failureMessage = "No pude abrir la configuración de casa inteligente.",
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
            successMessage = "Listo, preparando una alarma para las $formatted.",
            failureMessage = "No pude abrir la aplicación de alarmas.",
        )
    }

    fun setTimer(seconds: Int, label: String?): ActionResult {
        val safeSeconds = seconds.coerceIn(1, 86_400)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, safeSeconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }

        val minutes = safeSeconds / 60
        val remainingSeconds = safeSeconds % 60
        val duration = when {
            minutes > 0 && remainingSeconds > 0 -> "$minutes minutos y $remainingSeconds segundos"
            minutes > 0 -> "$minutes minutos"
            else -> "$remainingSeconds segundos"
        }

        return launch(
            intent,
            successMessage = "Listo, temporizador de $duration.",
            failureMessage = "No pude abrir el temporizador del teléfono.",
        )
    }

    fun openMaps(query: String): ActionResult {
        val geoIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(query)}"),
        )

        val result = launch(
            geoIntent,
            successMessage = "De una, buscando $query en el mapa.",
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
