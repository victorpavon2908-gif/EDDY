package com.niko.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.niko.assistant.media.MediaFiles
import java.io.File

@Composable
internal fun MediaShell(title: String, onHome: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onHome) { Text("LEO") }
            OutlinedButton(onClick = { NikoUiModeStore.set(context, NikoUiMode.TOOLBOX) }) { Text("Transformarme") }
        }
        content()
    }
}

@Composable
internal fun MediaPermissions(camera: Boolean, audio: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permissions = remember(camera, audio) { buildList {
        if (camera) add(Manifest.permission.CAMERA)
        if (audio) add(Manifest.permission.RECORD_AUDIO)
    } }
    fun hasPermissions() = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(hasPermissions()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasPermissions() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = hasPermissions() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (granted) content() else {
        Text("Permití el acceso para usar esta herramienta. La grabación empieza cuando tocás Iniciar.")
        Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text("Conceder permisos") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
            Text("Abrir ajustes de permisos")
        }
    }
}

@Composable
internal fun SavedMedia(file: File) {
    val context = LocalContext.current
    var error by remember(file) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(file.name)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { error = !MediaFiles.open(context, file, false) }) { Text("Abrir") }
                TextButton(onClick = { error = !MediaFiles.open(context, file, true) }) { Text("Compartir / exportar") }
            }
            if (error) Text("No hay una aplicación disponible para abrir o compartir este archivo.")
        }
    }
}

@Composable
internal fun LeoToolbox(onHome: () -> Unit) {
    val context = LocalContext.current
    val files = remember { MediaFiles.directory(context).listFiles().orEmpty().filter { it.isFile && it.length() > 0 }
        .sortedByDescending { it.lastModified() }.take(30) }
    MediaShell("¿En qué me convierto?", onHome) {
        Text("Elegí una forma o decí «LEO, conviértete en una cámara».")
        NikoUiMode.entries.filter { it !in setOf(NikoUiMode.ASSISTANT, NikoUiMode.TOOLBOX) }.forEach { mode ->
            FilledTonalButton(onClick = { NikoUiModeStore.set(context, mode) }, modifier = Modifier.fillMaxWidth()) { Text(mode.title) }
        }
        Text("Capturas recientes", style = MaterialTheme.typography.titleLarge)
        Text("Se guardan en este teléfono. Exportalas antes de desinstalar EDDY.")
        files.forEach { SavedMedia(it) }
        if (files.isEmpty()) Text("Todavía no hay capturas.")
    }
}
