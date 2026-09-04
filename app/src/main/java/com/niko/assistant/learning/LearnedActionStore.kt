package com.niko.assistant.learning

import com.niko.assistant.memory.MemoryLearning
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/** Durable exact aliases learned only from an explicit user correction. */
class LearnedActionStore(private val directory: File) {
    data class Entry(val phrase: String, val dsl: String, val uses: Int, val updatedAt: Long)

    private val file get() = File(directory, "learned-actions.bin")
    private val backup get() = File(directory, "learned-actions.bak")

    @Synchronized
    fun remember(phrase: String, dsl: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = phraseKey(phrase) ?: return false
        if (!AdaptiveLearningPolicy.canPersistLiteral(dsl) || !validDsl(dsl)) return false
        val entries = readEntries().toMutableList()
        val old = entries.firstOrNull { it.phrase == key }
        entries.removeAll { it.phrase == key }
        entries += Entry(key, dsl, old?.uses ?: 0, nowMs)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        save(entries)
        return true
    }

    @Synchronized
    fun resolve(phrase: String, nowMs: Long = System.currentTimeMillis()): String? {
        val key = phraseKey(phrase) ?: return null
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.phrase == key }
        if (index < 0) return null
        val found = entries[index]
        entries[index] = found.copy(uses = found.uses + 1, updatedAt = nowMs)
        save(entries)
        return found.dsl
    }

    @Synchronized fun count(): Int = readEntries().size

    @Synchronized
    fun clear() {
        listOf(file, backup, File(directory, "learned-actions.pending")).forEach {
            check(!it.exists() || it.delete()) { "No se pudieron borrar las acciones aprendidas." }
        }
    }

    private fun readEntries(): List<Entry> {
        for (candidate in listOf(file, backup)) {
            if (!candidate.isFile || candidate.length() !in MIN_BYTES..MAX_BYTES) continue
            decode(candidate.readBytes())?.let { return it }
        }
        check(!file.exists() && !backup.exists()) { "Las acciones aprendidas están dañadas; se conservaron para recuperación." }
        return emptyList()
    }

    private fun save(entries: List<Entry>) {
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear el aprendizaje de acciones." }
        val pending = File(directory, "learned-actions.pending")
        FileOutputStream(pending).use { stream -> stream.write(encode(entries)); stream.fd.sync() }
        if (file.isFile && decode(file.readBytes()) != null) replace(file, backup)
        replace(pending, file)
    }

    private fun replace(source: File, target: File) {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun encode(entries: List<Entry>): ByteArray {
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(entries.size)
                entries.forEach { entry ->
                    out.writeUTF(entry.phrase); out.writeUTF(entry.dsl)
                    out.writeInt(entry.uses); out.writeLong(entry.updatedAt)
                }
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { output ->
            output.write(body)
            DataOutputStream(output).writeLong(CRC32().apply { update(body) }.value)
        }.toByteArray()
    }

    private fun decode(bytes: ByteArray): List<Entry>? = runCatching {
        require(bytes.size.toLong() in MIN_BYTES..MAX_BYTES)
        val body = bytes.copyOf(bytes.size - 8)
        val checksum = DataInputStream(ByteArrayInputStream(bytes, bytes.size - 8, 8)).readLong()
        require(CRC32().apply { update(body) }.value == checksum)
        DataInputStream(ByteArrayInputStream(body)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val count = input.readInt().also { require(it in 0..MAX_ENTRIES) }
            List(count) {
                Entry(
                    phrase = input.readUTF().also { require(it.length in 2..384) },
                    dsl = input.readUTF().also { require(validDsl(it)) },
                    uses = input.readInt().also { require(it >= 0) },
                    updatedAt = input.readLong().also { require(it >= 0L) },
                )
            }.also { require(input.available() == 0) }
        }
    }.getOrNull()

    private fun validDsl(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_DSL_CHARS ||
            value.any { it == '\u0000' || it.isISOControl() && it !in setOf('\n', '\t') }
        ) return false
        val lines = value.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        return lines.size in 1..4 && lines.all { it.substringBefore('|').uppercase() in SAFE_OPCODES }
    }

    private fun phraseKey(value: String): String? = value.takeIf(AdaptiveLearningPolicy::canPersistLiteral)
        ?.let(MemoryLearning::key)?.take(384)?.takeIf { it.length >= 2 }

    private companion object {
        const val MAGIC = 0x4C454F41
        const val VERSION = 1
        const val MAX_ENTRIES = 64
        const val MAX_DSL_CHARS = 2_000
        const val MIN_BYTES = 20L
        const val MAX_BYTES = 180_000L
        val SAFE_OPCODES = setOf(
            "OPEN_APP", "CAMERA", "TIME", "BATTERY", "TORCH", "VOLUME", "BRIGHTNESS",
            "PANEL", "NAVIGATE", "MAPS", "SPOTIFY", "ALARM", "TIMER", "SEARCH", "VIBRATE",
            "AI_SETTINGS", "SMART_HOME_SETTINGS",
        )
    }
}
