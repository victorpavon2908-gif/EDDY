package com.niko.assistant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeoSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixtureSettings(): com.niko.assistant.inference.LeoInferenceSettings {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        return com.niko.assistant.inference.LeoInferenceSettings(app,
            javax.crypto.spec.SecretKeySpec(ByteArray(32) { 4 }, "AES"))
    }
    private fun fixtureClient(status: Int) = com.niko.assistant.inference.LeoInferenceClient {
        object : java.net.HttpURLConnection(java.net.URL("https://fixture.example/chat/completions")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun getInputStream() = java.io.ByteArrayInputStream(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"LEO conectado"}]}}]}""".toByteArray())
        }
    }
    private fun waitForText(text: String) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun savedGeminiCredentialCanUpdatePermissionsAndBeDeletedWithoutReentry() {
        val settings = fixtureSettings()
        settings.clear()
        settings.save(com.niko.assistant.inference.LeoGoogleProvider.ENDPOINT,
            com.niko.assistant.inference.LeoGoogleProvider.MODEL, "fixture-key", true)
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                com.niko.assistant.inference.LeoInferencePanel(settingsFactory = { settings }, clientFactory = { fixtureClient(200) })
            }
        } }
        compose.onNodeWithText("Compartir hasta tres intercambios recientes para continuar la conversación. Pueden contener datos personales.")
            .performScrollTo().performClick()
        compose.onNodeWithText("COMPROBAR Y ACTIVAR").performScrollTo().performClick()
        waitForText("Conexión comprobada y activada. Ya podés conversar con LEO.")
        compose.runOnIdle {
            assertEquals("fixture-key", settings.read()!!.key)
            assertEquals(true, settings.historyAllowed)
        }
        compose.onNodeWithText("DESCONECTAR Y BORRAR CLAVE").performScrollTo().performClick()
        waitForText("Proveedor desactivado y credencial eliminada de este teléfono.")
        compose.runOnIdle { assertEquals(null, settings.read()) }
        compose.onNodeWithText("COMPROBAR Y ACTIVAR").assertIsNotEnabled()
    }

    @Test fun rejectedGeminiKeyDoesNotReplaceThePreviouslyWorkingProvider() {
        val settings = fixtureSettings()
        settings.clear()
        settings.save("https://old.example/chat/completions", "old-model", "old-fixture-key", true)
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                com.niko.assistant.inference.LeoInferencePanel(settingsFactory = { settings }, clientFactory = { fixtureClient(401) })
            }
        } }
        compose.onNodeWithText("Google Gemini").performClick()
        compose.onNodeWithText("Pegá tu clave de Gemini").performScrollTo().performTextInput("wrong-fixture-key")
        compose.onNodeWithText("Autorizo enviar mis preguntas a este proveedor y hacer una prueba de conexión.")
            .performScrollTo().performClick()
        compose.onNodeWithText("COMPROBAR Y ACTIVAR").performScrollTo().performClick()
        waitForText(com.niko.assistant.inference.LeoInferenceClient.Failure.CREDENTIAL.description + " (HTTP 401)")
        compose.runOnIdle {
            assertEquals("old-model", settings.model)
            assertEquals("old-fixture-key", settings.read()!!.key)
        }
    }

    @Test fun searchTestReportsFailureAllowsRetryAndPublishesOnlyReadEvidence() {
        val pending = CompletableDeferred<com.niko.assistant.ai.NikoAiReply>()
        var attempts = 0
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                com.niko.assistant.ai.LeoSearchTestPanel(com.niko.assistant.modes.LeoMode.SUPER) { query, mode, report ->
                    assertEquals(com.niko.assistant.modes.LeoMode.SUPER, mode)
                    org.junit.Assert.assertTrue(query.contains("solar"))
                    attempts++
                    if (attempts == 1) {
                        report(com.niko.assistant.ai.LeoNativeWebSearch.FetchDiagnostic("fixture.example", null, 0, "UnknownHostException"))
                        pending.await()
                    } else com.niko.assistant.ai.NikoAiReply("Fuente leída", true,
                        listOf(com.niko.assistant.ai.NikoWebSource("Fuente", "https://fixture.example/solar")),
                        passages = listOf(com.niko.assistant.ai.NikoRetrievedPassage(1, "Contenido leído", true)))
                }
            }
        } }
        compose.onNodeWithText("Probar búsqueda").performClick()
        compose.onNodeWithText("Comprobando…").assertIsNotEnabled()
        compose.runOnIdle { pending.complete(com.niko.assistant.ai.NikoAiReply("Prueba sin conexión", false, emptyList())) }
        waitForText("Prueba sin conexión")
        compose.onNodeWithText("Ver diagnóstico de la prueba").performClick()
        compose.onNodeWithText("fixture.example: sin conexión con este proveedor").assertIsDisplayed()
        compose.onNodeWithText("Probar búsqueda").performClick()
        waitForText("Búsqueda disponible: 1 fuentes y lectura de páginas completada.")
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun geminiSetupOpensOfficialGooglePageAndRequiresUserCredential() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        com.niko.assistant.inference.LeoInferenceSettings(context).clear()
        val opened = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    LeoGeminiSettingsCard(openUrl = { opened += it })
                }
            }
        }
        compose.onNodeWithText("Inteligencia con Gemini").performClick()
        compose.onNodeWithText("ABRIR GOOGLE AI STUDIO").performClick()
        compose.runOnIdle {
            assertEquals(listOf(com.niko.assistant.inference.LeoGoogleProvider.STUDIO), opened)
            assertEquals(false, com.niko.assistant.inference.LeoInferenceSettings(context).enabled)
        }
        compose.onNodeWithText("COMPROBAR Y ACTIVAR").assertIsNotEnabled()
        compose.onNodeWithText("Pegá tu clave de Gemini").assertExists()
    }

    @Test fun modePickerPersistsSelectionAndReactsToVoiceSideChanges() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        com.niko.assistant.modes.LeoModeStore.set(context, com.niko.assistant.modes.LeoMode.NORMAL)
        compose.setContent {
            MaterialTheme {
                val mode = com.niko.assistant.ui.rememberLeoMode()
                androidx.compose.foundation.layout.Column {
                    Text("Modo actual: ${mode.id}")
                    com.niko.assistant.ui.LeoModeOptions(mode) {
                        com.niko.assistant.modes.LeoModeStore.set(context, it)
                    }
                }
            }
        }
        compose.onNodeWithText("Super Leo").performClick()
        compose.onNodeWithText("Modo actual: super").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(com.niko.assistant.modes.LeoMode.SUPER, com.niko.assistant.modes.LeoModeStore.read(context))
            com.niko.assistant.modes.LeoModeStore.set(context, com.niko.assistant.modes.LeoMode.RESEARCH)
        }
        compose.onNodeWithText("Modo actual: investigador").assertIsDisplayed()
        compose.onNodeWithText("Normal").performClick()
        compose.onNodeWithText("Modo actual: normal").assertIsDisplayed()
    }

    @Test fun initiallyExpandedSectionDisplaysItsControlsImmediately() {
        compose.setContent {
            MaterialTheme {
                DarkSettingsCard(Icons.Rounded.SettingsSuggest, "Internet", "Fuentes", initiallyExpanded = true) {
                    Text("Probar búsqueda")
                }
            }
        }
        compose.onNodeWithText("Probar búsqueda").assertIsDisplayed()
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithText("Probar búsqueda").assertIsNotDisplayed()
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithText("Probar búsqueda").assertIsDisplayed()
    }

    @Test fun collapsingSectionPreservesContentAndDoesNotDisposeWork() {
        var started = 0
        var disposed = 0
        compose.setContent {
            MaterialTheme {
                DarkSettingsCard(Icons.Rounded.SettingsSuggest, "Descargas", "Uso sin Internet") {
                    DisposableEffect(Unit) {
                        started++
                        onDispose { disposed++ }
                    }
                    Text("Descarga en curso")
                }
            }
        }
        compose.onNodeWithText("Descarga en curso").assertDoesNotExist()
        compose.onNodeWithText("Descargas").performClick()
        compose.onNodeWithText("Descarga en curso").assertIsDisplayed()
        compose.onNodeWithText("Descargas").performClick()
        compose.onNodeWithText("Descarga en curso").assertIsNotDisplayed()
        compose.runOnIdle {
            assertEquals(1, started)
            assertEquals(0, disposed)
        }
        compose.onNodeWithText("Descargas").performClick()
        compose.onNodeWithText("Descarga en curso").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, started) }
    }

    @Test fun switchRowHasOneAccessibleControlAndCallsPreferenceOncePerTap() {
        var saves = 0
        compose.setContent {
            var enabled by remember { mutableStateOf(false) }
            MaterialTheme {
                DarkSwitchRow("Buscar en Internet", "Consultar fuentes", enabled) {
                    enabled = it
                    saves++
                }
            }
        }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithText("Buscar en Internet").performClick()
        compose.onNode(isToggleable()).assertIsOff()
        compose.runOnIdle { assertEquals(2, saves) }
    }
}
