package com.niko.assistant.inference.local

import android.content.*
import android.os.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicLong

class LeoLocalInferenceClient(context: Context) {
    private val app = context.applicationContext
    suspend fun reply(question: String, context: String = "", validating: Boolean = false, model: LeoLocalModelSpec? = null): String? {
        val store = LeoLocalModelStore(app, if (validating) model else null)
        val revision = store.snapshot()
        if (!validating && revision == null) return null
        if (!store.compatible() || !store.modelFile.isFile) return null
        val prompt = runCatching { LeoLocalPrompt.build(question, context).toByteArray(Charsets.UTF_8) }.getOrNull() ?: return null
        return withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(95000) {
                suspendCancellableCoroutine { continuation ->
                    val id = ids.incrementAndGet()
                    val main = Handler(Looper.getMainLooper())
                    var remote: Messenger? = null
                    var bound = false
                    lateinit var connection: ServiceConnection
                    fun release(cancel: Boolean) {
                        if (cancel) runCatching { remote?.send(Message.obtain().apply {
                            what = LeoLocalInferenceService.CANCEL
                            data = Bundle().apply { putLong("id", id) }
                        }) }
                        if (bound) { bound = false; runCatching { app.unbindService(connection) } }
                    }
                    fun finish(answer: String?) {
                        release(false)
                        if (continuation.isActive) continuation.resume(answer)
                    }
                    val receiver = Messenger(Handler(Looper.getMainLooper()) { msg ->
                        if (msg.what == LeoLocalInferenceService.RESULT && msg.data.getLong("id") == id) {
                            val answer = msg.data.getByteArray("answer")?.toString(Charsets.UTF_8)
                            finish(if (validating || store.isCurrent(revision)) LeoLocalPrompt.answer(answer) else null)
                        }
                        true
                    })
                    connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            remote = Messenger(binder)
                            if (!continuation.isActive) { release(true); return }
                            if (!validating && !store.isCurrent(revision)) { finish(null); return }
                            try { remote!!.send(Message.obtain().apply {
                                what = LeoLocalInferenceService.RUN; replyTo = receiver
                                data = Bundle().apply { putLong("id", id); putByteArray("prompt", prompt); putString("model_id", store.spec.id) }
                            }) } catch (_: RemoteException) { finish(null) }
                        }
                        override fun onServiceDisconnected(name: ComponentName) = finish(null)
                        override fun onBindingDied(name: ComponentName) = finish(null)
                        override fun onNullBinding(name: ComponentName) = finish(null)
                    }
                    bound = runCatching { app.bindService(Intent(app, LeoLocalInferenceService::class.java), connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
                    if (!bound) finish(null)
                    continuation.invokeOnCancellation { main.post { release(true) } }
                }
            }
        }
    }
    companion object { private val ids = AtomicLong(System.currentTimeMillis()) }
}
