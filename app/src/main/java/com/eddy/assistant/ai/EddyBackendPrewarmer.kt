package com.eddy.assistant.ai

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Despierta el backend gratuito únicamente DESPUÉS de detectar la palabra EDDY.
 * No transmite audio ni texto de la conversación; solo hace GET /health.
 * Esto respeta el modo privado pasivo y aprovecha los segundos en los que el usuario
 * termina de decir su orden para comenzar el cold start de Render.
 */
object EddyBackendPrewarmer {
    private val warming = AtomicBoolean(false)

    @Volatile private var lastReadyAt = 0L

    fun wake(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastReadyAt < READY_CACHE_MS) return
        if (!warming.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        thread(name = "EDDY-Backend-Warmup", isDaemon = true) {
            try {
                val base = EddyAiSettings.baseUrl(appContext).trim().trimEnd('/')
                if (base.isBlank()) return@thread
                val deadline = System.currentTimeMillis() + MAX_WARMUP_MS
                var waitMs = 500L
                while (System.currentTimeMillis() < deadline) {
                    if (ping("$base/health")) {
                        lastReadyAt = System.currentTimeMillis()
                        return@thread
                    }
                    Thread.sleep(waitMs)
                    waitMs = (waitMs * 2).coerceAtMost(4_000L)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                warming.set(false)
            }
        }
    }

    private fun ping(endpoint: String): Boolean {
        val connection = runCatching { URL(endpoint).openConnection() as HttpURLConnection }.getOrNull()
            ?: return false
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 12_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "keep-alive")
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private const val READY_CACHE_MS = 8 * 60_000L
    private const val MAX_WARMUP_MS = 70_000L
}
