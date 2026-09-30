package com.niko.assistant.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niko.assistant.media.*
import com.niko.assistant.ui.robot.RobotMotion
import com.niko.assistant.ui.robot.RobotMotionBus
import com.niko.assistant.voice.NikoTextToSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LeoMusicScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("leo_music", Context.MODE_PRIVATE) }
    val player = remember { LeoMusicPlayer(context.applicationContext) }
    val playback by player.state.collectAsStateWithLifecycle()
    var speaking by remember { mutableStateOf(false) }
    val voice = remember { NikoTextToSpeech(context.applicationContext, onSpeakingChanged = { speaking = it; player.duck(it) }) }
    var party by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf(prefs.getString("query", "").orEmpty()) }
    val searches = remember(scope) { MusicSearchSession(scope) }
    val searchState by searches.state.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    fun search() { message = ""; searches.submit(query) }
    fun openLink(url: String) {
        if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure)
            message = "No hay una aplicación disponible para abrir este enlace."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull() ?: "Audio del teléfono"
                }
                // A slow document provider can return after the activity has stopped.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.load(uri, name)
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { player.pause(); voice.stop(); searches.cancel(); RobotMotionBus.clear() }
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "query") { query = prefs.getString("query", "").orEmpty(); search() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
            searches.cancel(); player.close(); voice.shutdown(); RobotMotionBus.clear()
        }
    }
    LaunchedEffect(Unit) { if (query.isNotBlank()) search() }
    LaunchedEffect(playback.playing, party) {
        if (!playback.playing || !party) { voice.stop(); RobotMotionBus.clear(); return@LaunchedEffect }
        var count = 0
        val cheers = listOf("¡Qué cool!", "¡Hujuuu!", "¡Ese ritmo está buenísimo!")
        while (true) {
            RobotMotionBus.perform(RobotMotion.DANCE)
            if (count % 6 == 1 && voice.isReady) voice.speak(cheers[(count / 6) % cheers.size])
            count++
            delay(7500L)
        }
    }
    MediaShell("Música con LEO", onHome) {
        NikoHero(if (speaking) NikoVisualState.SPEAKING else NikoVisualState.IDLE,
            modifier = Modifier.fillMaxWidth().height(230.dp))
        Text(playback.title.ifBlank { "Elegí música para empezar" }, style = MaterialTheme.typography.titleLarge)
        if (playback.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        MusicProgress(player, playback)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = playback.title.isNotBlank() && !playback.loading, onClick = { if (playback.playing) player.pause() else player.play() }) {
                Text(if (playback.playing) "Pausar" else "Reproducir")
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Abrir archivo") }
        }
        if (playback.error.isNotBlank()) Text(playback.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = party, onCheckedChange = { party = it })
            Text("Modo fiesta: baile y reacciones de LEO")
        }
        Text("Las reacciones son animaciones y frases ocasionales; no analizan el ritmo del audio.")
        OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, label = { Text("Canción o artista") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { search() }, enabled = query.isNotBlank() && !searchState.loading) { Text(if (searchState.loading) "Buscando…" else "Buscar música") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openLink("https://www.youtube.com/results?search_query=" + Uri.encode(query)) }) { Text("YouTube") }
            TextButton(onClick = { openLink("https://open.spotify.com/search/" + Uri.encode(query)) }) { Text("Spotify") }
        }
        if (message.isNotBlank()) Text(message)
        if (searchState.message.isNotBlank()) Text(searchState.message)
        searchState.results.forEach { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    Text(result.artist)
                    Text("Vista previa · Apple/iTunes")
                    Row {
                        TextButton(onClick = { player.load(Uri.parse(result.preview), result.title) }) { Text("Escuchar muestra") }
                        TextButton(onClick = { openLink(result.link) }) { Text("Abrir en Apple Music") }
                    }
                }
            }
        }
        Text("Reproduce completos los archivos que elijás del teléfono. Los resultados de búsqueda son muestras; los servicios externos pueden requerir suscripción. La música se pausa al salir.")
    }
}

/** Only this small subtree ticks; the 3D hero and search list do not read the playhead. */
@Composable
private fun MusicProgress(player: LeoMusicPlayer, playback: LeoMusicPlayer.State) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var position by remember(player) { mutableFloatStateOf(0f) }
    var dragging by remember(player) { mutableStateOf(false) }
    LaunchedEffect(playback.playing, playback.title, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            position = player.progress()
            while (playback.playing) {
                if (!dragging) position = player.progress()
                delay(500L)
            }
        }
    }
    Slider(value = position,
        onValueChange = { dragging = true; position = it },
        onValueChangeFinished = { player.seek(position); dragging = false },
        enabled = playback.title.isNotBlank() && !playback.loading)
}
