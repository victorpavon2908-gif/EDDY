package com.niko.assistant.ui

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.niko.assistant.media.CaptureGate
import com.niko.assistant.media.MediaFiles
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LeoAudioRecorderScreen(onHome: () -> Unit) {
    MediaShell("Grabadora de audio", NikoUiMode.AUDIO_RECORDER, onHome) {
        MediaPermissions(camera = false, audio = true) { AudioRecorderContent() }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun AudioRecorderContent() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val token = remember { Any() }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    var saved by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Lista para grabar") }
    var started by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    fun stop() {
        val active = recorder ?: return
        recorder = null
        val ok = runCatching { active.stop() }.isSuccess
        active.release()
        CaptureGate.release(token)
        if (ok && (output?.length() ?: 0L) > 0L) { saved = output; status = "Audio guardado" }
        else { output?.delete(); status = "No se pudo guardar: la grabación fue demasiado corta o se interrumpió." }
        output = null; busy = false
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop(); CaptureGate.release(token) }
    }
    LaunchedEffect(recorder) {
        while (recorder != null) { elapsed = SystemClock.elapsedRealtime() - started; delay(250L) }
    }
    Text("%02d:%02d".format(elapsed / 60_000, elapsed / 1000 % 60), style = MaterialTheme.typography.displayLarge)
    Text(status)
    Button(enabled = !busy || recorder != null, onClick = {
        if (recorder != null) stop() else {
            busy = true
            scope.launch {
                var pending: MediaRecorder? = null
                var file: File? = null
                try {
                    check(CaptureGate.acquire(token))
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        CaptureGate.release(token); busy = false; return@launch
                    }
                    file = MediaFiles.create(context, "m4a")
                    val active = MediaRecorder(context)
                    pending = active
                    active.setAudioSource(MediaRecorder.AudioSource.MIC)
                    active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    active.setAudioEncodingBitRate(128_000)
                    active.setAudioSamplingRate(44_100)
                    active.setOutputFile(file.absolutePath)
                    active.setMaxDuration(3_600_000)
                    active.setMaxFileSize(128L * 1024 * 1024)
                    active.setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stop()
                    }
                    active.setOnErrorListener { _, _, _ -> stop() }
                    active.prepare(); active.start()
                    output = file; recorder = active; pending = null
                    started = SystemClock.elapsedRealtime(); elapsed = 0
                    status = "Grabando…"
                } catch (error: Exception) {
                    pending?.release(); file?.delete(); busy = false; CaptureGate.release(token)
                    if (error is CancellationException) throw error
                    status = "No pude grabar. Revisá permisos, espacio y micrófono."
                }
            }
        }
    }) { Text(if (recorder != null) "Detener y guardar" else if (busy) "Preparando…" else "Iniciar grabación") }
    Text("LEO pausa la escucha mientras grabás. Se detiene al salir o bloquear la pantalla. Máximo: 1 hora o 128 MB.")
    saved?.let { SavedMedia(it) }
}
