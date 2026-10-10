package com.niko.assistant.devicecontrol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Testable boundary between the visual agent and Android AccessibilityService. */
interface LeoUiSession {
    suspend fun snapshot(): LeoUiSnapshot
    suspend fun perform(step: LeoUiStep.Do, snapshot: LeoUiSnapshot): LeoUiActionResult
    suspend fun performDirect(action: NikoDirectUiAction): Boolean
}

/** Android implementation. Direct actions stay local; no remote model receives screen data. */
class AndroidLeoUiSession(
    private val service: NikoAccessibilityService,
) : LeoUiSession {
    val planner = LeoUiStepPlanner()

    override suspend fun snapshot(): LeoUiSnapshot = withContext(Dispatchers.Default) {
        service.snapshot()
    }

    override suspend fun perform(step: LeoUiStep.Do, snapshot: LeoUiSnapshot): LeoUiActionResult =
        withContext(Dispatchers.Main.immediate) {
            service.performNodeAction(
                action = step.action.name.lowercase(),
                nodeId = step.nodeId,
                text = step.text,
                desired = step.desired,
                expectedSnapshotId = snapshot.snapshotId,
                expectedRevision = snapshot.uiRevision,
            )
        }

    override suspend fun performDirect(action: NikoDirectUiAction): Boolean = withContext(Dispatchers.Main.immediate) {
        when (action) {
            is NikoDirectUiAction.ClickLabel -> service.clickText(action.label)
            is NikoDirectUiAction.TypeFocused -> service.setTextInFocusedField(action.text)
            NikoDirectUiAction.ScrollForward -> service.scrollForward()
            NikoDirectUiAction.ScrollBackward -> service.scrollBackward()
            NikoDirectUiAction.Back -> service.goBack()
        }
    }
}
