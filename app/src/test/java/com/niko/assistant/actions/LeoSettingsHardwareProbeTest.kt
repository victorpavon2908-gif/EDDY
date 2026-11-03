package com.niko.assistant.actions

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoSettingsHardwareProbeTest {
    @Test fun missingHardwareDoesNotProduceExampleMeasurements() {
        val result = LeoSettingsHardwareProbe.batteryReading(null, Int.MIN_VALUE)
        assertNull(result.batteryPercent)
        assertNull(result.temperatureC)
        assertNull(result.lux)
        assertNull(result.accelerationG)
        assertTrue(result.describe().contains("no disponible"))
        assertTrue(result.describe().contains("sin lectura"))
        assertFalse(result.describe().contains("85%"))
        assertFalse(result.describe().contains("120 lx"))
    }
    @Test fun usesMeasuredScaleAndRejectsInvalidSamples() {
        val intent = Intent().putExtra(BatteryManager.EXTRA_LEVEL, 3).putExtra(BatteryManager.EXTRA_SCALE, 4)
            .putExtra(BatteryManager.EXTRA_TEMPERATURE, 325)
        val result = LeoSettingsHardwareProbe.batteryReading(intent, 99, 40f, 1.02f)
        assertEquals(75, result.batteryPercent)
        assertEquals(32.5f, result.temperatureC!!, 0.001f)
        val invalid = LeoSettingsHardwareProbe.batteryReading(Intent(), -1, Float.NaN, -2f)
        assertNull(invalid.batteryPercent)
        assertNull(invalid.temperatureC)
        assertNull(invalid.lux)
        assertNull(invalid.accelerationG)
    }
}
