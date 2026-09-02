package com.niko.assistant.memory

import com.niko.assistant.compat.UpgradeIdentity

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** Durable local archive. Prompt windows are bounded; stored turns/lessons are not pruned. */
class NikoMemoryArchive private constructor(context: Context) :
    SQLiteOpenHelper(context, UpgradeIdentity.memoryDatabase, null, DATABASE_VERSION) {

    data class SemanticMemory(
        val key: String,
        val kind: String,
        val text: String,
        val normalized: String,
        val confidence: Float,
        val createdAt: Long,
        val updatedAt: Long,
        val lastAccess: Long,
        val accessCount: Int,
        val expiresAt: Long,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE turns (id INTEGER PRIMARY KEY, role TEXT NOT NULL, text TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY, text TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE lessons (key TEXT PRIMARY KEY, question TEXT NOT NULL, answer TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE legacy_backup (key TEXT PRIMARY KEY, json TEXT NOT NULL)")
        db.execSQL("CREATE INDEX turn_time ON turns(timestamp)")
        createSemanticMemoryTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createSemanticMemoryTable(db)
    }

    private fun createSemanticMemoryTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS semantic_memory (" +
                "key TEXT PRIMARY KEY, kind TEXT NOT NULL, text TEXT NOT NULL, normalized TEXT NOT NULL, " +
                "confidence REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
                "last_access INTEGER NOT NULL DEFAULT 0, access_count INTEGER NOT NULL DEFAULT 0, " +
                "expires_at INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS semantic_memory_kind ON semantic_memory(kind)")
        db.execSQL("CREATE INDEX IF NOT EXISTS semantic_memory_updated ON semantic_memory(updated_at DESC)")
    }

    @Synchronized
    fun importLegacy(turns: String, notes: String, lessons: String) {
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
        } finally {
            db.endTransaction()
        }
    }

    fun appendTurn(role: String, text: String, timestamp: Long) {
        writableDatabase.insertOrThrow("turns", null, ContentValues().apply {
            put("role", role)
            put("text", text)
            put("timestamp", timestamp)
        })
    }

    fun rememberNote(text: String) {
        writableDatabase.insertWithOnConflict("notes", null, ContentValues().apply {
            put("key", MemoryLearning.key(text))
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    fun rememberLesson(lesson: MemoryLearning.Lesson) {
        writableDatabase.insertWithOnConflict("lessons", null, ContentValues().apply {
            put("key", MemoryLearning.key(lesson.question))
            put("question", lesson.question)
            put("answer", lesson.answer)
            put("timestamp", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }

    fun answer(question: String): String? = readableDatabase.rawQuery(
        "SELECT answer FROM lessons WHERE key=?", arrayOf(MemoryLearning.key(question)),
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun recentNotes(limit: Int = 20): List<String> = readableDatabase.rawQuery(
        "SELECT text FROM notes ORDER BY timestamp DESC, rowid DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 40).toString()),
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }.asReversed() }

    fun lessonCount(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM lessons", null).use {
        it.moveToFirst()
        it.getLong(0)
    }

    @Synchronized
    fun upsertSemanticMemory(
        key: String,
        kind: String,
        text: String,
        normalized: String,
        confidence: Float,
        expiresAt: Long,
    ) {
        val now = System.currentTimeMillis()
        val previous = readableDatabase.rawQuery(
            "SELECT created_at, access_count, last_access, confidence FROM semantic_memory WHERE key=?",
            arrayOf(key),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else ExistingSemantic(
                createdAt = cursor.getLong(0),
                accessCount = cursor.getInt(1),
                lastAccess = cursor.getLong(2),
                confidence = cursor.getFloat(3),
            )
        }
        writableDatabase.insertWithOnConflict(
            "semantic_memory",
            null,
            ContentValues().apply {
                put("key", key)
                put("kind", kind)
                put("text", text)
                put("normalized", normalized)
                put("confidence", maxOf(confidence, previous?.confidence ?: 0f))
                put("created_at", previous?.createdAt ?: now)
                put("updated_at", now)
                put("last_access", previous?.lastAccess ?: 0L)
                put("access_count", previous?.accessCount ?: 0)
                put("expires_at", expiresAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        ).also { check(it != -1L) }
    }

    @Synchronized
    fun semanticCandidates(limit: Int, now: Long): List<SemanticMemory> = readableDatabase.rawQuery(
        "SELECT key, kind, text, normalized, confidence, created_at, updated_at, last_access, access_count, expires_at " +
            "FROM semantic_memory WHERE expires_at=0 OR expires_at>? ORDER BY updated_at DESC LIMIT ?",
        arrayOf(now.toString(), limit.coerceIn(1, 500).toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SemanticMemory(
                        key = cursor.getString(0),
                        kind = cursor.getString(1),
                        text = cursor.getString(2),
                        normalized = cursor.getString(3),
                        confidence = cursor.getFloat(4),
                        createdAt = cursor.getLong(5),
                        updatedAt = cursor.getLong(6),
                        lastAccess = cursor.getLong(7),
                        accessCount = cursor.getInt(8),
                        expiresAt = cursor.getLong(9),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun touchSemanticMemory(key: String, now: Long) {
        writableDatabase.execSQL(
            "UPDATE semantic_memory SET last_access=?, access_count=access_count+1 WHERE key=?",
            arrayOf(now, key),
        )
    }

    fun semanticMemoryCount(now: Long): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM semantic_memory WHERE expires_at=0 OR expires_at>?",
        arrayOf(now.toString()),
    ).use {
        it.moveToFirst()
        it.getLong(0)
    }

    fun clearMemory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("turns", "notes", "lessons", "semantic_memory", "legacy_backup").forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class ExistingSemantic(
        val createdAt: Long,
        val accessCount: Int,
        val lastAccess: Long,
        val confidence: Float,
    )

    private fun parseArray(raw: String): JSONArray = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

    companion object {
        private const val DATABASE_VERSION = 2
        @Volatile private var instance: NikoMemoryArchive? = null

        fun get(context: Context): NikoMemoryArchive = instance ?: synchronized(this) {
            instance ?: NikoMemoryArchive(context.applicationContext).also { instance = it }
        }
    }
}
