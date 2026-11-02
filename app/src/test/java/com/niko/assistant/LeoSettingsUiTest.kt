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
