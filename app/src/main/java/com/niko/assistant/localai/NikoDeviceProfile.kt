package com.niko.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Decide cuánto puede cargar LEO sin poner inestable el teléfono. */
data class NikoDeviceProfile(
    val tier: Tier,
    val totalRamMb: Long,
    val cpuCores: Int,
    val inferenceThreads: Int,
    val abi: String,
    val manufacturer: String = "",
    val model: String = "",
) {
    enum class Tier { LITE, BALANCED, POWER }

    /**
     * Compatibilidad universal Android 12+.
     *
     * Un SIGSEGV dentro de un runtime JNI no se puede capturar con try/catch y no debe
     * poder matar el proceso principal de LEO. Por eso el núcleo estable no habilita
     * inferencia LLM nativa dentro del mismo proceso en ningún fabricante. No usamos
     * listas negras por marca: Samsung, HONOR, Xiaomi, Motorola, OPPO, vivo, Pixel, etc.
     * siguen exactamente la misma regla.
     *
     * La conversación generativa local sólo se volverá a habilitar cuando ese runtime
     * viva en un proceso aislado que pueda morir/reiniciarse sin tumbar voz, wake word,
     * acciones, memoria ni búsqueda web.
     */
    val localLlmRuntimeSafe: Boolean get() = false

    /** El núcleo universal nunca depende del LLM JNI para funcionar. */
    val supportsLocalLlm: Boolean get() = false

    val prefersQualityLocalLlm: Boolean get() = false

    // Se conserva para compatibilidad de API; el runtime LLM no se crea en el proceso principal.
    val prefersGpuLlm: Boolean get() = false

    companion object {
        fun detect(context: Context): NikoDeviceProfile {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val ramMb = (memoryInfo.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val lowRamDevice = activityManager?.isLowRamDevice == true

            val tier = when {
                lowRamDevice -> Tier.LITE
                ramMb >= 7_000L && cores >= 8 -> Tier.POWER
                ramMb >= 4_500L && cores >= 6 -> Tier.BALANCED
                else -> Tier.LITE
            }

            val threads = when (tier) {
                Tier.LITE -> 1
                Tier.BALANCED -> 2
                Tier.POWER -> (cores - 2).coerceIn(2, 4)
            }

            return NikoDeviceProfile(
                tier = tier,
                totalRamMb = ramMb,
                cpuCores = cores,
                inferenceThreads = threads,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
            )
        }
    }
}
