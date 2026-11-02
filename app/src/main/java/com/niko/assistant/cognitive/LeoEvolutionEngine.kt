package com.niko.assistant.cognitive

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Candidate health gate, called only after transport, size and SHA-256 validation. */
object LeoEvolutionEngine {
    fun validateKnowledge(candidate: File) {
        check(candidate.isFile && candidate.length() > 0) { "No hay una base candidata completa" }
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        SQLiteDatabase.openDatabase(candidate.absolutePath, null, flags).use { db ->
            db.rawQuery("SELECT title, url FROM articles LIMIT 1", null).use {
                check(it.moveToFirst() && !it.getString(0).isNullOrBlank()) { "El conocimiento candidato está vacío" }
            }
            // Sample content and provenance: reject malformed URLs before activation.
            var searchProbe: Pair<Long, String>? = null
            db.rawQuery("SELECT id, title, url FROM articles ORDER BY id LIMIT 32", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)
                    val url = cursor.getString(2)
                    if (searchProbe == null) Regex("[\\p{L}\\p{N}]+").find(title.orEmpty())?.value?.let {
                        searchProbe = cursor.getLong(0) to it
                    }
                    val uri = runCatching { java.net.URI(url.orEmpty()) }.getOrNull()
                    // Built-in first-party lessons use leo://seed/<slug>, which is
                    // deliberately not a public webpage. They are safe only under
                    // this exact namespace; arbitrary custom schemes remain rejected.
                    val officialSeed = uri?.scheme == "leo" &&
                        uri.host == "seed" && uri.port == -1 && uri.rawUserInfo == null &&
                        uri.rawQuery == null && uri.rawFragment == null &&
                        Regex("^/[a-z0-9][a-z0-9-]{0,80}$").matches(uri.path.orEmpty())
                    val publicSource = uri?.scheme in setOf("http", "https") &&
                        !uri?.host.isNullOrBlank() && uri?.rawUserInfo == null
                    check(
                        !title.isNullOrBlank() && title.length <= 1000 &&
                            (officialSeed || publicSource),
                    ) {
                        "El conocimiento candidato contiene títulos o referencias inválidas"
                    }
                }
            }
            // Search an actual sampled title, not a fixed word that might be absent.
            val probe = checkNotNull(searchProbe) { "No hay títulos que permitan probar la búsqueda." }
            val ftsOk = runCatching {
                db.rawQuery(
                    "SELECT a.id FROM articles_fts JOIN articles a ON a.id=articles_fts.docid " +
                        "WHERE articles_fts MATCH ? AND a.id=? LIMIT 1",
                    arrayOf("\"${probe.second}\"", probe.first.toString()),
                ).use { it.moveToFirst() }
            }.getOrDefault(false)
            check(ftsOk) { "El índice de búsqueda del conocimiento candidato no funciona o no contiene sus artículos." }
        }
    }
}
