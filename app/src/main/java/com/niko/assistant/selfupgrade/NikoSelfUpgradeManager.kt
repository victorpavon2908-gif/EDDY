package com.niko.assistant.selfupgrade

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Diario local de evolución de NIKO. Guarda propuestas, pruebas y rollback.
 * No intenta saltarse la firma/instalador de Android: los cambios nativos deben
 * compilarse y firmarse antes de sustituir el APK instalado.
 */
class NikoSelfUpgradeManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, UpgradeIdentity.evolutionDirectory).apply { mkdirs() }
    private val journal = File(root, "journal.json")

    enum class State { PROPOSED, TESTING, READY, ACTIVATED, FAILED, ROLLED_BACK }

    data class Evolution(
        val id: String,
        val capability: String,
        val summary: String,
        val state: State,
        val createdAt: Long,
        val previousVersion: String,
        val candidateVersion: String,
        val testReport: String = "",
    )

    fun propose(capability: String, summary: String, previousVersion: String, candidatePayload: String): Evolution {
        val now = System.currentTimeMillis()
        val id = "evo-${now}-${sha256(candidatePayload).take(10)}"
        val item = Evolution(
            id = id,
            capability = capability.take(120),
            summary = summary.take(1200),
            state = State.PROPOSED,
            createdAt = now,
            previousVersion = previousVersion.take(120),
            candidateVersion = sha256(candidatePayload).take(16),
        )
        save(readAll() + item)
        File(root, "$id.candidate.txt").writeText(candidatePayload.take(200_000))
        return item
    }

    fun markTesting(id: String): Boolean = mutate(id) { it.copy(state = State.TESTING) }

    fun markReady(id: String, report: String): Boolean = mutate(id) {
        it.copy(state = State.READY, testReport = report.take(4000))
    }

    fun markFailed(id: String, report: String): Boolean = mutate(id) {
        it.copy(state = State.FAILED, testReport = report.take(4000))
    }

    fun markActivated(id: String): Boolean = mutate(id) { it.copy(state = State.ACTIVATED) }

    fun rollback(id: String): Boolean = mutate(id) { it.copy(state = State.ROLLED_BACK) }

    fun history(): List<Evolution> = readAll().sortedByDescending { it.createdAt }

    fun latestStable(): Evolution? = history().firstOrNull { it.state == State.ACTIVATED }

    private fun mutate(id: String, block: (Evolution) -> Evolution): Boolean {
        val list = readAll().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return false
        list[index] = block(list[index])
        save(list)
        return true
    }

    private fun readAll(): List<Evolution> = runCatching {
        if (!journal.isFile) return emptyList()
        val array = JSONArray(journal.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    Evolution(
                        id = o.getString("id"),
                        capability = o.getString("capability"),
                        summary = o.getString("summary"),
                        state = State.valueOf(o.getString("state")),
                        createdAt = o.getLong("createdAt"),
                        previousVersion = o.optString("previousVersion"),
                        candidateVersion = o.optString("candidateVersion"),
                        testReport = o.optString("testReport"),
                    ),
                )
            }
        }
    }.getOrElse { emptyList() }

    private fun save(items: List<Evolution>) {
        val array = JSONArray()
        items.takeLast(100).forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("capability", e.capability)
                put("summary", e.summary)
                put("state", e.state.name)
                put("createdAt", e.createdAt)
                put("previousVersion", e.previousVersion)
                put("candidateVersion", e.candidateVersion)
                put("testReport", e.testReport)
            })
        }
        journal.writeText(array.toString())
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
