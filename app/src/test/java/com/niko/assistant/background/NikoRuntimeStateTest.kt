package com.niko.assistant.background

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NikoRuntimeStateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetPreferences() {
        context.getSharedPreferences("niko_runtime_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("eddy_control", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun runningServiceAndReadySpeakerDoNotImplyAWorkingMicrophone() {
        NikoRuntimeState.setRunning(context, true)
        NikoRuntimeState.setVoiceReady(context, true)
        assertEquals(NikoRuntimeState.InputState.STOPPED, NikoRuntimeState.read(context).inputState)
    }

    @Test fun captureFailureClearsStaleListeningAndHeardText() {
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.READY, "Lista")
        NikoRuntimeState.setState(context, NikoRuntimeState.State.LISTENING)
        NikoRuntimeState.setHeard(context, "NIKO")
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.ERROR, "Micrófono ocupado")
        val snapshot = NikoRuntimeState.read(context)
        assertEquals(NikoRuntimeState.State.IDLE, snapshot.state)
        assertEquals(NikoRuntimeState.InputState.ERROR, snapshot.inputState)
        assertEquals("Micrófono ocupado", snapshot.inputStatus)
        assertEquals("", snapshot.heardText)
    }

    @Test fun recoveredMicrophoneWaitsForWakeWithoutArmingACommand() {
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.PREPARING, "Preparando")
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.READY, "Lista")
        assertEquals(NikoRuntimeState.State.IDLE, NikoRuntimeState.read(context).state)
    }

    @Test fun stoppedServiceCannotAdvertiseAReadyMicrophone() {
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.READY, "Lista")
        NikoRuntimeState.reset(context)
        assertEquals(NikoRuntimeState.InputState.STOPPED, NikoRuntimeState.read(context).inputState)
        assertFalse(NikoRuntimeState.read(context).running)
    }

    @Test fun disablingVoiceSurvivesRuntimeResetAndAnotherContext() {
        assertTrue(NikoVoiceSettings.enabled(context))
        NikoVoiceSettings.setEnabled(context, false)
        NikoRuntimeState.reset(context)
        assertFalse(NikoVoiceSettings.enabled(context.applicationContext))
        NikoVoiceSettings.setEnabled(context, true)
        assertTrue(NikoVoiceSettings.enabled(context))
    }
}
