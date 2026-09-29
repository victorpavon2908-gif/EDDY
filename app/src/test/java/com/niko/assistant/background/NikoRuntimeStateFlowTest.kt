package com.niko.assistant.background

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Validates that [NikoRuntimeState.stateFlow] emits the correct values
 * reactively whenever any setter is called, without requiring an additional
 * read() call from SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NikoRuntimeStateFlowTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun resetAll() {
        context.getSharedPreferences("niko_runtime_state", Context.MODE_PRIVATE).edit().clear().commit()
        // Reset StateFlow to default before each test.
        NikoRuntimeState.reset(context)
    }

    @Test fun stateFlowReflectsHeardTextWithoutExtraRead() = runBlocking {
        NikoRuntimeState.setHeard(context, "abre WhatsApp")
        val snapshot = NikoRuntimeState.stateFlow.value
        assertEquals("abre WhatsApp", snapshot.heardText)
    }

    @Test fun stateFlowReflectsResponseTextImmediately() = runBlocking {
        NikoRuntimeState.setResponse(context, "Claro, lo abro ahora.")
        assertEquals("Claro, lo abro ahora.", NikoRuntimeState.stateFlow.value.responseText)
    }

    @Test fun stateFlowReflectsStateTransitions() = runBlocking {
        NikoRuntimeState.setState(context, NikoRuntimeState.State.LISTENING)
        assertEquals(NikoRuntimeState.State.LISTENING, NikoRuntimeState.stateFlow.value.state)

        NikoRuntimeState.setState(context, NikoRuntimeState.State.THINKING)
        assertEquals(NikoRuntimeState.State.THINKING, NikoRuntimeState.stateFlow.value.state)

        NikoRuntimeState.setState(context, NikoRuntimeState.State.SPEAKING)
        assertEquals(NikoRuntimeState.State.SPEAKING, NikoRuntimeState.stateFlow.value.state)

        NikoRuntimeState.setState(context, NikoRuntimeState.State.IDLE)
        assertEquals(NikoRuntimeState.State.IDLE, NikoRuntimeState.stateFlow.value.state)
    }

    @Test fun stateFlowReflectsRunningFlag() = runBlocking {
        NikoRuntimeState.setRunning(context, true)
        assertTrue(NikoRuntimeState.stateFlow.value.running)

        NikoRuntimeState.setRunning(context, false)
        assertFalse(NikoRuntimeState.stateFlow.value.running)
    }

    @Test fun stateFlowReflectsWebSearchFlag() = runBlocking {
        NikoRuntimeState.setSearching(context, true)
        assertTrue(NikoRuntimeState.stateFlow.value.webSearching)

        NikoRuntimeState.setSearching(context, false)
        assertFalse(NikoRuntimeState.stateFlow.value.webSearching)
    }

    @Test fun stateFlowIsConsistentWithReadAfterInit() = runBlocking {
        NikoRuntimeState.setResponse(context, "Hola")
        NikoRuntimeState.setState(context, NikoRuntimeState.State.SPEAKING)
        NikoRuntimeState.setRunning(context, true)

        // Seed from disk (simulating process startup)
        NikoRuntimeState.init(context)

        val fromFlow = NikoRuntimeState.stateFlow.value
        val fromDisk = NikoRuntimeState.read(context)

        assertEquals(fromDisk.responseText, fromFlow.responseText)
        assertEquals(fromDisk.state, fromFlow.state)
        assertEquals(fromDisk.running, fromFlow.running)
    }

    @Test fun resetClearsFlowState() = runBlocking {
        NikoRuntimeState.setHeard(context, "texto previo")
        NikoRuntimeState.setState(context, NikoRuntimeState.State.THINKING)
        NikoRuntimeState.setRunning(context, true)

        NikoRuntimeState.reset(context)

        val snapshot = NikoRuntimeState.stateFlow.value
        assertEquals("", snapshot.heardText)
        assertEquals(NikoRuntimeState.State.IDLE, snapshot.state)
        assertFalse(snapshot.running)
    }

    @Test fun inputFailureClearsFlowStateAndHeardText() = runBlocking {
        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.READY, "Lista")
        NikoRuntimeState.setState(context, NikoRuntimeState.State.LISTENING)
        NikoRuntimeState.setHeard(context, "prueba")

        NikoRuntimeState.setInput(context, NikoRuntimeState.InputState.ERROR, "Micrófono ocupado")

        val snapshot = NikoRuntimeState.stateFlow.value
        assertEquals(NikoRuntimeState.InputState.ERROR, snapshot.inputState)
        assertEquals(NikoRuntimeState.State.IDLE, snapshot.state)
        assertEquals("", snapshot.heardText)
    }
}
