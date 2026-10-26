package com.niko.assistant.localai

import java.io.File

/** Same-filesystem rename transaction; retains the last directory until activation succeeds. */
internal object LeoDirectoryActivation {
    private fun backup(active: File) = File(active.parentFile, "${active.name}.previous")

    fun recover(active: File) {
        val previous = backup(active)
        if (previous.isDirectory && (!active.exists() || active.isDirectory && active.listFiles()?.isEmpty() == true)) {
            if (active.exists()) check(active.delete()) { "No pude preparar la recuperación" }
            check(previous.renameTo(active)) { "No pude recuperar el conocimiento anterior" }
        }
    }

    fun activate(candidate: File, active: File, rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }) {
        val candidateParent = candidate.parentFile?.canonicalFile
        val activeParent = active.parentFile?.canonicalFile
        require(candidate.isDirectory && candidate.canonicalFile != active.canonicalFile && candidateParent != null && candidateParent == activeParent)
        recover(active)
        val previous = backup(active)
        check(!previous.exists() || previous.deleteRecursively()) { "No pude limpiar la copia anterior" }
        val hadActive = active.exists()
        if (hadActive) check(rename(active, previous)) { "No pude preservar el conocimiento anterior" }
        if (!rename(candidate, active)) {
            if (hadActive) check(rename(previous, active)) { "La versión anterior está conservada pero requiere recuperación" }
            error("No pude activar el paquete nuevo; conservé la versión anterior")
        }
        // A leftover backup is safe and will be retired by the next activation.
        if (previous.exists()) previous.deleteRecursively()
    }
}
