path = 'C:/Users/MARIO LUNA/Desktop/EDDY/app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if '// 3. Descubrimiento federado soberano' in line:
        start_idx = i
    if 'verificar los detalles' in line:
        end_idx = i + 2 # include closing brace
        break

print(f'start: {start_idx}, end: {end_idx}')
assert start_idx != -1 and end_idx != -1

replacement = """        // 3. Descubrimiento federado soberano (Wikipedia + Google News + DDG Lite/API + Bing)
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
"""

new_lines = lines[:start_idx] + [replacement] + lines[end_idx:]
with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print('Successfully updated discovery and enriched block.')
