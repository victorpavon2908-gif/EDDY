package com.eddy.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Decide cuánto puede cargar EDDY sin poner inestable el teléfono. */
data class EddyDeviceProfile(
    val tier: Tier,
    val totalRamMb: Long,
    val cpuCores: Int,
    val inferenceThreads: Int,
    val abi: String,
) {
    enum class Tier { LITE, BALANCED, POWER }

    // EDDY ahora prioriza APIs + memoria local ligera. El LLM local pesado queda
    // desactivado por defecto incluso en gama media/alta para evitar descargas de
    // cientos de MB, presión de RAM y calentamiento. Puede reactivarse más adelante
    // como opción avanzada, pero ya no forma parte del arranque normal.
    val supportsLocalLlm: Boolean get() = false
    val prefersGpuLlm: Boolean get() = false

    companion object {
        fun detect(context: Context): EddyDeviceProfile {
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

            return EddyDeviceProfile(
                tier = tier,
                totalRamMb = ramMb,
                cpuCores = cores,
                inferenceThreads = threads,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        }
    }
}
