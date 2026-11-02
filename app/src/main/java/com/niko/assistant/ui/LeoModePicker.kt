package com.niko.assistant.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.niko.assistant.modes.LeoMode
import com.niko.assistant.modes.LeoModeStore

@Composable
internal fun rememberLeoMode(): LeoMode {
    val context = LocalContext.current
    val prefs = remember(context) { LeoModeStore.preferences(context) }
    var mode by remember(prefs) { mutableStateOf(LeoModeStore.read(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LeoModeStore.KEY || key == null) mode = LeoModeStore.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        mode = LeoModeStore.read(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}

@Composable
internal fun LeoModeOptions(selected: LeoMode, onSelect: (LeoMode) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LeoMode.entries.forEach { mode ->
            Surface(color = if (selected == mode) Color(mode.background) else Color.White,
                shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().selectable(selected == mode, role = Role.RadioButton,
                    onClick = { onSelect(mode) }).padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(12.dp).background(Color(mode.ink), CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(mode.title, color = Color(mode.ink), style = MaterialTheme.typography.titleMedium)
                        Text(mode.description, color = Color(0xFF455564), style = MaterialTheme.typography.bodyMedium)
                    }
                    RadioButton(selected = selected == mode, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Color(mode.ink), unselectedColor = Color(0xFF657585)))
                }
            }
        }
    }
}

@Composable
internal fun LeoModeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mode = rememberLeoMode()
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFFF3F2EE),
        titleContentColor = Color(0xFF172B3A), textContentColor = Color(0xFF455564), title = { Text("Modos de LEO") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Decí «Leo, activa modo Super Leo» o elegí un modo.")
            Spacer(Modifier.height(12.dp))
            LeoModeOptions(mode) { LeoModeStore.set(context, it) }
        } }, confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF172B3A))) { Text("Listo") } })
}
