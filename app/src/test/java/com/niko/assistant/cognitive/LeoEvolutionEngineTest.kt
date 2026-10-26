package com.niko.assistant.cognitive

import android.database.sqlite.SQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LeoEvolutionEngineTest {
    @Test(expected = IllegalStateException::class) fun rejectsKnowledgeWithUnsafeSourceDespiteWorkingIndex() {
        val file = Files.createTempFile("leo-unsafe", ".sqlite").toFile()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY, title TEXT, url TEXT)")
                db.execSQL("CREATE VIRTUAL TABLE articles_fts USING fts4(title, body)")
                db.execSQL("INSERT INTO articles VALUES (1, 'Agua', 'file:///private/data')")
                db.execSQL("INSERT INTO articles_fts(docid,title,body) VALUES (1, 'Agua', 'El agua es una sustancia')")
            }
            LeoEvolutionEngine.validateKnowledge(file)
        } finally { file.delete() }
    }
    @Test fun acceptsReadableSearchableKnowledge() {
        val file = Files.createTempFile("leo-knowledge", ".sqlite").toFile()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY, title TEXT, url TEXT)")
                db.execSQL("CREATE VIRTUAL TABLE articles_fts USING fts4(title, body)")
                db.execSQL("INSERT INTO articles VALUES (1, 'Agua', 'https://example.org/agua')")
                db.execSQL("INSERT INTO articles_fts(docid,title,body) VALUES (1, 'Agua', 'El agua es una sustancia')")
            }
            LeoEvolutionEngine.validateKnowledge(file)
        } finally { file.delete() }
    }
    @Test fun acceptsOfficialOfflineSeedReference() {
        val file = Files.createTempFile("leo-seed", ".sqlite").toFile()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY, title TEXT, url TEXT)")
                db.execSQL("CREATE VIRTUAL TABLE articles_fts USING fts4(title, body)")
                db.execSQL("INSERT INTO articles VALUES (1, 'Leo: identidad', 'leo://seed/identity')")
                db.execSQL("INSERT INTO articles_fts(docid,title,body) VALUES (1, 'Leo: identidad', 'Leo funciona sin internet')")
            }
            LeoEvolutionEngine.validateKnowledge(file)
        } finally { file.delete() }
    }
    @Test(expected = IllegalStateException::class) fun rejectsUntrustedInternalScheme() {
        val file = Files.createTempFile("leo-unsafe-seed", ".sqlite").toFile()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY, title TEXT, url TEXT)")
                db.execSQL("CREATE VIRTUAL TABLE articles_fts USING fts4(title, body)")
                db.execSQL("INSERT INTO articles VALUES (1, 'Leo falso', 'leo://external/identity')")
                db.execSQL("INSERT INTO articles_fts(docid,title,body) VALUES (1, 'Leo falso', 'Contenido externo')")
            }
            LeoEvolutionEngine.validateKnowledge(file)
        } finally { file.delete() }
    }
    @Test(expected = IllegalStateException::class) fun rejectsEmptyKnowledge() {
        val file = Files.createTempFile("leo-empty", ".sqlite").toFile()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE articles (id INTEGER PRIMARY KEY, title TEXT, url TEXT)")
            }
            LeoEvolutionEngine.validateKnowledge(file)
        } finally { file.delete() }
    }
}
