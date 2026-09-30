package com.niko.assistant.media

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

data class MusicResult(val title: String, val artist: String, val preview: String, val link: String)

object MusicSearch {
    // Two workers and a bounded queue: cancelled searches cannot accumulate without limit.
    private val executor = ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(8), { task -> Thread(task, "leo-music-search").apply { isDaemon = true } })

    suspend fun search(query: String): List<MusicResult> {
        val term = URLEncoder.encode(query.trim().take(200), "UTF-8")
        val connection = URL("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=12").openConnection() as HttpURLConnection
        return fetch(connection)
    }

    internal suspend fun fetch(connection: HttpURLConnection): List<MusicResult> = suspendCancellableCoroutine { continuation ->
        try {
            val future = executor.submit {
                try {
                    connection.connectTimeout = 6000; connection.readTimeout = 8000
                    check(continuation.isActive)
                    check(connection.responseCode in 200..299)
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            check(continuation.isActive)
                            val count = reader.read(buffer)
                            if (count < 0) break
                            check(result.length + count <= 1_000_000)
                            result.append(buffer, 0, count)
                        }
                        result.toString()
                    }
                    val result = parse(body)
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally { connection.disconnect() }
            }
            continuation.invokeOnCancellation {
                future.cancel(true)
                executor.remove(future as Runnable)
                connection.disconnect()
            }
        } catch (error: Exception) {
            connection.disconnect()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    internal fun trustedUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null &&
            (host == "apple.com" || host.endsWith(".apple.com") || host.endsWith(".mzstatic.com"))
    }.getOrDefault(false)

    internal fun parse(body: String): List<MusicResult> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(results.length(), 12)) {
                val item = results.optJSONObject(index) ?: continue
                val preview = item.optString("previewUrl")
                val link = item.optString("trackViewUrl")
                if (!trustedUrl(preview) || !trustedUrl(link)) continue
                add(MusicResult(item.optString("trackName").take(150), item.optString("artistName").take(100), preview, link))
            }
        }
    }
}
