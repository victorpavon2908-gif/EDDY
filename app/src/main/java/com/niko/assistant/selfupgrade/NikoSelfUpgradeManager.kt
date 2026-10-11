package com.niko.assistant.selfupgrade

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import android.util.AtomicFile
import java.util.UUID
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

    private val atomicJournal = AtomicFile(journal)
    companion object { private val LOCK = Any() }

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

    fun propose(capability: String, summary: String, previousVersion: String, candidatePayload: String): Evolution = synchronized(LOCK) {
        require(candidatePayload.toByteArray(Charsets.UTF_8).size <= 200_000) { "Candidate exceeds storage limit" }
        val existing = readAll()
        val now = System.currentTimeMillis()
        val id = "evo-${now}-${UUID.randomUUID()}"
        val item = Evolution(
            id = id,
            capability = capability.take(120),
            summary = summary.take(1200),
            state = State.PROPOSED,
            createdAt = now,
            previousVersion = previousVersion.take(120),
            candidateVersion = sha256(candidatePayload).take(16),
        )
        val payload = File(root, "$id.candidate.txt")
        payload.writeText(candidatePayload)
        try { save(existing + item) } catch (failure: Exception) { payload.delete(); throw failure }
        item
    }

    fun markTesting(id: String): Boolean = mutate(id, setOf(State.PROPOSED)) {
        it.copy(state = State.TESTING)
    }

    fun markReady(id: String, report: String): Boolean = report.isNotBlank() && mutate(id, setOf(State.TESTING)) {
        it.copy(state = State.READY, testReport = report.take(4000))
    }

    fun markFailed(id: String, report: String): Boolean = report.isNotBlank() && mutate(id, setOf(State.PROPOSED, State.TESTING, State.READY)) {
        it.copy(state = State.FAILED, testReport = report.take(4000))
    }

    // A journal transition does not install or restore anything. A future installer must
    // supply the observed version AFTER a verified activation/restoration.
    fun markActivated(id: String, observedVersion: String = ""): Boolean = mutate(id, setOf(State.READY)) {
        if (observedVersion != it.candidateVersion) it else it.copy(state = State.ACTIVATED)
    }

    fun rollback(id: String, observedVersion: String = ""): Boolean = mutate(id, setOf(State.ACTIVATED)) {
        if (observedVersion.isBlank() || observedVersion != it.previousVersion) it else it.copy(state = State.ROLLED_BACK)
    }

    /** Explicit memory deletion also removes personal observations stored in proposal payloads. */
    fun clear() = synchronized(LOCK) {
        root.listFiles().orEmpty().filter { it.name in setOf("journal.json", "journal.json.bak", "journal.json.new") || it.name.matches(Regex("evo-.*\\.candidate\\.txt")) }
            .forEach { check(it.delete()) { "No pude eliminar el historial de evolución" } }
    }

    fun history(): List<Evolution> = synchronized(LOCK) { readAll().sortedByDescending { it.createdAt } }

    fun latestStable(): Evolution? = history().firstOrNull { it.state == State.ACTIVATED }

    private fun mutate(id: String, allowed: Set<State>, block: (Evolution) -> Evolution): Boolean = synchronized(LOCK) {
        val list = readAll().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        val current = list[index]
        if (current.state !in allowed) return@synchronized false
        val payload = File(root, "${current.id}.candidate.txt")
        if (!payload.isFile || sha256(payload.readText()).take(16) != current.candidateVersion) return@synchronized false
        val next = block(current)
        if (next == current) return@synchronized false
        list[index] = next
        save(list)
        true
    }

    private fun readAll(): List<Evolution> = runCatching {
        if (!atomicJournal.baseFile.isFile && !File(root, "journal.json.bak").isFile) return emptyList()
        val array = JSONArray(String(atomicJournal.readFully(), Charsets.UTF_8))
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
    }.getOrElse { throw IllegalStateException("No pude leer el historial de evolución; no se sobrescribió", it) }

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
        val stream = atomicJournal.startWrite()
        try {
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            atomicJournal.finishWrite(stream)
        } catch (failure: Exception) {
            atomicJournal.failWrite(stream)
            throw failure
        }
        val retained = items.takeLast(100).map { "${it.id}.candidate.txt" }.toSet()
        root.listFiles().orEmpty().filter { it.name.matches(Regex("evo-.*\\.candidate\\.txt")) && it.name !in retained }
            .forEach { it.delete() }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
