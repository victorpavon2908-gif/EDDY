package com.niko.assistant.learning

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Atomic, versioned checkpoint with a previous known-good copy. Call from an IO worker. */
class AdaptiveIntentStore(
    private val directory: File,
    private val bundledCheckpoint: (() -> ByteArray?)? = null,
) {
    private val file get() = File(directory, "intent-network.bin")
    private val backup get() = File(directory, "intent-network.bak")

    @Synchronized fun load(): OnlineIntentNetwork {
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists() || candidate.length() !in 16_000L..150_000L) continue
            runCatching { OnlineIntentNetwork.decode(candidate.readBytes()) }.getOrNull()?.let { network ->
                if (bundledCheckpoint != null && network.ensureSeeded()) save(network)
                return network
            }
        }
        check(!file.exists() && !backup.exists()) { "Los datos de aprendizaje están dañados; se conservaron para recuperación." }
        if (bundledCheckpoint != null) {
            runCatching { bundledCheckpoint.invoke()?.let(OnlineIntentNetwork::decode) }.getOrNull()?.let { network ->
                network.ensureSeeded()
                return network
            }
            return OnlineIntentNetwork.pretrained()
        }
        return OnlineIntentNetwork()
    }

    @Synchronized fun save(network: OnlineIntentNetwork) {
        check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear la memoria adaptativa." }
        val temporary = File(directory, "intent-network.pending")
        FileOutputStream(temporary).use { stream -> stream.write(network.encode()); stream.fd.sync() }
        if (file.exists() && OnlineIntentNetwork.decode(file.readBytes()) != null) replace(file, backup)
        replace(temporary, file)
    }

    @Synchronized fun clear() {
        listOf(file, backup, File(directory, "intent-network.pending")).forEach {
            check(!it.exists() || it.delete()) { "No se pudo borrar el aprendizaje local." }
        }
    }

    private fun replace(source: File, target: File) {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
}
