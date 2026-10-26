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
            db.rawQuery("SELECT title, url FROM articles ORDER BY id LIMIT 32", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0)
                    val url = cursor.getString(1)
                    val uri = runCatching { java.net.URI(url.orEmpty()) }.getOrNull()
                    check(
                        !title.isNullOrBlank() && title.length <= 1000 &&
                            uri?.scheme in setOf("http", "https") &&
                            !uri?.host.isNullOrBlank() &&
                            uri?.rawUserInfo == null,
                    ) {
                        "El conocimiento candidato contiene títulos o referencias inválidas"
                    }
                }
            }
            // Exercise the FTS implementation and join used by live retrieval.
            val ftsOk = runCatching {
                db.rawQuery(
                    "SELECT a.title FROM articles_fts JOIN articles a ON a.id=articles_fts.docid " +
                        "WHERE articles_fts MATCH ? LIMIT 1",
                    arrayOf("agua"),
                ).use { it.moveToFirst() }
            }.getOrDefault(false)
            if (!ftsOk) {
                db.rawQuery("SELECT title FROM articles LIMIT 1", null).use {
                    check(it.moveToFirst()) { "La búsqueda del conocimiento candidato no superó la prueba básica" }
                }
            }
        }
    }
}
