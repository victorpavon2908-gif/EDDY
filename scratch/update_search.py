import re

path = 'C:/Users/MARIO LUNA/Desktop/EDDY/app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update failureMessage
old_failure = '''    internal fun failureMessage(events: List<FetchDiagnostic>): String? = when {
        events.any { it.status == 429 } -> "Un buscador limitó las consultas y no obtuve evidencia suficiente. Probá más tarde o pasame un enlace público."
        events.any { it.status == 403 } -> "Un sitio rechazó el acceso y no obtuve evidencia suficiente. Podés pasarme otra fuente pública; no voy a saltarme ese bloqueo."
        events.any { it.failure == "SocketTimeoutException" } -> "La consulta a Internet agotó el tiempo sin reunir evidencia suficiente. Podés reintentar o pasarme un enlace público."
        events.any { it.failure == "UnknownHostException" || it.failure == "ConnectException" } -> "No pude conectar con las fuentes. Revisá la conexión o probá con un enlace público cuando vuelva Internet."
        else -> null
    }'''

new_failure = '''    internal fun failureMessage(events: List<FetchDiagnostic>): String? {
        if (events.isEmpty()) return null
        if (events.any { it.status in 200..299 }) return null
        return when {
            events.all { it.status == 429 } -> "Un buscador limitó las consultas y no obtuve evidencia suficiente. Probá más tarde o pasame un enlace público."
            events.all { it.status == 403 } -> "Un sitio rechazó el acceso y no obtuve evidencia suficiente. Podés pasarme otra fuente pública; no voy a saltarme ese bloqueo."
            events.any { it.failure == "SocketTimeoutException" } -> "La consulta a Internet agotó el tiempo sin reunir evidencia suficiente. Podés reintentar o pasarme un enlace público."
            events.any { it.failure == "UnknownHostException" || it.failure == "ConnectException" } -> "No pude conectar con las fuentes. Revisá la conexión o probá con un enlace público cuando vuelva Internet."
            else -> null
        }
    }'''

# Replace failureMessage regardless of encoding
pattern_failure = re.compile(r'internal fun failureMessage\(events: List<FetchDiagnostic>\): String\? = when \{[\s\S]*?else -> null\s*\}')
content, n = pattern_failure.subn(new_failure, content)
print(f'Replaced failureMessage: {n}')

# 2. Update discovered and enriched
old_discovery_regex = re.compile(r'// 3\. Descubrimiento federado soberano[\s\S]*?val finalText = if \(readCount == 0 && cited\.all \{ it\.snippet\.isBlank\(\) \} \) \{[\s\S]*?\} else summary\.ifBlank \{ [^\n]+\n[^\n]+\}')
new_discovery = '''// 3. Descubrimiento federado soberano (Wikipedia + Google News + DDG Lite/API + Bing)
        val discovered = coroutineScope {
            val base = listOf(
                async { searchWikipedia(researchQueries.first(), fetch).map { it.copy(angle = 0) } },
                async { searchGoogleNews(researchQueries.first(), fetch).map { it.copy(angle = 0) } },
                async { searchDuckDuckGoLite(researchQueries.first(), fetch).map { it.copy(angle = 0) } },
                async { searchBing(researchQueries.first(), fetch).map { it.copy(angle = 0) } },
            )
            val facets = researchQueries.drop(1).mapIndexed { index, planned ->
                async {
                    val results = when {
                        current && index == 0 -> searchGoogleNews(planned, fetch)
                        index % 2 == 0 -> searchWikipedia(planned, fetch)
                        else -> searchDuckDuckGo(planned, fetch)
                    }
                    results.map { it.copy(angle = index + 1) }
                }
            }
            (base + facets).awaitAll().flatten()
        }
        discovered.forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }

        if (current && hits.values.count { isRelevantTo(subject, it) } < MIN_RESULTS) {
            searchGoogleNews(subject, fetch).forEach { hit -> hits.putIfAbsent(hitKey(hit), hit) }
        }
        val relevant = selectDiverse(hits.values.filter { isRelevantTo(subject, it) }, terms)
        if (relevant.isEmpty()) return@withContext noResults(subject)

        val enriched = coroutineScope {
            relevant.mapIndexed { index, original -> async {
                if (original.articleText.length >= 120) {
                    return@async original.copy(rank = index)
                }
                val page = fetch(original.url, PAGE_ACCEPT, PAGE_LIMIT)
                if (page != null && (isBlockedPage(page) || !safePublicUrl(page.finalUrl))) {
                    return@async if (original.snippet.isNotBlank()) original.copy(rank = index) else null
                }
                val articleText = page?.body?.let(::extractReadableText).orEmpty()
                val usefulArticle = articleText.takeIf {
                    isRelevantTo(subject, original.copy(title = "", snippet = it))
                }.orEmpty()
                original.copy(
                    url = page?.finalUrl ?: original.url,
                    publisher = hostOf(page?.finalUrl ?: original.url).ifBlank { original.publisher },
                    rank = index,
                    articleText = usefulArticle.ifBlank { original.articleText },
                    steps = if (usefulArticle.isNotBlank()) extractSteps(page?.body.orEmpty()) else original.steps,
                    code = if (usefulArticle.isNotBlank()) extractCode(page?.body.orEmpty()) else original.code,
                )
            } }.awaitAll().filterNotNull().distinctBy(::hitKey)
        }
        if (enriched.isEmpty()) return@withContext noResults(subject)
        val cited = enriched.sortedByDescending { it.articleText.isNotBlank() }.distinctBy { it.url }.take(MAX_SOURCES)
        val summary = practicalAnswer(cleanQuery, cited) ?: summarize(subject, cited, current, wantsDepth(cleanQuery))
        val sources = cited
            .mapIndexed { index, hit -> NikoWebSource("[${index + 1}] ${hit.title.ifBlank { hostOf(hit.url) }}", hit.url) }
        val readCount = cited.count { it.articleText.isNotBlank() }
        val finalText = if (readCount == 0 && cited.all { it.snippet.isBlank() }) {
            "Encontré enlaces sobre $subject, pero no pude extraer texto de las páginas ni extractos del buscador. Abrí las fuentes o reintentá la búsqueda."
        } else if (readCount == 0) {
            buildString {
                append("Encontré fuentes relacionadas con $subject, pero no pude abrir los artículos completos. ")
                append("Estos son extractos públicos del buscador, no información comprobada:\\n\\n")
                append(summary.ifBlank {
                    cited.take(3).joinToString("\\n") { "• ${it.title}: ${it.snippet.take(220)}" }
                })
                append("\\n\\nAbrí las fuentes para verificar los detalles o volvé a intentar la búsqueda.")
            }
        } else summary.ifBlank {
            cited.mapNotNull { it.articleText.takeIf(String::isNotBlank)?.take(350) ?: it.snippet.takeIf(String::isNotBlank)?.take(220) }
                .joinToString(" ").ifBlank { "Encontré enlaces sobre $subject, pero no pude extraer suficiente texto para resumirlos." }
        }'''

content, n2 = old_discovery_regex.subn(new_discovery, content)
print(f'Replaced discovery & enriched: {n2}')

# 3. Update searchWikipedia and searchDuckDuckGoLite
search_wiki_regex = re.compile(r'internal suspend fun searchWikipedia\(query: String, fetch: suspend \(String, String, Int\) -> Fetch\?\): List<Hit> \{[\s\S]*?val jsonStr = fetch\(url, "application/json", 150_000\)\?\.body \?: return emptyList\(\)\s*return parseWikipedia\(jsonStr\)\s*\}')
new_search_wiki = '''internal suspend fun searchWikipedia(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://es.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=3&prop=extracts|info&inprop=url&exintro=1&explaintext=1&exsentences=4&format=json"
        val jsonStr = fetch(url, "application/json", 150_000)?.body
        val generatorHits = if (!jsonStr.isNullOrBlank()) parseWikipedia(jsonStr) else emptyList()
        if (generatorHits.isNotEmpty()) return generatorHits

        val openSearchUrl = "https://es.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=3&namespace=0&format=json"
        val openSearchJson = fetch(openSearchUrl, "application/json", 80_000)?.body
        return if (!openSearchJson.isNullOrBlank()) parseWikipediaOpenSearch(openSearchJson, fetch) else emptyList()
    }

    internal suspend fun parseWikipediaOpenSearch(jsonStr: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> = runCatching {
        if (jsonStr.isBlank()) return emptyList()
        val array = JSONArray(jsonStr)
        if (array.length() < 4) return emptyList()
        val titles = array.optJSONArray(1) ?: return emptyList()
        val urls = array.optJSONArray(3) ?: return emptyList()
        val hits = mutableListOf<Hit>()
        for (i in 0 until minOf(titles.length(), 3)) {
            val title = titles.optString(i, "")
            val url = urls.optString(i, "")
            if (title.isBlank() || url.isBlank()) continue
            val summaryUrl = "https://es.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"
            val summaryJson = fetch(summaryUrl, "application/json", 100_000)?.body
            val extract = if (!summaryJson.isNullOrBlank()) {
                JSONObject(summaryJson).optString("extract", "").let(::htmlToText).trim()
            } else ""
            if (extract.length >= 20) {
                hits += Hit(
                    title = "$title - Wikipedia",
                    url = url,
                    snippet = extract.take(900),
                    publisher = "Wikipedia",
                    rank = i,
                    articleText = extract.take(MAX_ARTICLE_TEXT),
                )
            }
        }
        hits
    }.getOrDefault(emptyList())'''

content, n3 = search_wiki_regex.subn(new_search_wiki, content)
print(f'Replaced searchWikipedia: {n3}')

# 4. Update searchDuckDuckGoLite to add DDG Instant Answer fallback
ddg_lite_regex = re.compile(r'internal suspend fun searchDuckDuckGoLite\(query: String, fetch: suspend \(String, String, Int\) -> Fetch\?\): List<Hit> \{[\s\S]*?val html = fetch\(url, HTML_ACCEPT, SEARCH_HTML_LIMIT\)\?\.body \?: return emptyList\(\)\s*return parseDuckDuckGoLite\(html\)\s*\}')
new_ddg_lite = '''internal suspend fun searchDuckDuckGoLite(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://lite.duckduckgo.com/lite/?q=$encoded"
        val html = fetch(url, HTML_ACCEPT, SEARCH_HTML_LIMIT)?.body
        val liteHits = if (!html.isNullOrBlank()) parseDuckDuckGoLite(html) else emptyList()
        if (liteHits.isNotEmpty()) return liteHits

        return searchDuckDuckGoApi(query, fetch)
    }

    internal suspend fun searchDuckDuckGoApi(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        val jsonStr = fetch(url, "application/json", 150_000)?.body ?: return emptyList()
        return parseDuckDuckGoApi(jsonStr)
    }

    internal fun parseDuckDuckGoApi(jsonStr: String): List<Hit> = runCatching {
        if (jsonStr.isBlank()) return emptyList()
        val root = JSONObject(jsonStr)
        val hits = mutableListOf<Hit>()
        val abstractText = root.optString("AbstractText", "").trim()
        val heading = root.optString("Heading", "").trim()
        val abstractUrl = root.optString("AbstractURL", "").trim()
        val source = root.optString("AbstractSource", "DuckDuckGo")
        if (abstractText.length >= 20 && abstractUrl.isNotBlank() && safePublicUrl(abstractUrl)) {
            hits += Hit(
                title = if (heading.isNotBlank()) "$heading - $source" else "DuckDuckGo: $heading",
                url = abstractUrl,
                snippet = abstractText.take(900),
                publisher = source,
                rank = 0,
                articleText = abstractText.take(MAX_ARTICLE_TEXT),
            )
        }
        val related = root.optJSONArray("RelatedTopics")
        if (related != null) {
            for (i in 0 until minOf(related.length(), 4)) {
                val item = related.optJSONObject(i) ?: continue
                val text = item.optString("Text", "").trim()
                val firstUrl = item.optString("FirstURL", "").trim()
                if (text.length >= 20 && safePublicUrl(firstUrl)) {
                    hits += Hit(
                        title = text.take(60),
                        url = firstUrl,
                        snippet = text.take(900),
                        publisher = hostOf(firstUrl).ifBlank { "DuckDuckGo" },
                        rank = hits.size,
                    )
                }
            }
        }
        hits
    }.getOrDefault(emptyList())'''

content, n4 = ddg_lite_regex.subn(new_ddg_lite, content)
print(f'Replaced searchDuckDuckGoLite: {n4}')

# 5. Update searchGoogleNews to use US:es-419
content = content.replace("gl=NI&ceid=NI:es-419", "gl=US&ceid=US:es-419")

# 6. Update relevanceScore
relevance_regex = re.compile(r'if \(bodyOverlap == 0 \|\| \(terms\.size > 1 && \(bodyOverlap < 2 \|\| coverage < 0\.5\)\)\) return 0\.0')
new_relevance = 'if (bodyOverlap == 0 || (terms.size == 2 && bodyOverlap < 2) || (terms.size > 2 && bodyOverlap < 2 && titleOverlap == 0)) return 0.0'
content, n6 = relevance_regex.subn(new_relevance, content)
print(f'Replaced relevanceScore threshold: {n6}')

# 7. Update QUERY_FILLERS and timeouts
content = content.replace('"busca", "buscame", "internet",', '"busca", "buscame", "internet",\n        "quien", "quienes", "cual", "cuales", "donde", "cuando", "fue", "es", "era", "ser",')
content = content.replace('private const val CONNECT_TIMEOUT_MS = 4_000', 'private const val CONNECT_TIMEOUT_MS = 6_000')
content = content.replace('private const val READ_TIMEOUT_MS = 4_000', 'private const val READ_TIMEOUT_MS = 6_000')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Successfully saved LeoNativeWebSearch.kt')
