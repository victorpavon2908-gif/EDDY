path = 'C:/Users/MARIO LUNA/Desktop/EDDY/app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

target = """    internal suspend fun searchDuckDuckGo(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetch(url, HTML_ACCEPT, SEARCH_HTML_LIMIT)?.body ?: return emptyList()
        return parseDuckDuckGo(html)
    }"""

repl = """    internal suspend fun searchDuckDuckGo(query: String, fetch: suspend (String, String, Int) -> Fetch?): List<Hit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetch(url, HTML_ACCEPT, SEARCH_HTML_LIMIT)?.body
        val hits = if (!html.isNullOrBlank()) parseDuckDuckGo(html) else emptyList()
        if (hits.isNotEmpty()) return hits
        return searchDuckDuckGoApi(query, fetch)
    }"""

assert target in text
text = text.replace(target, repl)
with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Updated searchDuckDuckGo')
