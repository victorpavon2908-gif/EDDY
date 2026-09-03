package com.niko.assistant.devicecontrol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoUiStepPlannerTest {
    @Test fun settingsBatteryAndPermissionsUseCurrentLabelsNotCoordinates() = runBlocking {
        val planner = LeoUiStepPlanner { prompt ->
            when {
                "text=\"Batería\"" in prompt -> "CLICK|node_42"
                else -> "CLICK|node_7"
            }
        }
        val battery = snapshot(
            "com.android.settings",
            """
            Package: com.android.settings
            [node_0] FrameLayout
              [node_42] TextView [text="Batería", clickable]
            """,
            id = 1,
        )
        val batteryStep = planner.next("navegá hasta batería", battery)
        assertEquals("node_42", (batteryStep as LeoUiStep.Do).nodeId)

        val permissionsMoved = snapshot(
            "com.android.settings",
            """
            Package: com.android.settings
            [node_0] FrameLayout
              [node_7] TextView [text="Permisos", clickable]
            """,
            id = 2,
        )
        val permissionsStep = planner.next("navegá hasta permisos", permissionsMoved)
        assertEquals("node_7", (permissionsStep as LeoUiStep.Do).nodeId)
    }

    @Test fun whatsappSpotifyYoutubeAndBrowserFixturesAcceptDynamicNodeIds() {
        val planner = LeoUiStepPlanner { null }
        val fixtures = listOf(
            snapshot("com.whatsapp", "Package: com.whatsapp\n[node_81] ImageButton [desc=\"Buscar\", clickable]", 10) to "node_81",
            snapshot("com.spotify.music", "Package: com.spotify.music\n[node_6] EditText [text=\"Buscar\", editable]", 11) to "node_6",
            snapshot("com.google.android.youtube", "Package: com.google.android.youtube\n[node_105] Button [desc=\"Buscar\", clickable]", 12) to "node_105",
            snapshot("com.android.chrome", "Package: com.android.chrome\n[node_33] EditText [id=\"url_bar\", editable]", 13) to "node_33",
        )

        assertEquals("node_81", (planner.parse("CLICK|node_81", fixtures[0].first) as LeoUiStep.Do).nodeId)
        assertEquals("node_6", (planner.parse("TYPE|node_6|Bad Bunny", fixtures[1].first) as LeoUiStep.Do).nodeId)
        assertEquals("node_105", (planner.parse("CLICK|node_105", fixtures[2].first) as LeoUiStep.Do).nodeId)
        assertEquals("node_33", (planner.parse("TYPE|node_33|openai.com", fixtures[3].first) as LeoUiStep.Do).nodeId)
    }

    @Test fun staleOrInventedNodeIdsAreRejected() {
        val planner = LeoUiStepPlanner { null }
        val current = snapshot(
            "com.whatsapp",
            "Package: com.whatsapp\n[node_9] TextView [text=\"Juan\", clickable]",
            20,
        )
        assertNull(planner.parse("CLICK|node_8", current))
        assertEquals("node_9", (planner.parse("CLICK|node_9", current) as LeoUiStep.Do).nodeId)
    }

    @Test fun oneModelResponseCanNeverScheduleTwoVisualActions() {
        val planner = LeoUiStepPlanner { null }
        val current = snapshot(
            "com.android.settings",
            "Package: com.android.settings\n[node_2] TextView [text=\"Batería\", clickable]",
            30,
        )
        assertNull(planner.parse("CLICK|node_2\nBACK", current))
        assertNull(planner.parse("```\nCLICK|node_2\n```", current))
    }

    @Test fun longClickSelectAndToggleRequireCapabilitiesExposedByCurrentNode() {
        val planner = LeoUiStepPlanner { null }
        val current = snapshot(
            "com.example.safe",
            """
            Package: com.example.safe
            [node_1] TextView [text="Elemento", clickable, long-clickable]
            [node_2] RadioButton [text="Opción", selectable]
            [node_3] Switch [text="Modo oscuro", clickable, checkable]
            [node_4] TextView [text="Normal", clickable]
            """,
            40,
        )
        assertEquals(LeoUiAction.LONG_CLICK, (planner.parse("LONG_CLICK|node_1", current) as LeoUiStep.Do).action)
        assertEquals(LeoUiAction.SELECT, (planner.parse("SELECT|node_2", current) as LeoUiStep.Do).action)
        val toggle = planner.parse("TOGGLE|node_3|ON", current) as LeoUiStep.Do
        assertEquals(LeoUiAction.TOGGLE, toggle.action)
        assertTrue(toggle.desired == true)
        assertNull(planner.parse("LONG_CLICK|node_4", current))
        assertNull(planner.parse("TOGGLE|node_4|ON", current))
    }

    @Test fun sensitiveControlsAndSecretFieldsNeverBecomeExecutableSteps() {
        val planner = LeoUiStepPlanner { null }
        val current = snapshot(
            "com.example.app",
            """
            Package: com.example.app
            [node_1] Button [text="Pagar", clickable]
            [node_2] Button [text="Enviar", clickable]
            [node_3] Button [text="Permitir", clickable]
            [node_4] EditText [id="password", editable, password-protected]
            [node_5] EditText [id="search", editable]
            """,
            50,
        )
        assertNull(planner.parse("CLICK|node_1", current))
        assertNull(planner.parse("CLICK|node_2", current))
        assertNull(planner.parse("CLICK|node_3", current))
        assertNull(planner.parse("TYPE|node_4|1234", current))
        assertNull(planner.parse("TYPE|node_5|mi contraseña es 1234", current))
        assertFalse(NikoUiTaskPolicy.isSensitiveControl("Permisos"))
        assertFalse(NikoUiTaskPolicy.isSensitiveControl("Seguridad"))
    }

    @Test fun plannerAbortsWhenModelFailsOrReturnsGarbage() = runBlocking {
        val current = snapshot(
            "com.android.settings",
            "Package: com.android.settings\n[node_1] TextView [text=\"Apps\", clickable]",
            60,
        )
        val failed = LeoUiStepPlanner { null }.next("navegá a apps", current)
        assertTrue(failed is LeoUiStep.Abort)
        val garbage = LeoUiStepPlanner { "DELETE|node_1" }.next("navegá a apps", current)
        assertTrue(garbage is LeoUiStep.Abort)
    }

    private fun snapshot(packageName: String, tree: String, id: Long) = LeoUiSnapshot(
        packageName = packageName,
        tree = tree.trimIndent(),
        nodeCount = tree.lineSequence().count { "[node_" in it },
        snapshotId = id,
        uiRevision = id,
        signature = "sig-$id",
    )
}
