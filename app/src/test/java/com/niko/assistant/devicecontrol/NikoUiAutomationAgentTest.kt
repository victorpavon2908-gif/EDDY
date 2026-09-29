package com.niko.assistant.devicecontrol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoUiAutomationAgentTest {
    @Test fun directClickStaysLocalAndNeverCallsPlanner() = runBlocking {
        var plannerCalls = 0
        val planner = LeoUiStepPlanner { plannerCalls++; "ABORT|no debería llamarse" }
        val session = FakeSession(listOf(settingsSnapshot(1, "node_1", "Batería"))).apply {
            directHandler = { it is NikoDirectUiAction.ClickLabel }
        }
        val agent = agent(planner, session)

        val result = agent.run("tocá el botón Batería")

        assertTrue(result.success)
        assertEquals(0, plannerCalls)
        assertEquals(1, session.directActions.size)
        assertTrue(session.performed.isEmpty())
    }

    @Test fun whatsappSearchUsesOneCurrentNodePerIteration() = runBlocking {
        var calls = 0
        val planner = LeoUiStepPlanner {
            calls++
            when (calls) {
                1 -> "CLICK|node_8"
                2 -> "TYPE|node_21|Juan"
                3 -> "CLICK|node_3"
                else -> "DONE|Abrí el chat de Juan."
            }
        }
        val session = FakeSession(
            listOf(
                snapshot(1, "com.whatsapp", "[node_8] ImageButton [desc=\"Buscar\", clickable]"),
                snapshot(2, "com.whatsapp", "[node_21] EditText [id=\"search_input\", editable]"),
                snapshot(3, "com.whatsapp", "[node_3] TextView [text=\"Juan\", clickable]"),
                snapshot(4, "com.whatsapp", "[node_44] TextView [text=\"Juan\"]"),
            ),
        )
        val result = agent(planner, session).run("buscá el chat de Juan")

        assertTrue(result.success)
        assertEquals(listOf("node_8", "node_21", "node_3"), session.performed.map { it.nodeId })
        assertTrue(session.snapshotCalls >= 6)
        assertTrue(session.performed.all { it.nodeId?.startsWith("node_") != false })
    }

    @Test fun staleScreenForcesReobserveBeforeUsingNewNode() = runBlocking {
        var calls = 0
        val planner = LeoUiStepPlanner {
            calls++
            when (calls) {
                1 -> "CLICK|node_5"
                2 -> "CLICK|node_77"
                else -> "DONE|Listo."
            }
        }
        val session = FakeSession(
            listOf(
                settingsSnapshot(10, "node_5", "Batería"),
                settingsSnapshot(11, "node_77", "Batería"),
                settingsSnapshot(12, "node_90", "Uso de batería"),
            ),
        ).apply { staleFirstPerform = true }

        val result = agent(planner, session).run("navegá hasta batería")

        assertTrue(result.success)
        assertEquals("node_5", session.attempted[0].nodeId)
        assertEquals("node_77", session.performed.single().nodeId)
        assertTrue(session.snapshotCalls >= 3)
    }

    @Test fun sameStepOnUnchangedScreenStopsInsteadOfRandomRetry() = runBlocking {
        val planner = LeoUiStepPlanner { "CLICK|node_2" }
        val unchanged = settingsSnapshot(20, "node_2", "Apps")
        val session = FakeSession(listOf(unchanged)).apply {
            keepSameScreenAfterSuccess = true
        }

        val result = agent(planner, session).run("navegá hasta apps")

        assertFalse(result.success)
        assertTrue(result.message.contains("no avanzó", ignoreCase = true))
        assertEquals(1, session.performed.size)
    }

    @Test fun invalidNextStepStopsWithoutPerformingAnything() = runBlocking {
        val planner = LeoUiStepPlanner { "CLICK|node_999" }
        val session = FakeSession(listOf(settingsSnapshot(30, "node_4", "Aplicaciones")))

        val result = agent(planner, session).run("navegá hasta aplicaciones")

        assertFalse(result.success)
        assertTrue(session.performed.isEmpty())
        assertTrue(session.attempted.isEmpty())
    }

    @Test fun sensitiveRequestStopsBeforeDirectOrModelAction() = runBlocking {
        var calls = 0
        val planner = LeoUiStepPlanner { calls++; "CLICK|node_1" }
        val session = FakeSession(listOf(snapshot(40, "com.store", "[node_1] Button [text=\"Confirmar compra\", clickable]")))

        val result = agent(planner, session).run("tocá confirmar compra")

        assertFalse(result.success)
        assertEquals(0, calls)
        assertTrue(session.directActions.isEmpty())
        assertTrue(session.performed.isEmpty())
    }

    private fun agent(planner: LeoUiStepPlanner, session: FakeSession) = NikoUiAutomationAgent(
        planner = planner,
        sessionProvider = { session },
        settleDelay = { },
    )

    private class FakeSession(
        private val snapshots: List<LeoUiSnapshot>,
    ) : LeoUiSession {
        var state = 0
        var snapshotCalls = 0
        var staleFirstPerform = false
        var keepSameScreenAfterSuccess = false
        var directHandler: (NikoDirectUiAction) -> Boolean = { false }
        val directActions = mutableListOf<NikoDirectUiAction>()
        val attempted = mutableListOf<LeoUiStep.Do>()
        val performed = mutableListOf<LeoUiStep.Do>()

        override suspend fun snapshot(): LeoUiSnapshot {
            snapshotCalls++
            return snapshots[state.coerceIn(0, snapshots.lastIndex)]
        }

        override suspend fun perform(step: LeoUiStep.Do, snapshot: LeoUiSnapshot): LeoUiActionResult {
            attempted += step
            if (staleFirstPerform) {
                staleFirstPerform = false
                state = (state + 1).coerceAtMost(snapshots.lastIndex)
                return LeoUiActionResult(false, "pantalla cambió", stale = true)
            }
            performed += step
            if (!keepSameScreenAfterSuccess) state = (state + 1).coerceAtMost(snapshots.lastIndex)
            return LeoUiActionResult(true, "ok")
        }

        override suspend fun performDirect(action: NikoDirectUiAction): Boolean {
            directActions += action
            return directHandler(action)
        }
    }

    private fun settingsSnapshot(id: Long, node: String, label: String) = snapshot(
        id,
        "com.android.settings",
        "[$node] TextView [text=\"$label\", clickable]",
    )

    private fun snapshot(id: Long, packageName: String, body: String) = LeoUiSnapshot(
        packageName = packageName,
        tree = "Package: $packageName\n$body",
        nodeCount = body.lineSequence().count { "[node_" in it },
        snapshotId = id,
        uiRevision = id,
        signature = "sig-$id-${body.hashCode()}",
    )
}
