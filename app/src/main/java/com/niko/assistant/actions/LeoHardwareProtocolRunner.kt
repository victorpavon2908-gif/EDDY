package com.niko.assistant.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import com.niko.assistant.actions.LeoLabProtocols.ProtocolExecution
import com.niko.assistant.actions.LeoLabProtocols.ProtocolStep
import com.niko.assistant.actions.LeoLabProtocols.SensorSnapshot
import com.niko.assistant.actions.LeoLabProtocols.StepType
import com.niko.assistant.ui.NikoUiModeStore
import kotlin.math.sqrt

/**
 * Ejecutor Físico de Protocolos Biónicos y Monitor de Sensores Reales de Android (`LeoHardwareProtocolRunner`).
 *
 * Conecta los pasos de [LeoLabProtocols] directamente con los servicios físicos de Android:
 * [CameraManager] (linterna), [AudioManager] (volumen y silencio), [NotificationManager] (No Molestar),
 * [Settings.System] (brillo), [NikoUiModeStore] (herramientas embebidas), [BatteryManager] y [SensorManager].
 */
class LeoHardwareProtocolRunner(
    private val context: Context? = null,
    private val customStepDispatcher: ((ProtocolStep) -> Boolean)? = null,
) : SensorEventListener {

    data class HardwareRunReport(
        val protocolId: String,
        val totalSteps: Int,
        val succeededSteps: Int,
        val stepLogs: List<String>,
        val spokenSummary: String,
    )

    @Volatile
    var latestAmbientLux: Float = 120f
        private set

    @Volatile
    var latestAccelMagnitudeG: Float = 1.0f
        private set

    fun execute(execution: ProtocolExecution): HardwareRunReport {
        val logs = mutableListOf<String>()
        var succeeded = 0
        for (step in execution.executedSteps) {
            val ok = dispatchStep(step)
            if (ok) succeeded++
            logs += "${step.type.name}:${step.target}=${step.value}:${if (ok) "OK" else "SKIPPED"}"
        }
        return HardwareRunReport(
            protocolId = execution.protocol.id,
            totalSteps = execution.executedSteps.size,
            succeededSteps = succeeded,
            stepLogs = logs,
            spokenSummary = execution.message,
        )
    }

    private fun dispatchStep(step: ProtocolStep): Boolean {
        customStepDispatcher?.let { return it(step) }
        val ctx = context ?: return false
        return when (step.type) {
            StepType.TORCH -> applyTorch(ctx, step.value.equals("ON", ignoreCase = true))
            StepType.SET_VOLUME -> applyVolume(ctx, step.value.toIntOrNull() ?: 50)
            StepType.SET_BRIGHTNESS -> applyBrightness(ctx, step.value.toIntOrNull() ?: 50)
            StepType.OPEN_TOOL -> applyOpenTool(ctx, step.target)
            StepType.SET_DND -> applyDoNotDisturb(ctx, step.value.equals("ON", ignoreCase = true))
            StepType.SMART_HOME,
            StepType.RUN_DIAGNOSTICS,
            StepType.CUSTOM_NOTE -> true
        }
    }

    private fun applyTorch(ctx: Context, enable: Boolean): Boolean = runCatching {
        val manager = ctx.getSystemService(CameraManager::class.java) ?: return false
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        manager.setTorchMode(cameraId, enable)
        true
    }.getOrDefault(false)

    private fun applyVolume(ctx: Context, percent: Int): Boolean = runCatching {
        val audio = ctx.getSystemService(AudioManager::class.java) ?: return false
        val safe = percent.coerceIn(0, 100)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((safe / 100.0) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        true
    }.getOrDefault(false)

    private fun applyBrightness(ctx: Context, percent: Int): Boolean = runCatching {
        if (!Settings.System.canWrite(ctx)) return false
        val safe = percent.coerceIn(1, 100)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, ((safe / 100.0) * 255).toInt().coerceIn(1, 255))
        true
    }.getOrDefault(false)

    private fun applyOpenTool(ctx: Context, target: String): Boolean = runCatching {
        val mode = NikoUiModeStore.resolve(target) ?: return false
        NikoUiModeStore.set(ctx, mode)
        true
    }.getOrDefault(false)

    private fun applyDoNotDisturb(ctx: Context, enable: Boolean): Boolean = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL,
            )
            true
        } else if (enable) {
            val audio = ctx.getSystemService(AudioManager::class.java) ?: return false
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            true
        } else {
            true
        }
    }.getOrDefault(false)

    fun startSensorMonitoring(): Boolean {
        val ctx = context ?: return false
        return runCatching {
            val sm = ctx.getSystemService(SensorManager::class.java) ?: return false
            sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            true
        }.getOrDefault(false)
    }

    fun stopSensorMonitoring() {
        val ctx = context ?: return
        runCatching {
            ctx.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        }
    }

    fun captureSensorSnapshot(): SensorSnapshot {
        val ctx = context ?: return SensorSnapshot(
            ambientLux = latestAmbientLux,
            accelMagnitudeG = latestAccelMagnitudeG,
        )
        val batteryIntent = runCatching {
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
        val tempC = tempTenths / 10.0f

        val bm = runCatching { ctx.getSystemService(BatteryManager::class.java) }.getOrNull()
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: 85

        return SensorSnapshot(
            ambientLux = latestAmbientLux,
            batteryTempC = tempC,
            batteryPercent = level,
            accelMagnitudeG = latestAccelMagnitudeG,
            wifiConnected = true,
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        when (ev.sensor?.type) {
            Sensor.TYPE_LIGHT -> {
                if (ev.values.isNotEmpty()) {
                    latestAmbientLux = ev.values[0]
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (ev.values.size >= 3) {
                    val x = ev.values[0]
                    val y = ev.values[1]
                    val z = ev.values[2]
                    latestAccelMagnitudeG = sqrt(x * x + y * y + z * z) / 9.80665f
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
