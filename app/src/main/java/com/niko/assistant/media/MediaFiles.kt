package com.niko.assistant.media

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object MediaFiles {
    fun directory(context: Context): File = File(context.filesDir, "captures").apply { mkdirs() }
    fun create(context: Context, extension: String): File =
        File(directory(context), "LEO_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")

    fun open(context: Context, file: File, share: Boolean): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.media", file)
        val type = when (file.extension) { "jpg" -> "image/jpeg"; "mp4" -> "video/mp4"; else -> "audio/mp4" }
        val intent = if (share) Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)
            else Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, if (share) "Exportar captura" else "Abrir captura"))
    }.isSuccess
}
