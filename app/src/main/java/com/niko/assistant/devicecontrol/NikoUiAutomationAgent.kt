package com.niko.assistant.devicecontrol

import kotlinx.coroutines.delay

/**
 * Visual automation agent for LEO.
 *
 * Direct commands stay model-free. Complex tasks use [LeoUiStepPlanner] to choose one
 * allow-listed action from the current accessibility snapshot, execute it, wait for the
 * UI to settle and observe again. No local generative runtime is required.
 */
class NikoUiAutomationAgent private constructor(
    private val plannerProvider: (LeoUiSession) -> LeoUiStepPlanner?,
    private val sessionProvider: () -> LeoUiSession?,
    private val settleDelay: suspend (Long) -> Unit,
) {
    data class Result(
        val success: Boolean,
        val message: String,
        val iterations: Int,
    )

    /** Compatibility constructor. The legacy argument is intentionally ignored. */
    constructor(@Suppress("UNUSED_PARAMETER") legacyDependency: Any?) : this(
        plannerProvider = { session -> (session as? AndroidLeoUiSession)?.planner },
        sessionProvider = { NikoAccessibilityService.instance?.let(::AndroidLeoUiSession) },
        settleDelay = { delay(it) },
    )

    internal constructor(
        planner: LeoUiStepPlanner,
        sessionProvider: () -> LeoUiSession?,
        settleDelay: suspend (Long) -> Unit = { delay(it) },
    ) : this(
        plannerProvider = { planner },
        sessionProvider = sessionProvider,
        settleDelay = settleDelay,
    )

    suspend fun run(task: String): Result {
        val request = task.trim().take(600)
        if (request.isBlank()) return Result(false, "No recibí una tarea para la aplicación.", 0)
        val safety = NikoUiTaskPolicy.evaluate(request)
        if (!safety.allowed) return Result(false, safety.message, 0)

        val session = sessionProvider()
            ?: return Result(false, "Activá LEO Device Control en Accesibilidad de Android para que pueda manejar pantallas de otras apps.", 0)

        runDirect(session, request)?.let { return it }
        val planner = plannerProvider(session)
            ?: return Result(false, "No pude iniciar el planificador visual seguro.", 0)

        val history = ArrayDeque<String>()
        var snapshot = session.snapshot()
        var consecutiveFailures = 0
        var lastStepOnSameScreen: String? = null

        for (index in 0 until MAX_ITERATIONS) {
            if (snapshot.nodeCount == 0) {
                return Result(false, "No pude leer controles de ${snapshot.packageName.ifBlank { "la aplicación" }}.", index + 1)
            }

            val step = planner.next(
                task = request,
                snapshot = snapshot,
                history = history.joinToString("\n"),
            )

            when (step) {
                is LeoUiStep.Done -> return Result(true, step.message.ifBlank { "Listo." }, index + 1)
                is LeoUiStep.Abort -> return Result(false, step.reason.ifBlank { "Detuve la automatización de forma segura." }, index + 1)
                is LeoUiStep.Do -> {
                    val stepKey = "${snapshot.packageName}|${snapshot.signature}|${step.describe()}"
                    if (stepKey == lastStepOnSameScreen) {
                        return Result(
                            false,
                            "La pantalla no avanzó con ese paso. Me detuve para no tocar controles al azar.",
                            index + 1,
                        )
                    }
                    lastStepOnSameScreen = stepKey

                    val actionResult = session.perform(step, snapshot)
                    remember(history, "${snapshot.packageName}:${snapshot.signature.take(12)} ${step.describe()} -> ${actionResult.message}")

                    if (actionResult.blocked) return Result(false, actionResult.message, index + 1)
                    if (actionResult.stale) {
                        consecutiveFailures = 0
                        snapshot = session.snapshot()
                        continue
                    }
                    if (!actionResult.success) {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            return Result(false, "No logré identificar un paso seguro para continuar. ${actionResult.message}", index + 1)
                        }
                        snapshot = session.snapshot()
                        continue
                    }

                    consecutiveFailures = 0
                    snapshot = awaitStableSnapshot(session)
                }
            }
        }

        return Result(false, "La tarea necesitó demasiados pasos y la detuve para no tocar controles de más.", MAX_ITERATIONS)
    }

    private suspend fun runDirect(session: LeoUiSession, request: String): Result? {
        val action = NikoDirectUiAction.parse(request) ?: return null
        if (!session.performDirect(action)) return null
        val message = when (action) {
            is NikoDirectUiAction.ClickLabel -> "Toqué ${action.label}."
            is NikoDirectUiAction.TypeFocused -> "Escribí el texto en el campo activo."
            NikoDirectUiAction.ScrollForward -> "Bajé en la pantalla."
            NikoDirectUiAction.ScrollBackward -> "Subí en la pantalla."
            NikoDirectUiAction.Back -> "Cerré esa pantalla."
        }
        return Result(true, message, 1)
    }

    private suspend fun awaitStableSnapshot(session: LeoUiSession): LeoUiSnapshot {
        var previous: LeoUiSnapshot? = null
        repeat(STABLE_SNAPSHOT_ATTEMPTS) {
            settleDelay(SETTLE_POLL_MS)
            val current = session.snapshot()
            val prior = previous
            if (prior != null &&
                current.signature == prior.signature &&
                current.uiRevision == prior.uiRevision
            ) return current
            previous = current
        }
        return previous ?: session.snapshot()
    }

    private fun remember(history: ArrayDeque<String>, value: String) {
        history.addLast(value.take(500))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    companion object {
        private const val MAX_ITERATIONS = 10
        private const val MAX_CONSECUTIVE_FAILURES = 2
        private const val SETTLE_POLL_MS = 180L
        private const val STABLE_SNAPSHOT_ATTEMPTS = 5
        private const val MAX_HISTORY = 6
    }
}
