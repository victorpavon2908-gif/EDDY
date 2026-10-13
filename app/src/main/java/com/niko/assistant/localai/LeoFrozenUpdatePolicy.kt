package com.niko.assistant.localai

import java.io.File

/** A resumed archive must belong to the exact release being installed. */
internal object LeoFrozenUpdatePolicy {
    fun preparePartial(archive: File, expectedHash: String) {
        require(expectedHash.matches(Regex("[0-9a-f]{64}")))
        archive.parentFile?.mkdirs()
        val revision = File(archive.parentFile, "${archive.name}.sha256")
        val previous = if (revision.isFile) revision.readText().trim() else ""
        if (previous != expectedHash) {
            check(!archive.exists() || archive.delete()) { "No pude descartar la descarga de otra versión" }
            revision.writeText(expectedHash)
        }
    }
}
