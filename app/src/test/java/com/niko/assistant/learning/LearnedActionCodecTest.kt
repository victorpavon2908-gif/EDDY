package com.niko.assistant.learning

import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.SupportedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnedActionCodecTest {
    @Test fun encodesOnlySafeRepeatableCommands() {
        assertEquals(
            "OPEN_APP|WhatsApp\nTORCH|ON",
            LearnedActionCodec.encode(listOf(AssistantCommand.OpenApp(SupportedApp.WHATSAPP), AssistantCommand.SetTorch(true))),
        )
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.WhatsAppMessage(null, "mensaje viejo"))))
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.AutomateUi("confirmá la compra"))))
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.SmartHomeControl("portón", false))))
    }

    @Test fun rejectsUnsafeDeviceRanges() {
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.SetVolume(101))))
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.SetBrightness(-1))))
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.SetTimer(0, null))))
        assertNull(LearnedActionCodec.encode(listOf(AssistantCommand.Vibrate(10_000))))
    }
}
