package com.niko.assistant.cognitive

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Candidate health gate, called only after transport, size and SHA-256 validation. */
object LeoEvolutionEngine {
    fun validateKnowledge(candidate: File) {
        SQLiteDatabase.openDatabase(candidate.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "La base candidata no supera la comprobación de integridad"
                }
            }
            db.rawQuery("SELECT title, url FROM articles LIMIT 1", null).use {
                check(it.moveToFirst() && !it.getString(0).isNullOrBlank()) { "El conocimiento candidato está vacío" }
            }
            // Exercise the same FTS implementation and join used by live retrieval.
            db.rawQuery("SELECT a.title FROM articles_fts JOIN articles a ON a.id=articles_fts.docid " +
                "WHERE articles_fts MATCH ? LIMIT 1", arrayOf("agua")).use {
                check(it.moveToFirst()) { "La búsqueda del conocimiento candidato no superó la prueba básica" }
            }
        }
    }
}
