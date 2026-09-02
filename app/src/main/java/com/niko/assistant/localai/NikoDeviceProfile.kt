package com.niko.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Decide cuánto puede cargar NIKO sin poner inestable el teléfono. */
data class NikoDeviceProfile(
    val tier: Tier,
    val totalRamMb: Long,
    val cpuCores: Int,
    val inferenceThreads: Int,
    val abi: String,
) {
    enum class Tier { LITE, BALANCED, POWER }

    // La conversación local es opcional y se descarga desde Ajustes. No forma parte
    // del arranque de voz, así que habilitarla aquí no obliga a descargar 1+ GB.
    val supportsLocalLlm: Boolean get() = totalRamMb >= 4_500L && cpuCores >= 6

    // El 1.5B INT8 necesita bastante más memoria que el 0.5B. A partir de ~5.5 GB
    // y ocho núcleos preferimos calidad; equipos más modestos conservan el modelo rápido.
    val prefersQualityLocalLlm: Boolean get() = totalRamMb >= 5_500L && cpuCores >= 8

    // CPU/XNNPACK es la ruta más predecible entre fabricantes para estos .task.
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
            )
        }
    }
}
