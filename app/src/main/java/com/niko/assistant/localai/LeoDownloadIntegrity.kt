package com.niko.assistant.localai

import java.io.File

/** Pure contract shared by the downloader and deterministic regressions. */
internal object LeoDownloadIntegrity {
    fun validPartialRange(header: String?, start: Long, total: Long): Boolean {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header.orEmpty().trim()) ?: return false
        val first = match.groupValues[1].toLongOrNull() ?: return false
        val last = match.groupValues[2].toLongOrNull() ?: return false
        val size = match.groupValues[3].toLongOrNull() ?: return false
        return first == start && last >= first && last < total && size == total
    }

    fun requireValidArchive(file: File, size: Long, expectedHash: String, hash: (File) -> String) {
        if (file.length() != size || hash(file) != expectedHash) {
            check(file.delete() || !file.exists()) { "No pude retirar el paquete corrupto" }
            error("Paquete incompleto o corrupto; se retiró para permitir otra descarga")
        }
    }
}
