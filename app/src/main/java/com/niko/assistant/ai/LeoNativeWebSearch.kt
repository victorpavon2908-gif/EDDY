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
import kotlinx.coroutines.runInterruptible
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
        val angle: Int = 0,
    )

    internal data class Fetch(val body: String, val finalUrl: String)
    private data class SentenceCandidate(val text: String, val sourceIndex: Int, val score: Double)

    suspend fun search(query: String): NikoAiReply {
        val deadline = System.nanoTime() + SEARCH_BUDGET_MS * 1_000_000L
        return search(query) { url, accept, limit -> runInterruptible(Dispatchers.IO) { fetchText(url, accept, limit, deadline) } }
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
        val researchQueries = researchQueries(subject, cleanQuery, current)
        val hits = linkedMapOf<String, Hit>()
        val discovered = coroutineScope {
            val base = listOf(
                async { searchBing(researchQueries.first(), fetch).map { it.copy(angle = 0) } },
                async {
                    val results = if (current) searchGoogleNews(researchQueries.first(), fetch) else searchDuckDuckGo(researchQueries.first(), fetch)
                    results.map { it.copy(angle = 0) }
                },
            )
            val facets = researchQueries.drop(1).mapIndexed { index, planned ->
                async {
                    val results = when {
                        current && index == 0 -> searchGoogleNews(planned, fetch)
                        index % 2 == 0 -> searchBing(planned, fetch)
                        else -> searchDuckDuckGo(planned, fetch)
                    }
                    results.map { it.copy(angle = index + 1) }
                }
            }
            (base + facets).awaitAll().flatten()
        }
        discovered.forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        // Count useful hits, not login screens or unrelated RSS entries.
        if (current && hits.values.count { isRelevantTo(subject, it) } < MIN_RESULTS) {
            searchDuckDuckGo(subject, fetch).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        }
        val relevant = selectDiverse(hits.values.filter { isRelevantTo(subject, it) }, terms)
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
        val cited = enriched.distinctBy { it.url }.take(MAX_SOURCES)
        val summary = summarize(subject, cited, current)
        val sources = cited
            .mapIndexed { index, hit -> NikoWebSource("[${index + 1}] ${hit.title.ifBlank { hostOf(hit.url) }}", hit.url) }
        NikoAiReply(
            text = summary.ifBlank { "Encontré enlaces sobre $subject, pero no pude extraer suficiente texto para resumirlos." },
            webUsed = true,
            sources = sources,
            evidence = "Investigación local con ${researchQueries.size} enfoques de consulta y lectura de páginas. Tener varias fuentes no implica que sus afirmaciones estén confirmadas.",
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

    /** Plans complementary queries instead of trusting one wording or one result page. */
    internal fun researchQueries(subject: String, original: String, current: Boolean): List<String> {
        val request = normalize(original)
        val intentFacet = when {
            Regex("\\b(?:comparar|compara|comparacion|diferencia|diferencias|versus|vs)\\b").containsMatchIn(request) ->
                "$subject comparacion diferencias ventajas limitaciones"
            Regex("\\b(?:por que|causa|causas|motivo|motivos)\\b").containsMatchIn(request) ->
                "$subject causas explicacion evidencia"
            Regex("\\b(?:como|funciona|funcionamiento|pasos|proceso)\\b").containsMatchIn(request) ->
                "$subject funcionamiento proceso explicacion"
            Regex("\\b(?:recomienda|recomendame|mejor|conviene|elegir)\\b").containsMatchIn(request) ->
                "$subject criterios alternativas riesgos"
            current -> "$subject cronologia ultimas actualizaciones"
            else -> "$subject contexto explicacion detallada"
        }
        val primaryFacet = if (current) {
            "$subject comunicado datos recientes fuente oficial"
        } else {
            "$subject datos evidencia fuente primaria"
        }
        return listOf(subject, intentFacet, primaryFacet)
            .map { it.replace(Regex("\\s+"), " ").trim().take(220) }
            .filter(String::isNotBlank)
            .distinctBy(::normalize)
            .take(MAX_RESEARCH_QUERIES)
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

    private fun selectDiverse(candidates: List<Hit>, terms: Set<String>): List<Hit> {
        val remaining = candidates.toMutableList()
        val selected = mutableListOf<Hit>()
        val usedHosts = mutableSetOf<String>()
        val usedAngles = mutableSetOf<Int>()
        while (remaining.isNotEmpty() && selected.size < MAX_RESULTS) {
            val best = remaining.maxByOrNull { hit ->
                val host = hostOf(hit.url)
                relevanceScore(hit, terms) + sourceQualityScore(hit) - hit.rank * 0.04 +
                    (if (host !in usedHosts) 2.8 else -1.2) +
                    (if (hit.angle !in usedAngles) 1.2 else 0.0)
            } ?: break
            remaining.remove(best)
            selected += best
            usedHosts += hostOf(best.url)
            usedAngles += best.angle
        }
        return selected
    }

    private fun sourceQualityScore(hit: Hit): Double {
        val host = hostOf(hit.url)
        val institutional = host.endsWith(".gov") || host.contains(".gov.") || host.endsWith(".gob") ||
            host.contains(".gob.") || host.endsWith(".edu") || host.contains(".edu.")
        val titledAsPrimary = Regex("(?i)\\b(?:oficial|ministerio|universidad|instituto|organizacion|reporte|estudio)\\b")
            .containsMatchIn(hit.title)
        return (if (institutional) 1.4 else 0.0) + (if (titledAsPrimary) 0.5 else 0.0) +
            (if (hit.url.startsWith("https://")) 0.2 else 0.0)
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
            if (Thread.currentThread().isInterrupted || !safePublicUrl(next) || remainingMs(deadline) <= 0) return null
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
                            if (Thread.currentThread().isInterrupted || remainingMs(deadline) <= 0) return null
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
        val article = Regex("(?is)<(?:article|main)\\b[^>]*>(.*?)</(?:article|main)>").find(cleaned)?.groupValues?.get(1) ?: cleaned
        val paragraphs = P_TAG.findAll(article)
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
            // Read the article before the search teaser. Nearby explanatory sentences matter
            // even when they use pronouns instead of repeating the subject in every sentence.
            val raw = hit.articleText.ifBlank { hit.snippet }.ifBlank { hit.title }
            val sentences = splitSentences(raw).take(MAX_SENTENCES_PER_SOURCE)
            sentences.forEachIndexed sentenceLoop@ { index, sentence ->
                val tokens = meaningfulTokens(sentence).map(::canonicalTerm).toSet()
                val overlap = tokens.intersect(queryTerms).size
                val previousOverlap = if (index > 0) subjectTokens(sentences[index - 1]).intersect(queryTerms).size else 0
                if (overlap == 0 && (hit.articleText.isBlank() || previousOverlap == 0)) return@sentenceLoop
                val specifics = if (sentence.any(Char::isDigit)) 0.8 else 0.0
                val explanation = if (Regex("(?i)\\b(?:porque|permite|debido|significa|sin embargo|riesgo|limitaci[oó]n|ventaja)\\b").containsMatchIn(sentence)) 1.5 else 0.0
                val score = overlap * 2.0 + specifics + explanation + if (hit.articleText.isNotBlank()) 1.0 else 0.0
                candidates += SentenceCandidate(sentence, sourceIndex, score)
            }
        }
        if (candidates.isEmpty()) return ""
        val selected = mutableListOf<SentenceCandidate>()
        fun add(candidate: SentenceCandidate) {
            if (selected.size >= SUMMARY_SENTENCES || selected.any { similar(it.text, candidate.text) }) return
            val sameSource = selected.filter { it.sourceIndex == candidate.sourceIndex }
            if (sameSource.size >= 2 || (sameSource.sumOf { it.text.split(' ').size } + candidate.text.split(' ').size) > 95) return
            if (selected.sumOf { it.text.length } + candidate.text.length > SUMMARY_LIMIT) return
            selected += candidate
        }
        // A single verbose result cannot monopolize the answer.
        candidates.groupBy { it.sourceIndex }.values.forEach { group -> group.maxByOrNull { it.score }?.let(::add) }
        candidates.sortedByDescending { it.score }.forEach(::add)
        if (selected.isEmpty()) return ""
        val publishers = selected.map { publisherKey(hits[it.sourceIndex]) }.distinct().size
        val pagesRead = selected.map { it.sourceIndex }.distinct().count { hits[it].articleText.isNotBlank() }
        val anglesCovered = selected.map { hits[it.sourceIndex].angle }.distinct().size
        val overview = selected.take(2)
        val details = selected.drop(2)
        return buildString {
            appendLine(if (current) "Busqué información reciente en Internet." else "Busqué en Internet.")
            appendLine()
            appendLine("Respuesta breve sobre $query:")
            overview.groupBy { it.sourceIndex }.forEach { (index, facts) ->
                val hit = hits[index]
                appendLine("• ${facts.joinToString(" ") { it.text }} [${index + 1}]")
                if (current && hit.published.isNotBlank()) appendLine("  Fecha de la fuente [${index + 1}]: ${hit.published}")
            }
            if (details.isNotEmpty()) {
                appendLine()
                appendLine("Detalles y contexto:")
                details.groupBy { it.sourceIndex }.forEach { (index, facts) ->
                    val hit = hits[index]
                    appendLine("• ${facts.joinToString(" ") { it.text }} [${index + 1}]")
                    if (current && hit.published.isNotBlank()) appendLine("  Fecha de la fuente [${index + 1}]: ${hit.published}")
                }
            }
            appendLine()
            appendLine("Contraste y límites:")
            appendLine(when (publishers) {
                1 -> "Solo obtuve información útil de un sitio; no alcanza para corroborarla de forma independiente."
                2 -> "La cobertura reúne dos sitios independientes y $anglesCovered enfoques de consulta."
                else -> "La cobertura reúne $publishers sitios independientes y $anglesCovered enfoques de consulta."
            })
            append(if (pagesRead == 0) "Solo pude leer extractos del buscador, no los artículos completos. " else "Leí texto de $pagesRead páginas. ")
            append("Cada número indica de dónde sale el dato; si las fuentes discrepan, no debe asumirse consenso.")
            if (current && selected.none { hits[it.sourceIndex].published.isNotBlank() }) append(" Las fuentes no indican fecha de publicación; no puedo asegurar su actualidad.")
        }.trim()
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
        "enable javascript", "habilita javascript", "activa javascript", "newsletter",
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
    private const val MAX_RESEARCH_QUERIES = 3
    private const val MAX_RESULTS = 8
    private const val MIN_RESULTS = 4
    private const val MAX_SOURCES = 6
    private const val MAX_PARAGRAPHS = 32
    private const val MAX_ARTICLE_TEXT = 12_000
    private const val MAX_SENTENCES_PER_SOURCE = 40
    private const val SUMMARY_SENTENCES = 8
    private const val SUMMARY_LIMIT = 2_500
    private const val RSS_LIMIT = 700_000
    private const val SEARCH_HTML_LIMIT = 900_000
    private const val PAGE_LIMIT = 650_000
}
