package com.niko.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Settings diagnostics never substitute example values for missing device measurements. */
internal object LeoSettingsHardwareProbe {
    data class Reading(val batteryPercent: Int?, val temperatureC: Float?, val lux: Float?, val accelerationG: Float?) {
        fun describe(): String {
            fun number(value: Float?, decimals: Int) = value?.let { String.format(java.util.Locale.ROOT, "%.${decimals}f", it) }
            return "Batería: ${batteryPercent?.let { "$it%" } ?: "no disponible"} · " +
                "Temperatura: ${number(temperatureC, 1)?.let { "$it °C" } ?: "no disponible"} · " +
                "Luz: ${number(lux, 0)?.let { "$it lx" } ?: "sin lectura"} · " +
                "Acelerómetro: ${number(accelerationG, 2)?.let { "$it g" } ?: "sin lectura"}"
        }
    }
    fun batteryReading(intent: Intent?, fallbackPercent: Int?, lux: Float? = null, accelerationG: Float? = null): Reading {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (scale > 0 && level in 0..scale) (level.toLong() * 100 / scale).toInt()
            else fallbackPercent?.takeIf { it in 0..100 }
        val temperature = if (intent?.hasExtra(BatteryManager.EXTRA_TEMPERATURE) == true)
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it in -500..1500 }?.div(10f) else null
        return Reading(percent, temperature, lux?.takeIf { it.isFinite() && it >= 0f },
            accelerationG?.takeIf { it.isFinite() && it >= 0f })
    }
    suspend fun read(context: Context): Reading = withContext(Dispatchers.Main.immediate) {
        val app = context.applicationContext
        var lux: Float? = null
        var acceleration: Float? = null
        val sensors = app.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val sample = event ?: return
                if (sample.sensor?.type == Sensor.TYPE_LIGHT && sample.values.isNotEmpty()) lux = sample.values[0]
                if (sample.sensor?.type == Sensor.TYPE_ACCELEROMETER && sample.values.size >= 3) {
                    val (x, y, z) = sample.values
                    acceleration = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        try {
            var waiting = false
            listOf(Sensor.TYPE_LIGHT, Sensor.TYPE_ACCELEROMETER).forEach { type ->
                val sensor = sensors?.getDefaultSensor(type)
                if (sensor != null && sensors != null) waiting = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) || waiting
            }
            if (waiting) delay(800)
            val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val capacity = app.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            batteryReading(battery, capacity, lux, acceleration)
        } finally { sensors?.unregisterListener(listener) }
    }
}
