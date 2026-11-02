package com.niko.assistant.memory

import android.content.Context
import com.niko.assistant.learning.AdaptiveIntentStore
import com.niko.assistant.learning.LearnedActionStore
import com.niko.assistant.proactive.LeoInitiativeStore
import com.niko.assistant.selfupgrade.NikoSelfUpgradeManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One erasure path for settings and voice. Model files, voice identity and app settings stay intact. */
object LeoPersonalDataReset {
    val learningMutex = Mutex()
    private val resetMutex = Mutex()
    @Volatile var clearing: Boolean = false
        private set

    suspend fun clear(context: Context, before: () -> Unit = {}, after: () -> Unit = {}) = resetMutex.withLock {
        clearing = true
        try {
            before()
            withContext(Dispatchers.IO) {
                learningMutex.withLock {
                    val app = context.applicationContext
                    val directory = File(app.filesDir, "adaptive_learning")
                    val operations: List<() -> Unit> = listOf(
                        { NikoMemory(app).clearAll() },
                        { AdaptiveIntentStore(directory).clear() },
                        { LearnedActionStore(directory).clear() },
                        { NikoSelfUpgradeManager(app).clear() },
                        { LeoSovereignStore(app.filesDir).clear() },
                        { LeoInitiativeStore(app).clear() },
                    )
                    val errors = operations.mapNotNull { operation -> runCatching(operation).exceptionOrNull() }
                    check(errors.isEmpty()) { "No se pudo borrar toda la memoria personal; volvé a intentarlo." }
                }
            }
        } finally {
            try { after() } finally { clearing = false }
        }
    }
}
