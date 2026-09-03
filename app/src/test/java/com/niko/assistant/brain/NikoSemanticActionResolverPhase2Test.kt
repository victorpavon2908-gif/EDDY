package com.niko.assistant.brain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoSemanticActionResolverPhase2Test {
    @Test fun settingsCanOpenThenNavigateBatteryLocally() = runBlocking {
        var cloudCalls = 0
        val resolver = NikoSemanticActionResolver(LocalBrain()) { cloudCalls++; "NONE" }

        val commands = resolver.resolveMany("Leo, abrí Ajustes y navegá hasta Batería")

        assertEquals(
            listOf(
                AssistantCommand.OpenSystemPanel(SystemPanel.SETTINGS),
                AssistantCommand.AutomateUi("navegá hasta Batería"),
            ),
            commands,
        )
        assertEquals(0, cloudCalls)
    }

    @Test fun commonAppsKeepInAppSearchAsVisualGoal() = runBlocking {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { error("No cloud needed") }
        val cases = listOf(
            "abrí WhatsApp y buscá el chat de Juan" to SupportedApp.WHATSAPP,
            "abrí Spotify y buscá Bad Bunny" to SupportedApp.SPOTIFY,
            "abrí YouTube y buscá Rubén Darío" to SupportedApp.YOUTUBE,
            "abrí Chrome y buscá OpenAI" to SupportedApp.CHROME,
        )

        for ((request, app) in cases) {
            val commands = resolver.resolveMany(request)
            assertEquals(2, commands.size)
            assertEquals(AssistantCommand.OpenApp(app), commands.first())
            assertTrue(commands.last() is AssistantCommand.AutomateUi)
        }
    }

    @Test fun sensitiveSecondStepNeverBecomesAutomation() = runBlocking {
        var cloudCalls = 0
        val resolver = NikoSemanticActionResolver(LocalBrain()) { cloudCalls++; "NONE" }
        val commands = resolver.resolveMany("abrí Ajustes y cambiá permisos de seguridad")

        assertTrue(commands.none { it is AssistantCommand.AutomateUi })
    }

    @Test fun simpleKnownOpenStillUsesOriginalLocalFastPath() = runBlocking {
        var cloudCalls = 0
        val resolver = NikoSemanticActionResolver(LocalBrain()) { cloudCalls++; "OPEN_APP|YouTube" }
        assertEquals(listOf(AssistantCommand.OpenApp(SupportedApp.YOUTUBE)), resolver.resolveMany("abrí YouTube"))
        assertEquals(0, cloudCalls)
    }
}
