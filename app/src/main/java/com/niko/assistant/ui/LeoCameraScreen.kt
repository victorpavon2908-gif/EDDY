package com.niko.assistant.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.niko.assistant.media.CaptureGate
import com.niko.assistant.media.MediaFiles
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LeoCameraScreen(video: Boolean, onHome: () -> Unit) {
    MediaShell(if (video) "Grabadora de video" else "Cámara", onHome) {
        MediaPermissions(camera = true, audio = video) { CameraContent(video) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraContent(video: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    val token = remember { Any() }
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val preview = remember { Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) } }
    val photo = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val capture = remember { VideoCapture.withOutput(Recorder.Builder().setQualitySelector(
        QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build()) }
    var front by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var saved by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Preparando cámara…") }

    DisposableEffect(owner, front) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) runCatching {
                val available = future.get()
                provider = available
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                check(available.hasCamera(selector))
                if (video) available.bindToLifecycle(owner, selector, preview, capture)
                else available.bindToLifecycle(owner, selector, preview, photo)
                ready = true
                status = "Lista"
            }.onFailure { ready = false; status = "No pude abrir esta cámara. Probá cambiar de cámara." }
        }, executor)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) recording?.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            disposed = true
            ready = false
            recording?.stop()
            provider?.unbind(preview, photo, capture)
            owner.lifecycle.removeObserver(observer)
            // The finalization callback owns the gate while a video is being saved.
            if (recording == null) CaptureGate.release(token)
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(340.dp))
    Text(status)
    OutlinedButton(onClick = { ready = false; front = !front }, enabled = !busy) {
        Text(if (front) "Usar cámara trasera" else "Usar cámara frontal")
    }
    if (!video) {
        Button(enabled = ready && !busy, onClick = {
            busy = true
            val file = MediaFiles.create(context, "jpg")
            photo.targetRotation = view.display?.rotation ?: 0
            runCatching {
                photo.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            saved = file; busy = false; status = "Foto guardada"
                        }
                        override fun onError(error: ImageCaptureException) {
                            file.delete(); busy = false; status = "No pude guardar la foto."
                        }
                    })
            }.onFailure { file.delete(); busy = false; status = "No pude tomar la foto." }
        }) { Text(if (busy) "Guardando…" else "Tomar foto") }
    } else {
        Button(enabled = ready && (!busy || recording != null), onClick = {
            if (recording != null) { recording?.stop(); status = "Guardando video…" }
            else {
                busy = true
                scope.launch {
                    var file: File? = null
                    try {
                        check(CaptureGate.acquire(token)) { "El micrófono está ocupado." }
                        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            CaptureGate.release(token); busy = false; return@launch
                        }
                        val target = MediaFiles.create(context, "mp4")
                        file = target
                        capture.targetRotation = view.display?.rotation ?: 0
                        val output = FileOutputOptions.Builder(target).setFileSizeLimit(512L * 1024 * 1024).build()
                        recording = capture.output.prepareRecording(context, output).withAudioEnabled().start(executor) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> status = "Grabando…"
                                is VideoRecordEvent.Status -> status = "Grabando: ${event.recordingStats.recordedDurationNanos / 1_000_000_000}s"
                                is VideoRecordEvent.Finalize -> {
                                    recording = null; busy = false; CaptureGate.release(token)
                                    if ((!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED) && target.length() > 0) {
                                        saved = target; status = "Video guardado"
                                    } else { target.delete(); status = "No pude finalizar el video. Probá nuevamente." }
                                }
                            }
                        }
                    } catch (error: Exception) {
                        file?.delete(); busy = false; CaptureGate.release(token)
                        if (error is CancellationException) throw error
                        status = "No pude iniciar la grabación. Revisá permisos y micrófono."
                    }
                }
            }
        }) { Text(if (recording != null) "Detener video" else if (busy) "Preparando…" else "Iniciar video") }
        Text("Durante la grabación LEO pausa la escucha. Tocá Detener para finalizar. También se detiene al salir o bloquear la pantalla. Límite: 512 MB.")
    }
    saved?.let { SavedMedia(it) }
}
