package com.niko.assistant.localai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.memory.MemoryLearning
import java.io.Closeable
import java.util.Locale
import kotlin.math.ceil

/** Read-only retrieval over Leo's downloaded frozen knowledge brain. */
class LeoFrozenKnowledgeStore(context: Context) : Closeable {
    data class Hit(val title: String, val excerpt: String, val url: String) {
        val answer: String
            get() = buildString {
                append(title.trim().trimEnd('.', ':'))
                append(": ")
                append(excerpt.trim().trimStart('…', ' ').trim())
            }.trim()
    }

    private val appContext = context.applicationContext
    private val manager = LeoFrozenBrainManager(appContext)
    @Volatile private var database: SQLiteDatabase? = null
    @Volatile private var permanentlyFailed = false

    val isAvailable: Boolean get() = !permanentlyFailed && manager.isInstalled()

    @Synchronized
    fun prewarm(): Boolean = openDatabase() != null

    fun recall(message: String): Hit? {
        if (WebQueryRouter.needsCurrentInformation(message)) return null
        val terms = queryTerms(message)
        // One generic word is too ambiguous for an encyclopedia-sized brain. Falling through is
        // safer than answering a different question just because one title happens to match.
        if (terms.size < MIN_QUERY_TERMS) return null
        val db = openDatabase() ?: return null
        return runCatching {
            val strict = query(db, terms, andMode = true)
            val candidates = if (strict.isNotEmpty()) {
                strict
            } else if (terms.size >= 3) {
                query(db, terms, andMode = false)
            } else {
                emptyList()
            }
            candidates
                .filter { isStrongMatch(it, terms) }
                .maxByOrNull { score(it, terms) }
        }.getOrNull()
    }

    private fun query(database: SQLiteDatabase, terms: List<String>, andMode: Boolean): List<Hit> {
        val expression = matchExpression(terms, andMode)
        return database.rawQuery(
            "SELECT a.title, a.url, snippet(articles_fts, '', '', ' … ', 1, 48) " +
                "FROM articles_fts JOIN articles a ON a.id=articles_fts.docid " +
                "WHERE articles_fts MATCH ? LIMIT 12",
            arrayOf(expression),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0).orEmpty().trim()
                    val url = cursor.getString(1).orEmpty().trim()
                    val excerpt = cursor.getString(2).orEmpty().replace(Regex("\\s+"), " ").trim()
                    if (title.isNotBlank() && excerpt.length >= 24) add(Hit(title, excerpt, url))
                }
            }
        }
    }

    @Synchronized
    private fun openDatabase(): SQLiteDatabase? {
        database?.takeIf { it.isOpen }?.let { return it }
        if (permanentlyFailed || !manager.isInstalled()) return null
        val file = manager.databaseFile()
        return runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).also { opened ->
                // A frozen brain is never allowed to create a journal or mutate its FTS index.
                runCatching { opened.rawQuery("PRAGMA query_only=ON", null).close() }
                database = opened
            }
        }.getOrElse {
            permanentlyFailed = true
            null
        }
    }

    override fun close() {
        synchronized(this) {
            runCatching { database?.close() }
            database = null
        }
    }

    companion object {
        private const val MIN_QUERY_TERMS = 2
        private const val MAX_QUERY_TERMS = 8
        private val STOP_WORDS = setOf(
            "a", "al", "algo", "como", "con", "cual", "cuales", "de", "del", "dime", "decime",
            "el", "ella", "en", "es", "esta", "este", "esto", "fue", "la", "las", "le", "leo",
            "lo", "los", "me", "para", "por", "que", "quien", "quienes", "se", "ser", "sobre",
            "su", "sus", "te", "un", "una", "unos", "unas", "y",
        )

        internal fun queryTerms(message: String): List<String> = MemoryLearning.key(message)
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9ñáéíóúü]+"))
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 2 && it !in STOP_WORDS && it.none(Char::isDigit) }
            .distinct()
            .take(MAX_QUERY_TERMS)
            .toList()

        internal fun matchExpression(terms: List<String>, andMode: Boolean): String {
            val safe = terms.map { term -> "\"${term.replace("\"", "\"\"")}\"" }
            return safe.joinToString(if (andMode) " " else " OR ")
        }

        internal fun isStrongMatch(hit: Hit, terms: List<String>): Boolean {
            if (terms.size < MIN_QUERY_TERMS) return false
            val title = MemoryLearning.key(hit.title)
            val excerpt = MemoryLearning.key(hit.excerpt)
            val matched = terms.count { term -> title.contains(term) || excerpt.contains(term) }
            val titleMatches = terms.count(title::contains)
            val requiredCoverage = if (terms.size == 2) 2 else ceil(terms.size * 0.67).toInt()
            // At least one query concept must identify the article title; the rest must have strong
            // coverage in title/excerpt. This prevents a random OR hit from impersonating an answer.
            return titleMatches >= 1 && matched >= requiredCoverage
        }

        private fun score(hit: Hit, terms: List<String>): Int {
            val title = MemoryLearning.key(hit.title)
            val excerpt = MemoryLearning.key(hit.excerpt)
            var score = 0
            terms.forEach { term ->
                if (title.contains(term)) score += 5
                if (excerpt.contains(term)) score += 1
            }
            if (terms.size >= 2 && terms.all { title.contains(it) || excerpt.contains(it) }) score += 6
            return score
        }
    }
}
