path = 'C:/Users/MARIO LUNA/Desktop/EDDY/app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Faster transport timeout
text = text.replace('withTimeoutOrNull(8_000L)', 'withTimeoutOrNull(4_000L)')

# 2. Faster page enrichment
text = text.replace('relevant.mapIndexed { index, original -> async {', 'relevant.take(4).mapIndexed { index, original -> async {')
text = text.replace('val page = fetch(original.url, PAGE_ACCEPT, PAGE_LIMIT)', 'val page = try { withTimeoutOrNull(3_000L) { fetch(original.url, PAGE_ACCEPT, PAGE_LIMIT) } } catch (_: Exception) { null }')

# 3. Faster budget and socket timeouts
text = text.replace('private const val SEARCH_BUDGET_MS = 28_000L', 'private const val SEARCH_BUDGET_MS = 14_000L')
text = text.replace('private const val CONNECT_TIMEOUT_MS = 6_000', 'private const val CONNECT_TIMEOUT_MS = 3_500')
text = text.replace('private const val READ_TIMEOUT_MS = 6_000', 'private const val READ_TIMEOUT_MS = 3_500')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Optimized timeouts in LeoNativeWebSearch.kt')
