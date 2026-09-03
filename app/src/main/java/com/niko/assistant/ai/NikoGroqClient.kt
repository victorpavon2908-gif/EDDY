package com.niko.assistant.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Direct GroqCloud conversation. Voice, memory and device actions remain local. */
class NikoGroqClient(context: Context) {
    private val appContext = context.applicationContext
    private val gateway = GroqGateway(transport = GroqHttpClient())
    private var localError: String? = null
    val isConfigured get() = NikoAiSettings.apiKey(appContext).isNotBlank()
    val lastError get() = localError ?: gateway.lastError
    val lastModelUsed get() = gateway.lastModelUsed

    suspend fun testConnection(): Boolean = reply("Respondé únicamente con OK.", "") != null

    internal suspend fun synthesizeResearch(question: String, evidence: NikoAiReply): NikoAiReply? {
        if (!isConfigured || !evidence.webUsed || evidence.sources.isEmpty()) return null
        val configured = NikoAiSettings.model(appContext)
        val model = configured.takeIf(GroqProtocol::isChatModel) ?: GroqProtocol.DEFAULT_MODEL
        return withTimeoutOrNull(6_000L) {
            val payload = GroqConversation.forModel(ResearchSynthesis.payload(question, evidence), model, false)
            val response = GroqHttpClient().complete(NikoAiSettings.apiKey(appContext), payload)
            if (response.code !in 200..299) return@withTimeoutOrNull null
            val answer = runCatching { GroqProtocol.answer(JSONObject(response.body)) }.getOrNull()
            answer?.let { ResearchSynthesis.apply(it.text, evidence) }
        }
    }

    suspend fun reply(message: String, memoryContext: String, useWeb: Boolean = false, history: List<ConversationTurn> = emptyList()): NikoAiReply? {
        localError = null
        if (message.isBlank()) { localError = "El mensaje está vacío."; return null }
        if (AutonomousResearch.offlineOnly(message)) { localError = "Este pedido se mantiene sin Internet."; return null }
        if (!isConfigured) { localError = "Falta la API key de GroqCloud. Guardala en Ajustes."; return null }
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            localError = "Sin conexión a Internet. Las funciones locales siguen disponibles."; return null
        }
        return gateway.execute(
            GroqConversation.payload(message, memoryContext, history, useWeb, NikoAiSettings.personality(appContext)),
            NikoAiSettings.apiKey(appContext), NikoAiSettings.model(appContext), useWeb,
        )
    }
}
