package com.niko.assistant.inference.local

import android.app.Service
import android.content.Intent
import android.os.*
import java.util.concurrent.Executors

/** Separate OS process: native crashes/OOM cannot take down wake, ASR or the avatar process. */
class LeoLocalInferenceService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var active = 0L
    private var destroyed = false
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { message ->
        val id = message.data.getLong("id")
        when (message.what) {
            CANCEL -> if (id == active) runCatching { LeoLlamaBridge.cancel(id) }
            RUN -> {
                val receiver = message.replyTo
                val prompt = message.data.getByteArray("prompt")
                if (active != 0L || destroyed || id <= 0 || prompt == null || prompt.size > 16000) {
                    respond(receiver, id, null)
                } else {
                    active = id
                    worker.execute {
                        val result = try {
                            val file = LeoLocalModelStore(this).modelFile
                            if (!file.isFile || file.length() != LeoLocalModelStore.BYTES) null
                            else LeoLlamaBridge.generate(file.absolutePath, prompt, id, 384)
                        } catch (_: Exception) { null } catch (_: LinkageError) { null }
                        main.post { active = 0; if (!destroyed) respond(receiver, id, result) }
                    }
                }
            }
        }
        true
    })
    private fun respond(receiver: Messenger?, id: Long, result: ByteArray?) {
        runCatching { receiver?.send(Message.obtain().apply {
            what = RESULT; data = Bundle().apply { putLong("id", id); putByteArray("answer", result) }
        }) }
    }
    override fun onBind(intent: Intent) = inbox.binder
    override fun onDestroy() {
        destroyed = true
        if (active != 0L) runCatching { LeoLlamaBridge.cancel(active) }
        worker.shutdown()
        super.onDestroy()
    }
    companion object { const val RUN = 1; const val CANCEL = 2; const val RESULT = 3 }
}
