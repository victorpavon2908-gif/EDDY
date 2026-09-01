package com.niko.assistant

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.niko.assistant.background.NikoAssistantService
import com.niko.assistant.background.NikoVoiceSettings
import com.niko.assistant.background.NikoRuntimeState
import com.niko.assistant.ui.NikoEmbeddedApp
import com.niko.assistant.ui.NikoReferenceScreen
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import com.niko.assistant.ui.NikoVisualState
import com.niko.assistant.ui.theme.NikoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var fullScreenLauncher: ActivityResultLauncher<Intent>
    private lateinit var batteryLauncher: ActivityResultLauncher<Intent>
    private var overlayPromptedThisSession = false
    private var fullScreenPromptedThisSession = false
    private var batteryPromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLockScreenSetupStatus() }
        fullScreenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            maybeRequestBatteryOptimizationExemption(); refreshLockScreenSetupStatus()
        }
        overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this) && assistantEnabled()) sendServiceAction(NikoAssistantService.ACTION_REFRESH_BUBBLE)
            maybeRequestFullScreenIntentPermission()
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val micGranted = grants[Manifest.permission.RECORD_AUDIO] ?: hasMicrophonePermission()
            if (micGranted && assistantEnabled()) {
                startAssistantService()
                maybeRequestOverlayPermission()
            } else if (!micGranted) {
                NikoRuntimeState.setResponse(applicationContext, "Necesito permiso de micrófono para funcionar fuera de la aplicación.")
            }
        }

        setContent { NikoTheme { NikoAppScreen() } }
        requestAssistantPermissions()
    }

    override fun onStart() {
        super.onStart()
        if (hasMicrophonePermission() && assistantEnabled()) {
            startAssistantService(); sendServiceAction(NikoAssistantService.ACTION_HIDE_BUBBLE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMicrophonePermission() && assistantEnabled()) {
            startAssistantService(); sendServiceAction(NikoAssistantService.ACTION_HIDE_BUBBLE)
        }
        refreshLockScreenSetupStatus()
    }

    override fun onStop() {
        if (hasMicrophonePermission() && assistantEnabled()) sendServiceAction(NikoAssistantService.ACTION_SHOW_BUBBLE)
        super.onStop()
    }

    private fun requestAssistantPermissions() {
        val missing = buildList {
            if (!hasMicrophonePermission()) add(Manifest.permission.RECORD_AUDIO)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            if (assistantEnabled()) { startAssistantService(); maybeRequestOverlayPermission() }
        } else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun maybeRequestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) { maybeRequestFullScreenIntentPermission(); return }
        if (overlayPromptedThisSession) return
        overlayPromptedThisSession = true
        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun maybeRequestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { maybeRequestBatteryOptimizationExemption(); return }
        if (fullScreenPromptedThisSession) { maybeRequestBatteryOptimizationExemption(); return }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.canUseFullScreenIntent()) { maybeRequestBatteryOptimizationExemption(); return }
        fullScreenPromptedThisSession = true
        fullScreenLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
    }

    private fun maybeRequestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || batteryPromptedThisSession) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        batteryPromptedThisSession = true
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { batteryLauncher.launch(directIntent) }
            .onFailure { runCatching { batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }

    private fun refreshLockScreenSetupStatus() {
        if (!assistantEnabled() || !hasMicrophonePermission()) return
        if (NikoRuntimeState.read(applicationContext).running) return
        val fullScreenReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true else true
        val batteryReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true else true
        if (!fullScreenReady) {
            NikoRuntimeState.setResponse(applicationContext, "Activá pantalla completa para que NIKO pueda mostrarse con el teléfono bloqueado.")
        } else if (!batteryReady) {
            NikoRuntimeState.setResponse(applicationContext, "Permití a NIKO funcionar sin optimización de batería para mantener activa la palabra NIKO con la pantalla apagada.")
        }
    }

    private fun startAssistantService(action: String? = null) {
        if (!hasMicrophonePermission() || !assistantEnabled()) return
        val intent = UpgradeIdentity.assistantService(this).apply { this.action = action }
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { NikoRuntimeState.setResponse(applicationContext, "No pude iniciar el modo permanente de NIKO. Abrí la aplicación nuevamente.") }
    }

    private fun sendServiceAction(action: String) {
        runCatching { startService(UpgradeIdentity.assistantService(this).apply { this.action = action }) }
    }

    private fun hasMicrophonePermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun assistantEnabled(): Boolean = NikoVoiceSettings.enabled(this)

    @Composable
    private fun NikoAppScreen() {
        var snapshot by remember { mutableStateOf(NikoRuntimeState.read(applicationContext)) }
        var enabled by remember { mutableStateOf(assistantEnabled()) }
        var uiMode by remember { mutableStateOf(NikoUiModeStore.read(applicationContext)) }
        LaunchedEffect(Unit) {
            while (true) {
                snapshot = NikoRuntimeState.read(applicationContext)
                enabled = assistantEnabled()
                uiMode = NikoUiModeStore.read(applicationContext)
                delay(120L)
            }
        }
        Crossfade(targetState = uiMode, label = "niko-transform") { mode ->
            if (mode == NikoUiMode.ASSISTANT) {
                val visualState = when (snapshot.state) {
                    NikoRuntimeState.State.IDLE -> NikoVisualState.IDLE
                    NikoRuntimeState.State.LISTENING -> NikoVisualState.LISTENING
                    NikoRuntimeState.State.THINKING -> NikoVisualState.THINKING
                    NikoRuntimeState.State.SPEAKING -> NikoVisualState.SPEAKING
                }
                NikoReferenceScreen(
                    visualState = visualState,
                    heardText = snapshot.heardText,
                    responseText = snapshot.responseText,
                    voiceReady = snapshot.voiceReady,
                    autoListeningEnabled = enabled,
                    inputStatus = snapshot.inputStatus,
                    inputState = snapshot.inputState,
                    webSearching = snapshot.webSearching,
                    webUsed = snapshot.webUsed,
                    webSources = snapshot.webSources,
                )
            } else {
                NikoEmbeddedApp(mode = mode, onHome = { NikoUiModeStore.set(applicationContext, NikoUiMode.ASSISTANT) })
            }
        }
    }
}
