package com.eddy.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrainTest {
    private val brain = LocalBrain()

    @Test
    fun opensYoutube() {
        assertEquals(
            AssistantCommand.OpenApp(SupportedApp.YOUTUBE),
            brain.understand("EDDY, abre YouTube"),
        )
    }

    @Test
    fun understandsCameraWithoutAccent() {
        assertEquals(
            AssistantCommand.OpenCamera,
            brain.understand("abre la cámara"),
        )
    }

    @Test
    fun unknownCommandKeepsOriginalText() {
        val result = brain.understand("compra comida")
        assertTrue(result is AssistantCommand.Unknown)
        assertEquals("compra comida", (result as AssistantCommand.Unknown).originalText)
    }

    @Test
    fun createsEveningAlarm() {
        assertEquals(
            AssistantCommand.SetAlarm(19, 30, "Alarma creada por EDDY"),
            brain.understand("pon una alarma a las 7:30 pm"),
        )
    }

    @Test
    fun createsTimerInMinutes() {
        assertEquals(
            AssistantCommand.SetTimer(180, "Temporizador creado por EDDY"),
            brain.understand("pon un temporizador de 3 minutos"),
        )
    }

    @Test
    fun composesSms() {
        assertEquals(
            AssistantCommand.ComposeMessage("+50588887777", "voy en camino"),
            brain.understand("envía un mensaje al +505 8888 7777 diciendo voy en camino"),
        )
    }

    @Test
    fun preparesWhatsappMessage() {
        assertEquals(
            AssistantCommand.WhatsAppMessage("88887777", "ya voy"),
            brain.understand("manda por WhatsApp al 8888 7777 diciendo ya voy"),
        )
    }

    @Test
    fun playsSpotifySearch() {
        assertEquals(
            AssistantCommand.PlaySpotify("música de Carlos Mejía Godoy"),
            brain.understand("pon música de Carlos Mejía Godoy en Spotify"),
        )
    }

    @Test
    fun turnsTorchOn() {
        assertEquals(
            AssistantCommand.SetTorch(true),
            brain.understand("prendé la linterna"),
        )
    }

    @Test
    fun setsMediaVolume() {
        assertEquals(
            AssistantCommand.SetVolume(45),
            brain.understand("poné el volumen al 45%"),
        )
    }

    @Test
    fun opensWifiPanel() {
        assertEquals(
            AssistantCommand.OpenSystemPanel(SystemPanel.WIFI),
            brain.understand("abrí el wifi"),
        )
    }

    @Test
    fun setsBrightness() {
        assertEquals(
            AssistantCommand.SetBrightness(40),
            brain.understand("brillo al 40%"),
        )
    }

    @Test
    fun controlsSmartHomeLight() {
        assertEquals(
            AssistantCommand.SmartHomeControl("luz sala", false),
            brain.understand("apagá la luz de la sala"),
        )
    }

    @Test
    fun opensSmartHomeConfiguration() {
        assertEquals(
            AssistantCommand.OpenSmartHomeSettings,
            brain.understand("configura casa inteligente"),
        )
    }

    @Test
    fun reportsBattery() {
        assertEquals(
            AssistantCommand.BatteryStatus,
            brain.understand("cuánta batería me queda"),
        )
    }

    @Test
    fun opensMapDestination() {
        assertEquals(
            AssistantCommand.OpenMaps("Masaya"),
            brain.understand("llévame a Masaya"),
        )
    }

    @Test
    fun clearsMemoryOnExplicitRequest() {
        assertEquals(
            AssistantCommand.ClearMemory,
            brain.understand("EDDY olvida todo"),
        )
    }
}
