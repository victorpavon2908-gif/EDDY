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

    val supportsLocalLlm: Boolean get() = tier != Tier.LITE
    val prefersGpuLlm: Boolean get() = tier == Tier.POWER

    companion object {
        fun detect(context: Context): EddyDeviceProfile {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val ramMb = (memoryInfo.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val tier = when {
                ramMb >= 7_000L && cores >= 8 -> Tier.POWER
                ramMb >= 4_500L && cores >= 6 -> Tier.BALANCED
                else -> Tier.LITE
            }
            return EddyDeviceProfile(
                tier = tier,
                totalRamMb = ramMb,
                cpuCores = cores,
                inferenceThreads = (cores - 2).coerceIn(1, 4),
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            )
        }
    }
}
