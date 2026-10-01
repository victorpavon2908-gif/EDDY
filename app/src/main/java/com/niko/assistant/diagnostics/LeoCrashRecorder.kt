package com.niko.assistant.diagnostics

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LeoCrashRecorder {
    private const val CRASH_FILE = "leo_last_crash.txt"
    private const val TRACE_FILE = "leo_runtime_trace.txt"
    private const val MAX_TRACE_BYTES = 192 * 1024L

    @Volatile private var installed = false
    @Volatile private var context: Context? = null
    private val lock = Any()

    fun install(application: Application) {
        if (installed) return
        synchronized(lock) {
            if (installed) return
            context = application.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { recordFatal(thread, throwable) }
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
            breadcrumb("application_start")
        }
    }

    fun breadcrumb(value: String) {
        val app = context ?: return
        runCatching {
            synchronized(lock) {
                val file = File(app.filesDir, TRACE_FILE)
                if (file.length() > MAX_TRACE_BYTES) file.writeText("")
                file.appendText(
                    "${stamp()} pid=${Process.myPid()} thread=${Thread.currentThread().name} ${value.take(500)}\n",
                )
            }
        }
    }

    fun recordHandled(where: String, throwable: Throwable) {
        breadcrumb("handled_exception where=$where type=${throwable.javaClass.name} msg=${throwable.message.orEmpty()}")
        val app = context ?: return
        runCatching {
            synchronized(lock) {
                File(app.filesDir, CRASH_FILE).writeText(
                    buildReport("HANDLED", Thread.currentThread(), throwable),
                )
            }
        }
    }

    private fun recordFatal(thread: Thread, throwable: Throwable) {
        val app = context ?: return
        synchronized(lock) {
            File(app.filesDir, CRASH_FILE).writeText(buildReport("FATAL", thread, throwable))
        }
    }

    private fun buildReport(kind: String, thread: Thread, throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            "pid-${Process.myPid()}"
        }
        return buildString {
            appendLine("LEO CRASH REPORT")
            appendLine("kind=$kind")
            appendLine("timestamp=${stamp()}")
            appendLine("process=$processName")
            appendLine("pid=${Process.myPid()}")
            appendLine("thread=${thread.name}")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("exception=${throwable.javaClass.name}")
            appendLine("message=${throwable.message.orEmpty()}")
            appendLine()
            append(writer.toString())
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
