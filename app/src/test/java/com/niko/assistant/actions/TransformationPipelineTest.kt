package com.niko.assistant.actions

import android.app.Activity
import android.content.Context
import com.niko.assistant.LeoApplication
import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.LocalBrain
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TransformationPipelineTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val brain = LocalBrain()

    @After fun cleanup() {
        LeoApplication.foregroundActivity = null
        NikoUiModeStore.set(context, NikoUiMode.ASSISTANT)
    }

    @Test fun naturalVoicePhrasesReachTheExpectedEmbeddedScreenThroughTheRealCommandPath() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        LeoApplication.foregroundActivity = WeakReference(activity)
        val executor = ActionExecutor(context)

        val cases = listOf(
            "Leo conviértete en una cámara" to NikoUiMode.CAMERA,
            "Leo transformate en cámara" to NikoUiMode.CAMERA,
            "Leo quiero que seas una cámara" to NikoUiMode.CAMERA,
            "Leo ponete en modo de video" to NikoUiMode.VIDEO,
            "Leo hacete grabadora de audio" to NikoUiMode.AUDIO_RECORDER,
            "Leo pasate a música" to NikoUiMode.MUSIC,
            "Leo quiero una calculadora" to NikoUiMode.CALCULATOR,
            "Leo cambiate a cronómetro" to NikoUiMode.STOPWATCH,
            "Leo transformarte en temporizador" to NikoUiMode.TIMER,
            "Leo sé un reloj" to NikoUiMode.CLOCK,
            "Leo conviértete en notas" to NikoUiMode.NOTES,
            "Leo funciona como conversor de unidades" to NikoUiMode.CONVERTER,
        )

        cases.forEach { (phrase, expectedMode) ->
            NikoUiModeStore.set(context, NikoUiMode.ASSISTANT)
            val commands = brain.understandMany(phrase)
            assertEquals("$phrase should produce exactly one deterministic action", 1, commands.size)
            val command = commands.single()
            assertTrue("$phrase parsed as $command", command is AssistantCommand.OpenAppByName)
            val result = executor.openAppByName((command as AssistantCommand.OpenAppByName).name)
            assertTrue("$phrase failed to execute: ${result.spokenMessage}", result.success)
            assertEquals("$phrase did not reach the UI mode", expectedMode, NikoUiModeStore.read(context))
        }
    }

    @Test fun explanationsAndNegationsNeverTransformTheInterface() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        LeoApplication.foregroundActivity = WeakReference(activity)

        val phrases = listOf(
            "Leo no te conviertas en cámara",
            "Leo cómo funciona una calculadora",
            "Leo explicame cómo transformarte en una cámara",
        )

        phrases.forEach { phrase ->
            NikoUiModeStore.set(context, NikoUiMode.ASSISTANT)
            val command = brain.understandMany(phrase).single()
            assertTrue("$phrase unexpectedly became $command", command is AssistantCommand.Unknown)
            assertEquals(NikoUiMode.ASSISTANT, NikoUiModeStore.read(context))
        }
    }
}
