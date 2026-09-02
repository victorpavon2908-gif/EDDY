package com.niko.assistant.ai

import com.niko.assistant.brain.WebQueryRouter
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Investigación web nativa de LEO sin API key ni backend propio.
 * Descubre por RSS/HTML público, filtra por el tema real de la pregunta, lee páginas y
 * construye el resumen extractivo localmente. No manda la consulta a una IA remota.
 */
object LeoNativeWebSearch {
    internal data class Hit(
        val title: String,
        val url: String,
        val snippet: String,
        val publisher: String = "",
        val published: String = "",
        val rank: Int = 0,
        val articleText: String = "",
    )

    internal data class Fetch(val body: String, val finalUrl: String)
    private data class SentenceCandidate(val text: String, val sourceIndex: Int, val score: Double)

    suspend fun search(query: String): NikoAiReply {
        val deadline = System.nanoTime() + SEARCH_BUDGET_MS * 1_000_000L
        return search(query) { url, accept, limit -> fetchText(url, accept, limit, deadline) }
    }

    /** Injectable transport exercises discovery, redirects, filtering and summary together. */
    internal suspend fun search(
        query: String,
        fetch: suspend (String, String, Int) -> Fetch?,
    ): NikoAiReply = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().take(MAX_QUERY_CHARS)
        val subject = subjectQuery(cleanQuery)
        val terms = subjectTokens(subject)
        if (terms.isEmpty()) return@withContext NikoAiReply("Decime qué tema querés que busque.", false, emptyList())
        val current = WebQueryRouter.needsRecentInformation(cleanQuery)
        val hits = linkedMapOf<String, Hit>()
        val discovered = coroutineScope {
            listOf(
                async { searchBing(subject, fetch) },
                async { if (current) searchGoogleNews(subject, fetch) else searchDuckDuckGo(subject, fetch) },
            ).awaitAll().flatten()
        }
        discovered.forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        // Count useful hits, not login screens or unrelated RSS entries.
        if (current && hits.values.count { isRelevantTo(subject, it) } < MIN_RESULTS) {
            searchDuckDuckGo(subject, fetch).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        }
        val relevant = hits.values.filter { isRelevantTo(subject, it) }
            .sortedWith(compareByDescending<Hit> { relevanceScore(it, terms) }.thenBy { it.rank })
            .take(MAX_RESULTS)
        if (relevant.isEmpty()) return@withContext noResults(subject)

        val enriched = coroutineScope {
            relevant.mapIndexed { index, original -> async {
                val page = fetch(original.url, PAGE_ACCEPT, PAGE_LIMIT)
                if (page != null && (isBlockedPage(page) || !safePublicUrl(page.finalUrl))) return@async null
                val articleText = page?.body?.let(::extractReadableText).orEmpty()
                // A changed/redirected page cannot contribute text merely because its RSS title matched.
                val usefulArticle = articleText.takeIf {
                    isRelevantTo(subject, original.copy(title = "", snippet = it))
                }.orEmpty()
                if (articleText.length >= 80 && usefulArticle.isBlank()) return@async null
                original.copy(
                    url = page?.finalUrl ?: original.url,
                    publisher = hostOf(page?.finalUrl ?: original.url),
                    rank = index,
                    articleText = usefulArticle,
                )
            } }.awaitAll().filterNotNull().distinctBy(::hitKey)
        }
        if (enriched.isEmpty()) return@withContext noResults(subject)
        val summary = summarize(subject, enriched, current)
        val sources = enriched.distinctBy { it.url }.take(MAX_SOURCES)
            .map { NikoWebSource(it.title.ifBlank { hostOf(it.url) }, it.url) }
        NikoAiReply(
            text = summary.ifBlank { "Encontré enlaces sobre $subject, pero no pude extraer suficiente texto para resumirlos." },
            webUsed = true,
            sources = sources,
            evidence = "Resumen local de extractos web. Tener varias fuentes no implica que sus afirmaciones estén confirmadas.",
        )
    }

    private fun noResults(subject: String) = NikoAiReply(
        "No pude obtener fuentes útiles sobre $subject. Puede fallar la conexión o el buscador; no voy a darte páginas ajenas al tema.",
        false, emptyList(),
    )

    internal fun subjectQuery(query: String): String {
        val requested = WebQueryRouter.explicitQuery(query) ?: query
        val normalized = normalize(requested)
            .replace(Regex("^(?:informacion(?:es)?|datos|algo)\\s+(?:(?:sobre|acerca de|de|por)\\s+)?"), "")
        val tokens = normalized.split(' ').filter { it.length >= 2 && it !in QUERY_FILLERS }
        return tokens.joinToString(" ").trim().take(180).takeUnless { it in setOf("informacion", "datos", "algo") }.orEmpty()
    }

    internal fun isRelevantTo(query: String, hit: Hit): Boolean {
        if (!safePublicUrl(hit.url) || isLoginUrl(hit.url) || isBlockedTitle(hit.title)) return false
        val terms = subjectTokens(subjectQuery(query))
        return relevanceScore(hit, terms) > 0.0
    }

    private fun subjectTokens(subject: String): Set<String> = meaningfulTokens(subject).map(::canonicalTerm).toSet()

    private fun canonicalTerm(term: String): String = when (term) {
        "btc" -> "bitcoin"
        "eth" -> "ethereum"
        else -> term
    }

    private fun relevanceScore(hit: Hit, terms: Set<String>): Double {
        if (terms.isEmpty()) return 0.0
        val title = meaningfulTokens(hit.title).map(::canonicalTerm).toSet()
        val body = meaningfulTokens("${hit.title} ${hit.snippet}").map(::canonicalTerm).toSet()
        val titleOverlap = title.intersect(terms).size
        val bodyOverlap = body.intersect(terms).size
        val coverage = bodyOverlap.toDouble() / terms.size
        // One generic word (e.g. información) must never validate an unrelated result.
        if (bodyOverlap == 0 || (terms.size > 1 && (bodyOverlap < 2 || coverage < 0.5))) return 0.0
        return titleOverlap * 2.5 + bodyOverlap * 1.5 + coverage * 2.0
    }

    private suspend fun searchBing(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://www.bing.com/search?format=rss&setlang=es&q=$encoded"
        val xml = fetch(url, RSS_ACCEPT, RSS_LIMIT)?.body ?: return emptyList()
        return parseRss(xml, "Bing")
    }

    private suspend fun searchGoogleNews(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://news.google.com/rss/search?q=$encoded&hl=es-419&gl=NI&ceid=NI:es-419"
        val xml = fetch(url, RSS_ACCEPT, RSS_LIMIT)?.body ?: return emptyList()
        return parseRss(xml, "Google News")
    }

    private suspend fun searchDuckDuckGo(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetch(url, HTML_ACCEPT, SEARCH_HTML_LIMIT)?.body ?: return emptyList()
        return parseDuckDuckGo(html)
    }

    internal fun parseDuckDuckGo(html: String): List<Hit> = DDG_BLOCK.findAll(html).mapIndexedNotNull { index, block ->
        val anchor = ANCHOR.findAll(block.value).firstOrNull { "result__a" in attributes(it.value)["class"].orEmpty().split(' ') }
            ?: return@mapIndexedNotNull null
        val href = attributes(anchor.value)["href"]?.let(::decodeSearchUrl) ?: return@mapIndexedNotNull null
        if (!safePublicUrl(href)) return@mapIndexedNotNull null
        Hit(
            title = htmlToText(anchor.groupValues[1]).take(220),
            url = href,
            snippet = DDG_SNIPPET.find(block.value)?.groupValues?.get(1)?.let(::htmlToText).orEmpty().take(900),
            publisher = hostOf(href), rank = index,
        )
    }.take(MAX_RESULTS * 2).toList()

    internal fun parseRss(xml: String, fallbackPublisher: String = ""): List<Hit> {
        if (xml.isBlank()) return emptyList()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val items = document.getElementsByTagName("item")
            buildList {
                for (i in 0 until minOf(items.length, MAX_RESULTS * 2)) {
                    val item = items.item(i) as? Element ?: continue
                    val title = childText(item, "title").let(::htmlToText).take(220)
                    val link = childText(item, "link").trim()
                    if (!safePublicUrl(link)) continue
                    val publisher = childText(item, "source").let(::htmlToText).ifBlank { hostOf(link).ifBlank { fallbackPublisher } }
                    val snippet = childText(item, "description").let(::htmlToText).take(900)
                    add(Hit(title, link, snippet, publisher, childText(item, "pubDate").take(100), i))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun childText(element: Element, tag: String): String {
        val nodes = element.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0)?.textContent.orEmpty() else ""
    }

    private fun fetchText(url: String, accept: String, limit: Int, deadline: Long): Fetch? {
        var next = url
        repeat(5) {
            if (!safePublicUrl(next) || remainingMs(deadline) <= 0) return null
            if (isLoginUrl(next)) return Fetch("", next)
            var connection: HttpURLConnection? = null
            try {
                connection = URL(next).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = minOf(CONNECT_TIMEOUT_MS, remainingMs(deadline).coerceAtLeast(1))
                connection.readTimeout = minOf(READ_TIMEOUT_MS, remainingMs(deadline).coerceAtLeast(1))
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("Accept-Language", "es-NI,es;q=0.9,en;q=0.7")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: return null
                    next = URI(next).resolve(location).toString()
                } else {
                    if (status !in 200..299) return null
                    val type = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                    if (type.isNotBlank() && !listOf("html", "xml", "text/plain").any(type::contains)) return null
                    val body = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
                        val buffer = ByteArray(8192)
                        while (output.size() < limit) {
                            if (remainingMs(deadline) <= 0) return null
                            connection.readTimeout = minOf(READ_TIMEOUT_MS, remainingMs(deadline).coerceAtLeast(1))
                            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                        }
                        String(output.toByteArray(), charsetFrom(type))
                    }
                    return Fetch(body, next)
                }
            } catch (_: Exception) { return null }
            finally { connection?.disconnect() }
        }
        return null
    }

    private fun remainingMs(deadline: Long): Int = ((deadline - System.nanoTime()) / 1_000_000L)
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    internal fun isBlockedPage(page: Fetch): Boolean {
        if (isLoginUrl(page.finalUrl)) return true
        val title = TITLE.find(page.body)?.groupValues?.get(1)?.let(::htmlToText).orEmpty()
        return isBlockedTitle(title) || PASSWORD_INPUT.containsMatchIn(page.body) ||
            listOf("verify you are human", "verifica que eres humano", "unusual traffic", "just a moment").any(normalize(title)::contains)
    }

    private fun isBlockedTitle(title: String): Boolean = LOGIN_TITLE.containsMatchIn(normalize(title))

    private fun isLoginUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return true
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        return host in LOGIN_HOSTS || host.startsWith("login.") || host.startsWith("signin.") ||
            uri.path.orEmpty().lowercase(Locale.ROOT).split('/').any { it in setOf("login", "signin", "sign-in", "oauth", "authorize") }
    }

    private fun charsetFrom(contentType: String?): Charset {
        val raw = contentType?.let { Regex("(?i)charset=([^;\\s]+)").find(it)?.groupValues?.getOrNull(1) }?.trim(' ', '"', '\'')
        return raw?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    internal fun extractReadableText(html: String): String {
        if (html.isBlank()) return ""
        val cleaned = html.replace(SCRIPT_STYLE, " ").replace(COMMENTS, " ")
        val metaDescriptions = META_TAG.findAll(cleaned).mapNotNull { tag ->
            val attrs = attributes(tag.value)
            val key = (attrs["name"] ?: attrs["property"]).orEmpty().lowercase(Locale.ROOT)
            if (key in DESCRIPTION_KEYS) attrs["content"]?.let(::htmlToText) else null
        }.filter { it.length >= 40 && !looksLikeBoilerplate(it) }.take(2).toList()
        val paragraphs = P_TAG.findAll(cleaned)
            .map { htmlToText(it.groupValues[1]) }
            .filter { it.length in 45..900 && !looksLikeBoilerplate(it) }
            .take(MAX_PARAGRAPHS)
            .toList()
        return (metaDescriptions + paragraphs)
            .distinctBy { normalize(it).take(180) }
            .joinToString(" ")
            .take(MAX_ARTICLE_TEXT)
    }

    internal fun summarize(query: String, hits: List<Hit>, current: Boolean): String {
        val queryTerms = subjectTokens(subjectQuery(query))
        val candidates = mutableListOf<SentenceCandidate>()
        hits.forEachIndexed { sourceIndex, hit ->
            if (!isRelevantTo(query, hit)) return@forEachIndexed
            val raw = listOf(hit.title, hit.snippet, hit.articleText).filter(String::isNotBlank).joinToString(". ")
            splitSentences(raw).take(MAX_SENTENCES_PER_SOURCE).forEach { sentence ->
                val tokens = meaningfulTokens(sentence).map(::canonicalTerm).toSet()
                if (tokens.isEmpty()) return@forEach
                val overlap = tokens.intersect(queryTerms).size
                if (queryTerms.isNotEmpty() && overlap == 0) return@forEach
                val numbers = if (sentence.any(Char::isDigit)) 0.8 else 0.0
                val rankBoost = (MAX_RESULTS - hit.rank).coerceAtLeast(0) * 0.20
                val titleBoost = if (normalize(hit.title) == normalize(sentence)) 0.6 else 0.0
                val score = overlap * 3.5 + numbers + rankBoost + titleBoost + minOf(tokens.size, 35) * 0.02
                if (score >= 0.8) candidates += SentenceCandidate(sentence, sourceIndex, score)
            }
        }

        val selected = mutableListOf<SentenceCandidate>()
        candidates.sortedByDescending { it.score }.forEach { candidate ->
            if (selected.size >= SUMMARY_SENTENCES) return@forEach
            if (selected.any { similar(it.text, candidate.text) }) return@forEach
            selected += candidate
        }

        if (current && hits.size >= 2 && selected.map { it.sourceIndex }.distinct().size < 2) {
            val used = selected.map { it.sourceIndex }.toSet()
            candidates.filter { it.sourceIndex !in used }.maxByOrNull { it.score }?.let { second ->
                if (selected.none { similar(it.text, second.text) }) selected += second
            }
        }

        val body = selected
            .sortedBy { it.sourceIndex }
            .joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(SUMMARY_LIMIT)
        if (body.isBlank()) return ""

        val publishers = hits.map(::publisherKey).filter(String::isNotBlank).distinct().size
        val lead = if (current) "Busqué información reciente en Internet. " else "Busqué en Internet. "
        val tail = when {
            publishers >= 3 -> " Encontré $publishers fuentes relacionadas."
            publishers == 2 -> " Encontré dos fuentes relacionadas."
            else -> ""
        }
        return (lead + body + tail).take(SUMMARY_LIMIT + 160)
    }

    private fun splitSentences(text: String): List<String> = text
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+"))
        .map(String::trim)
        .filter { it.length in 25..420 && !looksLikeBoilerplate(it) }

    private fun meaningfulTokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .asSequence()
        .map(String::trim)
        .filter { it.length >= 2 && it !in STOP_WORDS && it !in QUERY_FILLERS }
        .toSet()

    private fun similar(a: String, b: String): Boolean {
        val left = meaningfulTokens(a)
        val right = meaningfulTokens(b)
        if (left.isEmpty() || right.isEmpty()) return false
        val union = left.union(right).size.toDouble()
        return union > 0 && left.intersect(right).size / union >= 0.72
    }

    private fun attributes(tag: String): Map<String, String> = ATTR.findAll(tag).associate { match ->
        match.groupValues[1].lowercase(Locale.ROOT) to decodeEntities(match.groupValues[3])
    }

    private fun htmlToText(value: String): String = decodeEntities(
        value.replace(Regex("(?i)<br\\s*/?>"), ". ").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
    )

    private fun decodeEntities(value: String): String {
        var result = value
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
        result = NUMERIC_ENTITY.replace(result) { match ->
            val raw = match.groupValues[1]
            val code = if (raw.startsWith("x", true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeSearchUrl(rawHref: String): String? {
        val decodedHtml = decodeEntities(rawHref)
        val absolute = when {
            decodedHtml.startsWith("//") -> "https:$decodedHtml"
            decodedHtml.startsWith("/") -> "https://duckduckgo.com$decodedHtml"
            else -> decodedHtml
        }
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
        if (uri.host?.lowercase(Locale.ROOT)?.endsWith("duckduckgo.com") == true && uri.rawQuery != null) {
            val params = uri.rawQuery.split('&').mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            params["uddg"]?.let { return runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        }
        return absolute
    }

    private fun safePublicUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") || uri.userInfo != null) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host.contains(':') || !host.contains('.') || Regex("^(?:0|127)\\.").containsMatchIn(host)) return false
        if (Regex("^(?:10\\.|192\\.168\\.|169\\.254\\.|172\\.(?:1[6-9]|2\\d|3[01])\\.)").containsMatchIn(host)) return false
        return true
    }

    private fun looksLikeBoilerplate(text: String): Boolean {
        val value = normalize(text)
        return BOILERPLATE.any(value::contains)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun hitKey(hit: Hit): String = "${hostOf(hit.url)}|${normalize(hit.title).take(100)}"
    private fun publisherKey(hit: Hit): String = normalize(hit.publisher).takeIf(String::isNotBlank) ?: hostOf(hit.url)
    private fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("")

    private val DDG_BLOCK = Regex("""<div\b[^>]*class=["'][^"']*\bresult\b[^"']*["'][^>]*>.*?(?=<div\b[^>]*class=["'][^"']*\bresult\b[^"']*["'][^>]*>|$)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ANCHOR = Regex("""<a\b[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val PASSWORD_INPUT = Regex("""<input\b[^>]*type\s*=\s*["']password["']""", RegexOption.IGNORE_CASE)
    private val LOGIN_TITLE = Regex("""^(?:inicio de sesion|inicia(?:r)? sesion|inicie sesion|sign in|log in|login|access denied|acceso denegado)(?:\b|$)""")
    private val LOGIN_HOSTS = setOf("accounts.google.com", "account.live.com", "outlook.live.com", "outlook.office.com", "outlook.office365.com")
    private val DDG_SNIPPET = Regex("<(?:a|div)[^>]+class=[\\\"'][^\\\"']*result__snippet[^\\\"']*[\\\"'][^>]*>(.*?)</(?:a|div)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_STYLE = Regex("<(script|style|noscript|svg)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val P_TAG = Regex("<p\\b[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\\\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
    private val NUMERIC_ENTITY = Regex("&#(x?[0-9a-fA-F]+);")
    private val DESCRIPTION_KEYS = setOf("description", "og:description", "twitter:description")
    private val BOILERPLATE = listOf(
        "aceptar cookies", "politica de privacidad", "suscribete", "inicia sesion", "todos los derechos reservados",
        "javascript", "newsletter", "a traves de un recorrido", "exploraremos las diferencias", "cuando se usa",
    )
    private val QUERY_FILLERS = setOf(
        "acerca", "tema", "tal", "cosa", "un", "una", "en", "al",
        "leo", "que", "ha", "han", "pasado", "pasa", "paso", "esta", "ahora", "mismo",
        "ultimas", "ultima", "noticias", "dime", "decime", "cuentame", "contame", "hablame", "sobre", "del", "de", "el",
        "la", "los", "las", "con", "por", "favor", "quiero", "saber", "busca", "buscame", "internet",
    )
    private val STOP_WORDS = setOf(
        "que", "como", "para", "por", "con", "una", "uno", "unos", "unas", "del", "las", "los", "este", "esta", "esto", "ese", "esa",
        "desde", "hasta", "sobre", "entre", "sin", "hay", "fue", "son", "ser", "sea", "han", "mas", "muy", "pero", "sus", "segun", "tambien",
        "hoy", "ahora", "actualmente", "pasado", "noticias", "reciente", "ayer", "the", "and", "for", "with", "from", "this", "that", "are", "was", "were", "has", "have",
    )

    private const val RSS_ACCEPT = "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5"
    private const val HTML_ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5"
    private const val PAGE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.8,text/plain;q=0.7,*/*;q=0.2"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36 LEO/1.0"
    private const val SEARCH_BUDGET_MS = 20_000L
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000
    private const val MAX_QUERY_CHARS = 500
    private const val MAX_RESULTS = 8
    private const val MIN_RESULTS = 4
    private const val MAX_SOURCES = 6
    private const val MAX_PARAGRAPHS = 16
    private const val MAX_ARTICLE_TEXT = 12_000
    private const val MAX_SENTENCES_PER_SOURCE = 18
    private const val SUMMARY_SENTENCES = 5
    private const val SUMMARY_LIMIT = 1_150
    private const val RSS_LIMIT = 700_000
    private const val SEARCH_HTML_LIMIT = 900_000
    private const val PAGE_LIMIT = 650_000
}
