package com.niko.assistant.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LeoTaskState { PLANNING, RUNNING, WAITING_USER, WAITING_TOOL, VERIFYING, COMPLETED, FAILED, CANCELLED }
data class LeoTaskStep(val index: Int, val state: LeoTaskState, val evidence: String = "")
data class LeoTaskSnapshot(val id: Long, val goal: String, val state: LeoTaskState, val steps: List<LeoTaskStep>)

/** Runs an already validated plan. This is not an unconstrained goal-generating model. */
class LeoTaskExecutor {
    private var counter = 0L
    private val records = linkedMapOf<Long, LeoTaskSnapshot>()
    fun history(): List<LeoTaskSnapshot> = records.values.toList()

    suspend fun <T> run(goal: String, steps: List<T>, execute: suspend (T) -> String): List<String> {
        require(steps.isNotEmpty() && steps.size <= 8)
        val id = ++counter
        var task = LeoTaskSnapshot(id, goal.take(600), LeoTaskState.PLANNING,
            steps.indices.map { LeoTaskStep(it, LeoTaskState.PLANNING) })
        fun publish() { records[id] = task; while (records.size > 20) records.remove(records.keys.first()) }
        fun step(index: Int, state: LeoTaskState, evidence: String = "") {
            task = task.copy(state = state, steps = task.steps.map { if (it.index == index) it.copy(state = state, evidence = evidence.take(800)) else it })
            publish()
        }
        publish()
        var active = 0
        val results = mutableListOf<String>()
        try {
            steps.forEachIndexed { index, command ->
                active = index
                currentCoroutineContext().ensureActive()
                step(index, LeoTaskState.WAITING_TOOL)
                val result = execute(command)
                currentCoroutineContext().ensureActive()
                results += result
                // Returned text is evidence of dispatch only, never proof of a physical effect.
                step(index, LeoTaskState.VERIFYING, result)
            }
            task = task.copy(state = LeoTaskState.VERIFYING)
            publish()
            return results
        } catch (cancelled: CancellationException) {
            step(active, LeoTaskState.CANCELLED, "Interrumpido; no repetir automáticamente un posible efecto externo.")
            throw cancelled
        } catch (error: Exception) {
            step(active, LeoTaskState.FAILED, error.javaClass.simpleName)
            throw error
        }
    }
}
