package com.niko.assistant.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

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
     * MediaPipe Tasks GenAI 0.10.24 puede abortar el proceso completo dentro de
     * libllm_inference_engine_jni.so en algunos HONOR. Ese SIGSEGV ocurre fuera de la JVM,
     * por lo que runCatching/try-catch no puede recuperarlo. En esos equipos mantenemos
     * toda la voz y las acciones locales, pero no entramos al runtime nativo de Qwen.
     */
    val localLlmRuntimeSafe: Boolean
        get() = manufacturer.trim().lowercase(Locale.ROOT) != "honor"

    // La conversación local solo se habilita si el hardware alcanza y el runtime nativo
    // es seguro para el fabricante. Esto también evita descargar Qwen en la primera
    // instalación de un equipo donde no lo vamos a ejecutar.
    val supportsLocalLlm: Boolean
        get() = localLlmRuntimeSafe && totalRamMb >= 4_500L && cpuCores >= 6

    // El 1.5B INT8 necesita bastante más memoria que el 0.5B. A partir de ~5.5 GB
    // y ocho núcleos preferimos calidad; equipos más modestos conservan el modelo rápido.
    val prefersQualityLocalLlm: Boolean get() = supportsLocalLlm && totalRamMb >= 5_500L && cpuCores >= 8

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
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
            )
        }
    }
}
