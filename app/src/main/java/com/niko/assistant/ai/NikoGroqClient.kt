package com.niko.assistant.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Direct GroqCloud conversation. Voice, memory and device actions remain local. */
class NikoGroqClient(context: Context) {
    private val appContext = context.applicationContext
    private val gateway = GroqGateway(transport = GroqHttpClient())
    private var localError: String? = null
    val isConfigured get() = NikoAiSettings.apiKey(appContext).isNotBlank()
    val lastError get() = localError ?: gateway.lastError
    val lastModelUsed get() = gateway.lastModelUsed

    suspend fun testConnection(): Boolean = reply("Respondé únicamente con OK.", "") != null

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
