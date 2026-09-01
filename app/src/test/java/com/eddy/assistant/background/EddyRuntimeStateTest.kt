package com.eddy.assistant.background

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
class EddyRuntimeStateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetPreferences() {
        context.getSharedPreferences("eddy_runtime_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("eddy_control", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun runningServiceAndReadySpeakerDoNotImplyAWorkingMicrophone() {
        EddyRuntimeState.setRunning(context, true)
        EddyRuntimeState.setVoiceReady(context, true)
        assertEquals(EddyRuntimeState.InputState.STOPPED, EddyRuntimeState.read(context).inputState)
    }

    @Test fun captureFailureClearsStaleListeningAndHeardText() {
        EddyRuntimeState.setInput(context, EddyRuntimeState.InputState.READY, "Lista")
        EddyRuntimeState.setState(context, EddyRuntimeState.State.LISTENING)
        EddyRuntimeState.setHeard(context, "EDDY")
        EddyRuntimeState.setInput(context, EddyRuntimeState.InputState.ERROR, "Micrófono ocupado")
        val snapshot = EddyRuntimeState.read(context)
        assertEquals(EddyRuntimeState.State.IDLE, snapshot.state)
        assertEquals(EddyRuntimeState.InputState.ERROR, snapshot.inputState)
        assertEquals("Micrófono ocupado", snapshot.inputStatus)
        assertEquals("", snapshot.heardText)
    }

    @Test fun recoveredMicrophoneWaitsForWakeWithoutArmingACommand() {
        EddyRuntimeState.setInput(context, EddyRuntimeState.InputState.PREPARING, "Preparando")
        EddyRuntimeState.setInput(context, EddyRuntimeState.InputState.READY, "Lista")
        assertEquals(EddyRuntimeState.State.IDLE, EddyRuntimeState.read(context).state)
    }

    @Test fun stoppedServiceCannotAdvertiseAReadyMicrophone() {
        EddyRuntimeState.setInput(context, EddyRuntimeState.InputState.READY, "Lista")
        EddyRuntimeState.reset(context)
        assertEquals(EddyRuntimeState.InputState.STOPPED, EddyRuntimeState.read(context).inputState)
        assertFalse(EddyRuntimeState.read(context).running)
    }

    @Test fun disablingVoiceSurvivesRuntimeResetAndAnotherContext() {
        assertTrue(EddyVoiceSettings.enabled(context))
        EddyVoiceSettings.setEnabled(context, false)
        EddyRuntimeState.reset(context)
        assertFalse(EddyVoiceSettings.enabled(context.applicationContext))
        EddyVoiceSettings.setEnabled(context, true)
        assertTrue(EddyVoiceSettings.enabled(context))
    }
}
