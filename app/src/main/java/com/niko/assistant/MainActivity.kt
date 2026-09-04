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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.niko.assistant.background.NikoAssistantService
import com.niko.assistant.background.NikoVoiceSettings
import com.niko.assistant.background.NikoRuntimeState
import com.niko.assistant.startup.LeoFirstRunSetup
import com.niko.assistant.startup.LeoFirstRunState
import com.niko.assistant.ui.LeoBrainStatusOverlay
import com.niko.assistant.ui.LeoFirstRunScreen
import com.niko.assistant.ui.LeoLiveTranscriptOverlay
import com.niko.assistant.ui.NikoEmbeddedApp
import com.niko.assistant.ui.NikoReferenceScreen
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import com.niko.assistant.ui.NikoVisualState
import com.niko.assistant.ui.theme.NikoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var fullScreenLauncher: ActivityResultLauncher<Intent>
    private lateinit var batteryLauncher: ActivityResultLauncher<Intent>
    private lateinit var firstRunSetup: LeoFirstRunSetup
    private var setupJob: Job? = null
    private val setupState = mutableStateOf(LeoFirstRunState())
    private var overlayPromptedThisSession = false
    private var fullScreenPromptedThisSession = false
    private var batteryPromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        firstRunSetup = LeoFirstRunSetup(applicationContext)
        setupState.value = if (firstRunSetup.isReady()) {
            LeoFirstRunState.ready(firstRunSetup.requiredModels().size)
        } else {
            LeoFirstRunState(
                phase = LeoFirstRunState.Phase.WAITING,
                totalModels = firstRunSetup.requiredModels().size,
                message = "Primero voy a preparar todos los módulos que faltan. LEO arrancará cuando termine.",
            )
        }

        batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLockScreenSetupStatus() }
        fullScreenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            maybeRequestBatteryOptimizationExemption(); refreshLockScreenSetupStatus()
        }
        overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this) && canRunLeo()) sendServiceAction(NikoAssistantService.ACTION_REFRESH_BUBBLE)
            maybeRequestFullScreenIntentPermission()
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val micGranted = grants[Manifest.permission.RECORD_AUDIO] ?: hasMicrophonePermission()
            if (micGranted) {
                beginInitialSetupOrStart()
            } else {
                val message = "Necesito permiso de micrófono antes de preparar y arrancar LEO."
                setupState.value = LeoFirstRunState.failed(firstRunSetup.requiredModels().size, message, "Permiso de micrófono")
                NikoRuntimeState.setResponse(applicationContext, message)
            }
        }

        setContent { NikoTheme { NikoAppScreen() } }
        requestAssistantPermissions()
    }

    override fun onStart() {
        super.onStart()
        if (canRunLeo()) {
            startAssistantService(); sendServiceAction(NikoAssistantService.ACTION_HIDE_BUBBLE)
        } else if (hasMicrophonePermission()) {
            beginInitialSetupOrStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (canRunLeo()) {
            startAssistantService(); sendServiceAction(NikoAssistantService.ACTION_HIDE_BUBBLE)
        } else if (hasMicrophonePermission()) {
            beginInitialSetupOrStart()
        }
        refreshLockScreenSetupStatus()
    }

    override fun onStop() {
        if (canRunLeo()) sendServiceAction(NikoAssistantService.ACTION_SHOW_BUBBLE)
        super.onStop()
    }

    private fun requestAssistantPermissions() {
        val missing = buildList {
            if (!hasMicrophonePermission()) add(Manifest.permission.RECORD_AUDIO)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            beginInitialSetupOrStart()
        } else {
            setupState.value = setupState.value.copy(
                phase = LeoFirstRunState.Phase.WAITING,
                message = "Concedé los permisos iniciales. Después descargaré y verificaré todo antes de arrancar LEO.",
            )
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun beginInitialSetupOrStart() {
        if (!hasMicrophonePermission()) return
        if (firstRunSetup.isReady()) {
            setupState.value = LeoFirstRunState.ready(firstRunSetup.requiredModels().size)
            if (assistantEnabled()) {
                startAssistantService()
                maybeRequestOverlayPermission()
            }
            return
        }
        if (setupJob?.isActive == true) return

        setupJob = lifecycleScope.launch {
            val result = firstRunSetup.prepare { progress -> runOnUiThread { setupState.value = progress } }
            if (result.ready && firstRunSetup.isReady()) {
                setupState.value = LeoFirstRunState.ready(firstRunSetup.requiredModels().size)
                NikoRuntimeState.setResponse(applicationContext, "Preparación inicial completa. LEO ya puede arrancar normalmente.")
                if (assistantEnabled()) {
                    startAssistantService()
                    maybeRequestOverlayPermission()
                }
            } else if (setupState.value.phase != LeoFirstRunState.Phase.FAILED) {
                setupState.value = LeoFirstRunState.failed(firstRunSetup.requiredModels().size, result.message)
            }
        }
    }

    private fun retryInitialSetup() {
        if (!hasMicrophonePermission()) requestAssistantPermissions() else beginInitialSetupOrStart()
    }

    private fun maybeRequestOverlayPermission() {
        if (!firstRunSetup.isReady()) return
        if (Settings.canDrawOverlays(this)) { maybeRequestFullScreenIntentPermission(); return }
        if (overlayPromptedThisSession) return
        overlayPromptedThisSession = true
        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun maybeRequestFullScreenIntentPermission() {
        if (!firstRunSetup.isReady()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { maybeRequestBatteryOptimizationExemption(); return }
        if (fullScreenPromptedThisSession) { maybeRequestBatteryOptimizationExemption(); return }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.canUseFullScreenIntent()) { maybeRequestBatteryOptimizationExemption(); return }
        fullScreenPromptedThisSession = true
        fullScreenLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
    }

    private fun maybeRequestBatteryOptimizationExemption() {
        if (!firstRunSetup.isReady()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || batteryPromptedThisSession) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        batteryPromptedThisSession = true
        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { batteryLauncher.launch(directIntent) }
            .onFailure { runCatching { batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }

    private fun refreshLockScreenSetupStatus() {
        if (!canRunLeo()) return
        if (NikoRuntimeState.read(applicationContext).running) return
        val fullScreenReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true else true
        val batteryReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true else true
        if (!fullScreenReady) {
            NikoRuntimeState.setResponse(applicationContext, "Activá pantalla completa para que LEO pueda mostrarse con el teléfono bloqueado.")
        } else if (!batteryReady) {
            NikoRuntimeState.setResponse(applicationContext, "Permití a LEO funcionar sin optimización de batería para mantener activa la palabra LEO con la pantalla apagada.")
        }
    }

    private fun startAssistantService(action: String? = null) {
        if (!canRunLeo()) return
        val intent = UpgradeIdentity.assistantService(this).apply { this.action = action }
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { NikoRuntimeState.setResponse(applicationContext, "No pude iniciar el modo permanente de LEO. Abrí la aplicación nuevamente.") }
    }

    private fun sendServiceAction(action: String) {
        if (!canRunLeo()) return
        runCatching { startService(UpgradeIdentity.assistantService(this).apply { this.action = action }) }
    }

    private fun hasMicrophonePermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun assistantEnabled(): Boolean = NikoVoiceSettings.enabled(this)
    private fun canRunLeo(): Boolean = hasMicrophonePermission() && assistantEnabled() && firstRunSetup.isReady()

    @Composable
    private fun NikoAppScreen() {
        val preparation = setupState.value
        if (preparation.phase != LeoFirstRunState.Phase.READY) {
            LeoFirstRunScreen(state = preparation, onRetry = ::retryInitialSetup)
            return
        }

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
        Crossfade(targetState = uiMode, label = "leo-transform") { mode ->
            if (mode == NikoUiMode.ASSISTANT) {
                val visualState = when (snapshot.state) {
                    NikoRuntimeState.State.IDLE -> NikoVisualState.IDLE
                    NikoRuntimeState.State.LISTENING -> NikoVisualState.LISTENING
                    NikoRuntimeState.State.THINKING -> NikoVisualState.THINKING
                    NikoRuntimeState.State.SPEAKING -> NikoVisualState.SPEAKING
                }
                Box {
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
                    LeoBrainStatusOverlay(
                        state = snapshot.brainState,
                        progress = snapshot.brainProgress,
                        status = snapshot.brainStatus,
                        downloadedBytes = snapshot.brainDownloadedBytes,
                        totalBytes = snapshot.brainTotalBytes,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 60.dp, start = 18.dp, end = 18.dp),
                    )
                    LeoLiveTranscriptOverlay(visualState)
                }
            } else {
                NikoEmbeddedApp(mode = mode, onHome = { NikoUiModeStore.set(applicationContext, NikoUiMode.ASSISTANT) })
            }
        }
    }
}
