package com.niko.assistant.smarthome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSmartHomeClientTest {

    @Test
    fun validateBaseUrlAcceptsCleanHttpAndHttpsAndRejectsUnsafeUrls() {
        assertEquals("http://192.168.1.50:8123", LocalSmartHomeClient.validateBaseUrl("http://192.168.1.50:8123/"))
        assertEquals("https://casa.local:8123", LocalSmartHomeClient.validateBaseUrl(" https://casa.local:8123/// "))

        assertNull(LocalSmartHomeClient.validateBaseUrl(""))
        assertNull(LocalSmartHomeClient.validateBaseUrl("ftp://192.168.1.50"))
        assertNull(LocalSmartHomeClient.validateBaseUrl("http://user:pass@192.168.1.50:8123"))
        assertNull(LocalSmartHomeClient.validateBaseUrl("javascript:alert(1)"))
    }

    @Test
    fun inferEntityIdMapsSpanishRoomsAndDevicesAccurately() {
        assertEquals("light.sala", LocalSmartHomeClient.inferEntityId("la luz de la sala"))
        assertEquals("fan.cuarto", LocalSmartHomeClient.inferEntityId("el ventilador del cuarto"))
        assertEquals("media_player.comedor", LocalSmartHomeClient.inferEntityId("televisor del comedor"))
        assertEquals("switch.cafetera", LocalSmartHomeClient.inferEntityId("cafetera"))
        assertEquals("light.kitchen_main", LocalSmartHomeClient.inferEntityId("light.kitchen_main"))
    }
}
