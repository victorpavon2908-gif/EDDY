package com.niko.assistant.actions

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.niko.assistant.AiSettingsActivity
import com.niko.assistant.MainActivity
import com.niko.assistant.SmartHomeSettingsActivity
import com.niko.assistant.brain.SupportedApp
import com.niko.assistant.brain.SystemPanel
import com.niko.assistant.brain.VolumeDirection
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import java.text.Normalizer
import java.util.Locale

class ActionExecutor(private val context: Context) {

    fun openApp(app: SupportedApp): ActionResult {
        val launchIntent = (if (app == SupportedApp.WHATSAPP) whatsappPackage()?.let(context.packageManager::getLaunchIntentForPackage) else context.packageManager.getLaunchIntentForPackage(app.packageName))
            ?: return openAppByName(app.displayName)
        return launch(launchIntent, "De una, abriendo ${app.displayName}.", "No pude abrir ${app.displayName}.")
    }

    fun openAppByName(requestedName: String): ActionResult {
        val embeddedName = when (requestedName) {
            "NIKO_TOOL_CALCULATOR" -> "calculadora"
            "NIKO_TOOL_STOPWATCH" -> "cronometro"
            "NIKO_TOOL_TIMER" -> "temporizador"
            "NIKO_TOOL_CLOCK" -> "reloj"
            "NIKO_TOOL_NOTES" -> "notas"
            "NIKO_TOOL_CONVERTER" -> "conversor"
            "NIKO_HOME" -> "niko"
            else -> requestedName
        }
        NikoUiModeStore.resolve(embeddedName)?.let { mode ->
            NikoUiModeStore.set(context, mode)
            val intent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            val message = if (mode == NikoUiMode.ASSISTANT) {
                "De una. Volví a mi pantalla principal."
            } else {
                "De una. Me convertí en ${mode.title.lowercase(Locale.forLanguageTag("es-NI"))}."
            }
            return launch(intent, message, "No pude cambiar mi interfaz ahorita.")
        }

        val cleanTarget = normalizeAppName(requestedName)
        if (cleanTarget.isBlank()) return ActionResult(false, "Decime el nombre de la app que querés abrir.")

        val packageManager = context.packageManager
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcherQuery, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcherQuery, PackageManager.MATCH_ALL)
        }
        val candidates = activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(packageManager)?.toString().orEmpty() }.getOrDefault("")
            if (label.isBlank()) return@mapNotNull null
            AppCandidate(label, packageName, scoreApp(cleanTarget, label, packageName))
        }
        val best = candidates.maxWithOrNull(compareBy<AppCandidate> { it.score }.thenByDescending { it.label.length })
        if (best == null || best.score < 45) return ActionResult(false, "No encuentro una app que se llame $requestedName en este teléfono.")
        val launchIntent = packageManager.getLaunchIntentForPackage(best.packageName)
            ?: return ActionResult(false, "Encontré ${best.label}, pero Android no me dejó abrirla.")
        return launch(launchIntent, "De una, abriendo ${best.label}.", "No pude abrir ${best.label}.")
    }

    private fun scoreApp(target: String, label: String, packageName: String): Int {
        val normalizedLabel = normalizeAppName(label)
        val normalizedPackage = normalizeAppName(packageName.substringAfterLast('.'))
        if (normalizedLabel == target) return 100
        if (normalizedPackage == target) return 95
        if (normalizedLabel.startsWith(target) || target.startsWith(normalizedLabel)) return 88
        if (normalizedLabel.contains(target) || target.contains(normalizedLabel)) return 82
        val targetTokens = target.split(' ').filter { it.length > 1 }.toSet()
        val labelTokens = normalizedLabel.split(' ').filter { it.length > 1 }.toSet()
        val overlap = targetTokens.intersect(labelTokens).size
        if (overlap > 0) return 55 + ((overlap.toDouble() / targetTokens.size.coerceAtLeast(1)) * 30).toInt()
        val distance = levenshtein(target, normalizedLabel)
        val longest = maxOf(target.length, normalizedLabel.length).coerceAtLeast(1)
        return ((1.0 - distance.toDouble() / longest) * 70).toInt()
    }

    private fun normalizeAppName(value: String): String {
        val lower = value.lowercase(Locale.ROOT).replace(Regex("(?i)\\b(?:app|aplicacion|aplicación|por favor)\\b"), " ")
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            previous = current
        }
        return previous[right.length]
    }

    fun openCamera(): ActionResult = launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "De una, abriendo la cámara.", "No pude encontrar una aplicación de cámara disponible.")

    fun dial(number: String): ActionResult = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")), "Listo, te abro la llamada al $number.", "No pude abrir el marcador del teléfono.")

    fun composeMessage(number: String, message: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:${Uri.encode(number)}"); if (message.isNotBlank()) putExtra("sms_body", message) }
        val spoken = if (number.isBlank()) "Elegí el contacto en Mensajes y revisá el texto antes de enviarlo." else if (message.isBlank()) "Listo, te abro un mensaje para $number." else "Ya te dejé preparado el mensaje para $number."
        return launch(intent, spoken, "No pude abrir una aplicación de mensajes.")
    }

    private fun whatsappPackage(): String? = listOf("com.whatsapp", "com.whatsapp.w4b")
        .firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun whatsappMessage(number: String?, message: String): ActionResult {
        val packageName = whatsappPackage() ?: return ActionResult(false, "No encuentro WhatsApp ni WhatsApp Business instalados.")
        if (number.isNullOrBlank() && message.isBlank()) return openApp(SupportedApp.WHATSAPP)

        val intent = if (!number.isNullOrBlank()) {
            val digits = number.filter(Char::isDigit)
            val international = if (digits.length == 8) "505$digits" else digits
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international?text=${Uri.encode(message)}")).apply { setPackage(packageName) }
        } else {
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message); setPackage(packageName) }
        }
        return launch(intent, if (number.isNullOrBlank()) "Elegí el contacto en WhatsApp y revisá el mensaje antes de enviarlo." else "Listo, te abro el chat de WhatsApp con el mensaje preparado.", "No pude abrir WhatsApp. Revisá que esté instalado.")
    }

    fun playSpotify(query: String): ActionResult {
        if (query.isBlank()) return openApp(SupportedApp.SPOTIFY)
        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply { setPackage(SupportedApp.SPOTIFY.packageName); putExtra(SearchManager.QUERY, query) }
        val direct = launch(playIntent, "De una, poniendo $query en Spotify.", "", false)
        if (direct.success) return direct
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply { setPackage(SupportedApp.SPOTIFY.packageName) }, "Te busqué $query en Spotify.", "No pude abrir Spotify o reproducir esa búsqueda.")
    }

    fun setTorch(enabled: Boolean): ActionResult = try {
        val manager = context.getSystemService(CameraManager::class.java) ?: return ActionResult(false, "No pude acceder a la linterna.")
        val cameraId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return ActionResult(false, "Este teléfono no reporta una linterna disponible.")
        manager.setTorchMode(cameraId, enabled)
        ActionResult(true, if (enabled) "Listo, linterna encendida." else "Listo, linterna apagada.")
    } catch (_: Exception) { ActionResult(false, "No pude cambiar la linterna ahorita.") }

    fun setVolume(percent: Int): ActionResult = try {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionResult(false, "No pude acceder al volumen.")
        val safe = percent.coerceIn(0, 100); val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, ((safe / 100.0) * max).toInt().coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
        ActionResult(true, "De una, volumen al $safe por ciento.")
    } catch (_: Exception) { ActionResult(false, "No pude cambiar el volumen.") }

    fun adjustVolume(direction: VolumeDirection): ActionResult = try {
        val audio = context.getSystemService(AudioManager::class.java) ?: return ActionResult(false, "No pude acceder al volumen.")
        when (direction) {
            VolumeDirection.UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            VolumeDirection.DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            VolumeDirection.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        }
        ActionResult(true, when (direction) { VolumeDirection.UP -> "Listo, subiendo el volumen."; VolumeDirection.DOWN -> "Listo, bajando el volumen."; VolumeDirection.MUTE -> "Listo, dejé el audio en silencio." })
    } catch (_: Exception) { ActionResult(false, "No pude cambiar el volumen.") }

    fun setBrightness(percent: Int): ActionResult {
        val safe = percent.coerceIn(1, 100)
        if (!Settings.System.canWrite(context)) return launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")), "Necesito que me permitás modificar ajustes del sistema. Activá el permiso y luego pedime el brillo otra vez.", "No pude abrir el permiso para controlar el brillo.")
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((safe / 100.0) * 255).toInt().coerceIn(1, 255))
            ActionResult(true, "Listo, brillo al $safe por ciento.")
        } catch (_: Exception) { ActionResult(false, "No pude cambiar el brillo.") }
    }

    fun openSystemPanel(panel: SystemPanel): ActionResult {
        val intent = when (panel) {
            SystemPanel.WIFI -> Intent(Settings.Panel.ACTION_WIFI); SystemPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SystemPanel.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY); SystemPanel.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            SystemPanel.NFC -> Intent(Settings.ACTION_NFC_SETTINGS); SystemPanel.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS); SystemPanel.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
        }
        val label = when (panel) { SystemPanel.WIFI -> "Wi‑Fi"; SystemPanel.BLUETOOTH -> "Bluetooth"; SystemPanel.INTERNET -> "Internet"; SystemPanel.LOCATION -> "ubicación"; SystemPanel.NFC -> "NFC"; SystemPanel.AIRPLANE -> "modo avión"; SystemPanel.SETTINGS -> "configuración" }
        return launch(intent, "Te abrí $label para que lo cambiés.", "No pude abrir los ajustes de $label.")
    }

    fun batteryStatus(): ActionResult {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return ActionResult(false, "No pude leer el nivel de batería.")
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) ActionResult(true, "Tenés $level por ciento de batería.") else ActionResult(false, "No pude leer el porcentaje de batería.")
    }

    fun vibrate(milliseconds: Long): ActionResult = try {
        val duration = milliseconds.coerceIn(50L, 2_000L)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator else { @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java) }
            ?: return ActionResult(false, "No pude acceder al vibrador.")
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)); ActionResult(true, "Listo.")
    } catch (_: Exception) { ActionResult(false, "No pude hacer vibrar el teléfono.") }

    fun searchWeb(query: String): ActionResult = launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")), "De una, buscando $query en internet.", "No pude abrir el navegador.")

    fun shareText(text: String): ActionResult {
        val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        return launch(Intent.createChooser(sendIntent, "Compartir con…"), "Listo, escogé dónde querés compartirlo.", "No pude abrir el menú para compartir.")
    }

    fun openSmartHomeSettings(): ActionResult = launch(Intent(context, SmartHomeSettingsActivity::class.java), "Te abrí la configuración de tu casa inteligente.", "No pude abrir la configuración de casa inteligente.")
    fun openAiSettings(): ActionResult = launch(Intent(context, AiSettingsActivity::class.java), "Te abrí la configuración de inteligencia y búsqueda web.", "No pude abrir la configuración de inteligencia.")

    fun setAlarm(hour: Int, minute: Int, label: String?): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR, hour); putExtra(AlarmClock.EXTRA_MINUTES, minute); putExtra(AlarmClock.EXTRA_SKIP_UI, false); if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }
        return launch(intent, "Listo, preparando una alarma para las ${String.format("%02d:%02d", hour, minute)}.", "No pude abrir la aplicación de alarmas.")
    }

    fun setTimer(seconds: Int, label: String?): ActionResult {
        val safeSeconds = seconds.coerceIn(1, 86_400)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, safeSeconds); putExtra(AlarmClock.EXTRA_SKIP_UI, false); if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }
        val minutes = safeSeconds / 60; val remainingSeconds = safeSeconds % 60
        val duration = when { minutes > 0 && remainingSeconds > 0 -> "$minutes minutos y $remainingSeconds segundos"; minutes > 0 -> "$minutes minutos"; else -> "$remainingSeconds segundos" }
        return launch(intent, "Listo, temporizador de $duration.", "No pude abrir el temporizador del teléfono.")
    }

    fun openMaps(query: String): ActionResult {
        val result = launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")), "De una, buscando $query en el mapa.", "", false)
        if (result.success) return result
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")), "Buscando $query en el mapa.", "No pude abrir mapas ni el navegador.")
    }

    private fun launch(intent: Intent, successMessage: String, failureMessage: String, returnFailureImmediately: Boolean = true): ActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return AndroidActionLauncher.launch(context, intent, successMessage, if (returnFailureImmediately) failureMessage else "")
    }

    private data class AppCandidate(val label: String, val packageName: String, val score: Int)
}

data class ActionResult(val success: Boolean, val spokenMessage: String)
