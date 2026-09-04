package com.niko.assistant.localai

import android.content.Context
import java.io.File

/**
 * Storage contract for Leo's two-tier brain.
 *
 * frozen/ is immutable after a validated install. adaptive/ is the only area allowed to grow
 * with the owner. Five decimal gigabytes is a hard ceiling; Leo must also leave free device
 * space instead of filling the phone just because its own quota is not exhausted yet.
 */
class LeoBrainStorage(context: Context) {
    private val appContext = context.applicationContext
    val root: File = File(appContext.filesDir, ROOT_DIRECTORY).apply { mkdirs() }
    val frozen: File = File(root, FROZEN_DIRECTORY).apply { mkdirs() }
    val adaptive: File = File(root, ADAPTIVE_DIRECTORY).apply { mkdirs() }
    val downloads: File = File(root, DOWNLOAD_DIRECTORY).apply { mkdirs() }

    data class Usage(
        val frozenBytes: Long,
        val adaptiveBytes: Long,
        val totalBytes: Long,
        val deviceFreeBytes: Long,
    ) {
        val adaptiveRemainingBytes: Long
            get() = (ADAPTIVE_MAX_BYTES - adaptiveBytes).coerceAtLeast(0L)
    }

    fun usage(): Usage {
        val frozenBytes = directoryBytes(frozen)
        val adaptiveBytes = directoryBytes(adaptive)
        return Usage(
            frozenBytes = frozenBytes,
            adaptiveBytes = adaptiveBytes,
            totalBytes = frozenBytes + adaptiveBytes,
            deviceFreeBytes = root.usableSpace.coerceAtLeast(0L),
        )
    }

    fun canInstallFrozen(archiveBytes: Long, installedBytes: Long): Boolean {
        if (archiveBytes <= 0L || installedBytes !in FROZEN_MIN_BYTES..FROZEN_MAX_BYTES) return false
        val temporaryNeed = archiveBytes + installedBytes
        return root.usableSpace >= temporaryNeed + MIN_DEVICE_FREE_BYTES
    }

    fun canGrowAdaptive(extraBytes: Long): Boolean {
        if (extraBytes < 0L) return false
        val current = usage()
        if (current.adaptiveBytes + extraBytes > ADAPTIVE_MAX_BYTES) return false
        if (current.frozenBytes + current.adaptiveBytes + extraBytes > TOTAL_MAX_BYTES) return false
        return current.deviceFreeBytes >= extraBytes + MIN_DEVICE_FREE_BYTES
    }

    /** Directory reserved for the trainable intent network and its checkpoints. */
    fun trainingDirectory(): File = File(adaptive, "training").apply { mkdirs() }

    /** Exact user corrections/actions remain separate from general learned knowledge. */
    fun correctionsDirectory(): File = File(adaptive, "corrections").apply { mkdirs() }

    fun knowledgeDirectory(): File = File(adaptive, "knowledge").apply { mkdirs() }
    fun indexDirectory(): File = File(adaptive, "indexes").apply { mkdirs() }
    fun checkpointDirectory(): File = File(adaptive, "checkpoints").apply { mkdirs() }

    private fun directoryBytes(directory: File): Long {
        if (!directory.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(directory)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            children.forEach { child ->
                if (child.isDirectory) stack.add(child)
                else if (child.isFile) total += child.length().coerceAtLeast(0L)
            }
        }
        return total
    }

    companion object {
        const val ROOT_DIRECTORY = "leo_brain"
        const val FROZEN_DIRECTORY = "frozen"
        const val ADAPTIVE_DIRECTORY = "adaptive"
        const val DOWNLOAD_DIRECTORY = "downloads"

        // Decimal bytes on purpose: the product contract is 500 MB + 4,500 MB = 5,000 MB.
        const val FROZEN_TARGET_BYTES = 500_000_000L
        const val FROZEN_MIN_BYTES = 490_000_000L
        const val FROZEN_MAX_BYTES = 500_000_000L
        const val ADAPTIVE_MAX_BYTES = 4_500_000_000L
        const val TOTAL_MAX_BYTES = 5_000_000_000L

        // Leo must not consume the final storage on a phone even when its own quota allows it.
        const val MIN_DEVICE_FREE_BYTES = 750_000_000L
    }
}
