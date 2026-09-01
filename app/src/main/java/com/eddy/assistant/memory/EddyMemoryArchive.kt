package com.eddy.assistant.memory

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** Durable local archive. Prompt windows are bounded; stored turns/lessons are not pruned. */
class EddyMemoryArchive private constructor(context: Context) : SQLiteOpenHelper(context, "eddy_memory_archive.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE turns (id INTEGER PRIMARY KEY, role TEXT NOT NULL, text TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY, text TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE lessons (key TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE legacy_backup (key TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX turn_time ON turns(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun importLegacy(turns: String, notes: String, lessons: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val imported = db.rawQuery("SELECT 1 FROM metadata WHERE key='imported_v1'", null).use { it.moveToFirst() }
            if (!imported) {
                // Preserve the exact original data, including malformed sections, before
                // importing readable records. One damaged preference must not block voice.
                mapOf("turns" to turns, "notes" to notes, "lessons" to lessons).forEach { (key, json) ->
                    db.insertOrThrow("legacy_backup", null, ContentValues().apply { put("key", key); put("json", json) })
                }
                val oldTurns = parseArray(turns)
                for (i in 0 until oldTurns.length()) oldTurns.optJSONObject(i)?.let { item ->
                    item.optString("text").takeIf { it.isNotBlank() }?.let {
                        appendTurn(item.optString("role", "user"), it, item.optLong("timestamp"))
                    }
                }
                val oldNotes = parseArray(notes)
                for (i in 0 until oldNotes.length()) (oldNotes.opt(i) as? String)?.takeIf { it.isNotBlank() }?.let(::rememberNote)
                val oldLessons = parseArray(lessons)
                for (i in 0 until oldLessons.length()) oldLessons.optJSONObject(i)?.let { item ->
                    val question = item.optString("question")
                    val answer = item.optString("answer")
                    if (question.isNotBlank() && answer.isNotBlank()) rememberLesson(MemoryLearning.Lesson(question, answer))
                }
                db.execSQL("INSERT INTO metadata(key) VALUES ('imported_v1')")
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun appendTurn(role: String, text: String, timestamp: Long) {
        writableDatabase.insertOrThrow("turns", null, ContentValues().apply {
            put("role", role); put("text", text); put("timestamp", timestamp)
        })
    }

    fun rememberNote(text: String) {
        writableDatabase.insertWithOnConflict("notes", null, ContentValues().apply {
            put("key", MemoryLearning.key(text)); put("text", text); put("timestamp", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    fun rememberLesson(lesson: MemoryLearning.Lesson) {
        writableDatabase.insertWithOnConflict("lessons", null, ContentValues().apply {
            put("key", MemoryLearning.key(lesson.question)); put("question", lesson.question)
            put("answer", lesson.answer); put("timestamp", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    fun answer(question: String): String? = readableDatabase.rawQuery(
        "SELECT answer FROM lessons WHERE key=?", arrayOf(MemoryLearning.key(question)),
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun recentNotes(limit: Int = 20): List<String> = readableDatabase.rawQuery(
        "SELECT text FROM notes ORDER BY timestamp DESC, rowid DESC LIMIT ?", arrayOf(limit.coerceIn(1, 40).toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }.asReversed() }

    fun lessonCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM lessons", null).use { it.moveToFirst(); it.getLong(0) }

    fun clearMemory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("turns", "notes", "lessons", "legacy_backup").forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun parseArray(raw: String): JSONArray = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

    companion object {
        @Volatile private var instance: EddyMemoryArchive? = null
        fun get(context: Context): EddyMemoryArchive = instance ?: synchronized(this) {
            instance ?: EddyMemoryArchive(context.applicationContext).also { instance = it }
        }
    }
}
