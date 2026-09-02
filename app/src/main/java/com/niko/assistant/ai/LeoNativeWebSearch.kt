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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Búsqueda web nativa de LEO, sin API key ni backend propio.
 *
 * Usa endpoints públicos que un navegador puede consultar directamente (RSS/HTML),
 * entra a varias páginas, extrae texto útil y construye un resumen extractivo local.
 * No manda el contenido a Groq, Gemini, OpenAI ni a otro modelo remoto.
 */
object LeoNativeWebSearch {
    private data class Hit(
        val title: String,
        val url: String,
        val snippet: String,
        val publisher: String = "",
        val published: String = "",
        val rank: Int = 0,
        val articleText: String = "",
    )

    private data class Fetch(val body: String, val finalUrl: String)
    private data class SentenceCandidate(val text: String, val sourceIndex: Int, val score: Double)

    suspend fun search(query: String): NikoAiReply = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().take(MAX_QUERY_CHARS)
        if (cleanQuery.isBlank()) return@withContext NikoAiReply("Decime qué querés que busque.", false, emptyList())

        val current = WebQueryRouter.needsCurrentInformation(cleanQuery)
        val hits = linkedMapOf<String, Hit>()

        // Bing RSS entrega URLs directas y texto sin necesitar token ni SDK.
        searchBing(cleanQuery).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }

        // Para actualidad agregamos una segunda fuente de descubrimiento. No es una API
        // autenticada: es el RSS público de Google News que también abre cualquier lector.
        if (current || hits.size < MIN_RESULTS) {
            searchGoogleNews(cleanQuery).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        }

        // Tercer camino de respaldo para no quedar atados a un único buscador.
        if (hits.size < MIN_RESULTS) {
            searchDuckDuckGo(cleanQuery).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        }

        if (hits.isEmpty()) {
            return@withContext NikoAiReply(
                "Tengo conexión, pero los buscadores públicos no me devolvieron resultados utilizables. Probá diciéndome el tema con un poco más de detalle.",
                false,
                emptyList(),
            )
        }

        val enriched = hits.values.take(MAX_RESULTS).mapIndexed { index, original ->
            val article = fetchPage(original.url)
            val finalUrl = article?.finalUrl?.takeIf(::safePublicUrl) ?: original.url
            original.copy(
                url = finalUrl,
                rank = index,
                articleText = article?.body?.let(::extractReadableText).orEmpty(),
            )
        }.distinctBy(::hitKey)

        val summary = summarize(cleanQuery, enriched, current)
        val sources = enriched
            .filter { safePublicUrl(it.url) }
            .distinctBy { publisherKey(it) }
            .take(MAX_SOURCES)
            .map { NikoWebSource(it.title.ifBlank { hostOf(it.url) }, it.url) }

        if (summary.isBlank()) {
            NikoAiReply(
                "Encontré resultados en Internet, pero no pude extraer suficiente texto confiable para resumirlos. Puedo abrirte las fuentes si querés.",
                sources.isNotEmpty(),
                sources,
            )
        } else {
            val diversity = enriched.map(::publisherKey).filter(String::isNotBlank).distinct().size
            NikoAiReply(
                text = summary,
                webUsed = sources.isNotEmpty(),
                sources = sources,
                evidence = when {
                    diversity >= 3 -> "Resumen local contrastado entre $diversity fuentes."
                    diversity == 2 -> "Resumen local contrastado entre 2 fuentes."
                    else -> "Resumen local basado en la fuente disponible."
                },
            )
        }
    }

    private fun searchBing(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://www.bing.com/search?format=rss&setlang=es&q=$encoded"
        val xml = fetchText(url, "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5", RSS_LIMIT)?.body ?: return emptyList()
        return parseRss(xml, "Bing")
    }

    private fun searchGoogleNews(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://news.google.com/rss/search?q=$encoded&hl=es-419&gl=NI&ceid=NI:es-419"
        val xml = fetchText(url, "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5", RSS_LIMIT)?.body ?: return emptyList()
        return parseRss(xml, "Google News")
    }

    private fun searchDuckDuckGo(query: String): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetchText(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", SEARCH_HTML_LIMIT)?.body ?: return emptyList()
        val anchors = DDG_RESULT.findAll(html).take(MAX_RESULTS).toList()
        val snippets = DDG_SNIPPET.findAll(html).take(MAX_RESULTS).map { htmlToText(it.groupValues[1]) }.toList()
        return anchors.mapIndexedNotNull { index, match ->
            val href = decodeSearchUrl(match.groupValues[1]) ?: return@mapIndexedNotNull null
            if (!safePublicUrl(href)) return@mapIndexedNotNull null
            Hit(
                title = htmlToText(match.groupValues[2]).take(220),
                url = href,
                snippet = snippets.getOrNull(index).orEmpty().take(700),
                publisher = hostOf(href),
                rank = index,
            )
        }
    }

    internal fun parseRss(xml: String, fallbackPublisher: String = ""): List<Hit> {
        if (xml.isBlank()) return emptyList()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isXIncludeAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val items = document.getElementsByTagName("item")
            buildList {
                for (i in 0 until minOf(items.length, MAX_RESULTS)) {
                    val item = items.item(i) as? Element ?: continue
                    val title = childText(item, "title").let(::htmlToText).take(220)
                    val link = childText(item, "link").trim()
                    if (!safePublicUrl(link)) continue
                    val publisher = childText(item, "source").let(::htmlToText).ifBlank { hostOf(link).ifBlank { fallbackPublisher } }
                    val snippet = childText(item, "description").let(::htmlToText).take(900)
                    add(
                        Hit(
                            title = title,
                            url = link,
                            snippet = snippet,
                            publisher = publisher,
                            published = childText(item, "pubDate").take(100),
                            rank = i,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun childText(element: Element, tag: String): String {
        val nodes = element.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0)?.textContent.orEmpty() else ""
    }

    private fun fetchPage(url: String): Fetch? = fetchText(
        url,
        "text/html,application/xhtml+xml,application/xml;q=0.8,text/plain;q=0.7,*/*;q=0.2",
        PAGE_LIMIT,
    )

    private fun fetchText(url: String, accept: String, limit: Int): Fetch? {
        if (!safePublicUrl(url)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Accept-Language", "es-NI,es;q=0.9,en;q=0.6")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code !in 200..299) return null
            val finalUrl = connection.url.toString()
            if (!safePublicUrl(finalUrl)) return null
            val bytes = readLimited(connection.inputStream, limit)
            val charset = charsetFrom(connection.contentType)
            Fetch(String(bytes, charset), finalUrl)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimited(stream: java.io.InputStream, limit: Int): ByteArray = stream.use { input ->
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (count <= 0) break
            output.write(buffer, 0, count)
            total += count
        }
        output.toByteArray()
    }

    private fun charsetFrom(contentType: String?): Charset {
        val raw = contentType?.let { Regex("(?i)charset=([^;\\s]+)").find(it)?.groupValues?.getOrNull(1) }
            ?.trim(' ', '"', '\'')
        return raw?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    internal fun extractReadableText(html: String): String {
        if (html.isBlank()) return ""
        val cleaned = html
            .replace(SCRIPT_STYLE, " ")
            .replace(COMMENTS, " ")
        val metaDescriptions = META_TAG.findAll(cleaned).mapNotNull { tag ->
            val attrs = attributes(tag.value)
            val key = (attrs["name"] ?: attrs["property"]).orEmpty().lowercase(Locale.ROOT)
            if (key in DESCRIPTION_KEYS) attrs["content"]?.let(::htmlToText) else null
        }.filter { it.length >= 40 }.take(2).toList()
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
        val queryTerms = meaningfulTokens(query)
        val candidates = mutableListOf<SentenceCandidate>()
        hits.forEachIndexed { sourceIndex, hit ->
            val raw = listOf(hit.snippet, hit.articleText).filter(String::isNotBlank).joinToString(" ")
            splitSentences(raw).take(MAX_SENTENCES_PER_SOURCE).forEach { sentence ->
                val tokens = meaningfulTokens(sentence)
                if (tokens.isEmpty()) return@forEach
                val overlap = tokens.count(queryTerms::contains)
                val numbers = if (sentence.any(Char::isDigit)) 0.7 else 0.0
                val rankBoost = (MAX_RESULTS - hit.rank).coerceAtLeast(0) * 0.18
                val publisherBoost = if (hit.publisher.isNotBlank()) 0.25 else 0.0
                val score = overlap * 3.2 + numbers + rankBoost + publisherBoost + minOf(tokens.size, 35) * 0.025
                if (score >= 0.6) candidates += SentenceCandidate(sentence, sourceIndex, score)
            }
        }

        val selected = mutableListOf<SentenceCandidate>()
        candidates.sortedByDescending { it.score }.forEach { candidate ->
            if (selected.size >= SUMMARY_SENTENCES) return@forEach
            if (selected.any { similar(it.text, candidate.text) }) return@forEach
            selected += candidate
        }
        if (selected.isEmpty()) {
            hits.firstOrNull()?.snippet?.takeIf { it.length >= 40 }?.let {
                selected += SentenceCandidate(it, 0, 1.0)
            }
        }

        // Para noticias intentamos que al menos dos medios aparezcan si existen.
        if (current && hits.size >= 2 && selected.map { it.sourceIndex }.distinct().size < 2) {
            val second = candidates
                .filter { it.sourceIndex != selected.firstOrNull()?.sourceIndex }
                .maxByOrNull { it.score }
            if (second != null && selected.none { similar(it.text, second.text) }) selected += second
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
            publishers >= 3 -> " Lo contrasté entre $publishers fuentes distintas."
            publishers == 2 -> " Lo contrasté entre dos fuentes distintas."
            else -> ""
        }
        return (lead + body + tail).take(SUMMARY_LIMIT + 160)
    }

    private fun splitSentences(text: String): List<String> = text
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[.!?])\\s+"))
        .map(String::trim)
        .filter { it.length in 35..420 && !looksLikeBoilerplate(it) }

    private fun meaningfulTokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .asSequence()
        .map(String::trim)
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun similar(a: String, b: String): Boolean {
        val left = meaningfulTokens(a)
        val right = meaningfulTokens(b)
        if (left.isEmpty() || right.isEmpty()) return false
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return union > 0 && intersection / union >= 0.72
    }

    private fun attributes(tag: String): Map<String, String> = ATTR.findAll(tag).associate { match ->
        match.groupValues[1].lowercase(Locale.ROOT) to decodeEntities(match.groupValues[3])
    }

    private fun htmlToText(value: String): String = decodeEntities(
        value
            .replace(Regex("(?i)<br\\s*/?>"), ". ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(),
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
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http")) return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host == "0.0.0.0" || host == "127.0.0.1" || host == "::1") return false
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

    private val DDG_RESULT = Regex(
        "<a[^>]+class=[\\\"'][^\\\"']*result__a[^\\\"']*[\\\"'][^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val DDG_SNIPPET = Regex(
        "<(?:a|div)[^>]+class=[\\\"'][^\\\"']*result__snippet[^\\\"']*[\\\"'][^>]*>(.*?)</(?:a|div)>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SCRIPT_STYLE = Regex("<(script|style|noscript|svg)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val P_TAG = Regex("<p\\b[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\\\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
    private val NUMERIC_ENTITY = Regex("&#(x?[0-9a-fA-F]+);")
    private val DESCRIPTION_KEYS = setOf("description", "og:description", "twitter:description")
    private val BOILERPLATE = listOf("aceptar cookies", "politica de privacidad", "suscribete", "inicia sesion", "todos los derechos reservados", "javascript", "newsletter")
    private val STOP_WORDS = setOf(
        "que", "como", "para", "por", "con", "una", "uno", "unos", "unas", "del", "las", "los", "este", "esta", "esto", "ese", "esa",
        "desde", "hasta", "sobre", "entre", "sin", "hay", "fue", "son", "ser", "sea", "han", "mas", "muy", "pero", "sus", "segun", "tambien",
        "the", "and", "for", "with", "from", "this", "that", "are", "was", "were", "has", "have",
    )

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36 LEO/0.9"
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 8_000
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
