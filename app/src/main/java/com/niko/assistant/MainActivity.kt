package com.niko.assistant

import com.niko.assistant.compat.UpgradeIdentity

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import com.niko.assistant.diagnostics.LeoCrashRecorder
import com.niko.assistant.startup.LeoFirstRunSetup
import com.niko.assistant.startup.LeoFirstRunState
import com.niko.assistant.localai.LeoFrozenBrainManager
import com.niko.assistant.ui.LeoBrainStatusOverlay
import com.niko.assistant.ui.LeoFirstRunScreen
import com.niko.assistant.ui.LeoLiveTranscriptOverlay
import com.niko.assistant.ui.LeoMorphTransitionOverlay
import com.niko.assistant.ui.NikoEmbeddedApp
import com.niko.assistant.ui.NikoReferenceScreen
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import com.niko.assistant.ui.NikoVisualState
import com.niko.assistant.ui.theme.NikoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val runtimeStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let(NikoRuntimeState::acceptExternalState)
        }
    }
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var firstRunSetup: LeoFirstRunSetup
    private var setupJob: Job? = null
    private var frozenBrainJob: Job? = null
    private lateinit var frozenBrainManager: LeoFrozenBrainManager
    private val setupState = mutableStateOf(LeoFirstRunState())
    private var overlayPromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        NikoVoiceSettings.ensureIsolatedVoiceMigration(applicationContext)
        firstRunSetup = LeoFirstRunSetup(applicationContext)
        frozenBrainManager = LeoFrozenBrainManager(applicationContext)
        setupState.value = if (firstRunSetup.isReady()) {
            LeoFirstRunState.ready(firstRunSetup.requiredModels().size)
        } else {
            LeoFirstRunState(
                phase = LeoFirstRunState.Phase.WAITING,
                totalModels = firstRunSetup.requiredModels().size,
                message = "Primero voy a preparar todos los módulos que faltan. LEO arrancará cuando termine.",
            )
        }

        overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this) && canRunLeo()) sendServiceAction(NikoAssistantService.ACTION_REFRESH_BUBBLE)
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

        ContextCompat.registerReceiver(
            this,
            runtimeStateReceiver,
            IntentFilter(NikoRuntimeState.ACTION_RUNTIME_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { NikoTheme { NikoAppScreen() } }
        requestAssistantPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (canRunLeo()) {
            val snapshot = NikoRuntimeState.read(applicationContext)
            if (snapshot.inputState == NikoRuntimeState.InputState.STOPPED || snapshot.inputState == NikoRuntimeState.InputState.ERROR) {
                NikoRuntimeState.setResponse(applicationContext, "Preparando el micrófono de LEO…")
            }
            startAssistantService(); sendServiceAction(NikoAssistantService.ACTION_HIDE_BUBBLE)
            ensureFrozenBrain()
        } else if (hasMicrophonePermission()) {
            beginInitialSetupOrStart()
        }
    }

    override fun onStop() {
        if (canRunLeo()) sendServiceAction(NikoAssistantService.ACTION_SHOW_BUBBLE)
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(runtimeStateReceiver) }
        super.onDestroy()
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
            ensureFrozenBrain()
            return
        }
        if (setupJob?.isActive == true) return

        setupJob = lifecycleScope.launch {
            val result = try {
                firstRunSetup.prepare { progress -> runOnUiThread { setupState.value = progress } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setupState.value = LeoFirstRunState.failed(
                    firstRunSetup.requiredModels().size,
                    "No pude preparar los archivos. Revisá conexión y almacenamiento y tocá Reintentar.",
                )
                return@launch
            }
            if (result.ready && firstRunSetup.isReady()) {
                setupState.value = LeoFirstRunState.ready(firstRunSetup.requiredModels().size)
                NikoRuntimeState.setResponse(applicationContext, "Preparación inicial completa. LEO ya puede arrancar normalmente.")
                if (assistantEnabled()) {
                    startAssistantService()
                    maybeRequestOverlayPermission()
                }
                ensureFrozenBrain()
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
        if (Settings.canDrawOverlays(this)) return
        if (overlayPromptedThisSession) return
        overlayPromptedThisSession = true
        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun ensureFrozenBrain() {
        if (!firstRunSetup.isReady() || frozenBrainJob?.isActive == true) return
        if (frozenBrainManager.isInstalled()) {
            NikoRuntimeState.setBrainProgress(
                applicationContext,
                NikoRuntimeState.BrainState.READY,
                "Cerebro local listo",
            )
            return
        }
        frozenBrainJob = lifecycleScope.launch {
            frozenBrainManager.ensureInstalled { progress ->
                val state = when (progress.state) {
                    LeoFrozenBrainManager.Progress.State.CHECKING -> NikoRuntimeState.BrainState.CHECKING
                    LeoFrozenBrainManager.Progress.State.DOWNLOADING -> NikoRuntimeState.BrainState.DOWNLOADING
                    LeoFrozenBrainManager.Progress.State.VERIFYING -> NikoRuntimeState.BrainState.VERIFYING
                    LeoFrozenBrainManager.Progress.State.INSTALLING -> NikoRuntimeState.BrainState.INSTALLING
                    LeoFrozenBrainManager.Progress.State.READY -> NikoRuntimeState.BrainState.READY
                    LeoFrozenBrainManager.Progress.State.FAILED -> NikoRuntimeState.BrainState.ERROR
                }
                NikoRuntimeState.setBrainProgress(
                    applicationContext,
                    state,
                    progress.message,
                    progress.downloadedBytes,
                    progress.totalBytes,
                )
            }
        }
    }

    private fun startAssistantService(action: String? = null) {
        if (!canRunLeo()) return
        val intent = UpgradeIdentity.assistantService(this).apply {
            this.action = action ?: NikoAssistantService.ACTION_START
        }
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

        // StateFlow already has a current value; do not reread preferences on each streamed token.
        val snapshot by NikoRuntimeState.stateFlow.collectAsStateWithLifecycle()
        var enabled by remember { mutableStateOf(assistantEnabled()) }
        var uiMode by remember { mutableStateOf(NikoUiModeStore.read(applicationContext)) }
        DisposableEffect(Unit) {
            val controls = getSharedPreferences(UpgradeIdentity.controlPreferences, Context.MODE_PRIVATE)
            val modes = getSharedPreferences(UpgradeIdentity.uiPreferences, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                enabled = assistantEnabled()
                uiMode = NikoUiModeStore.read(applicationContext)
            }
            controls.registerOnSharedPreferenceChangeListener(listener)
            modes.registerOnSharedPreferenceChangeListener(listener)
            enabled = assistantEnabled()
            uiMode = NikoUiModeStore.read(applicationContext)
            onDispose {
                controls.unregisterOnSharedPreferenceChangeListener(listener)
                modes.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        BackHandler(enabled = uiMode != NikoUiMode.ASSISTANT) {
            NikoUiModeStore.set(applicationContext, NikoUiMode.ASSISTANT)
        }
        val toolState = rememberSaveableStateHolder()
        var renderedMode by remember { mutableStateOf(uiMode) }
        var morphTarget by remember { mutableStateOf(uiMode) }
        var morphing by remember { mutableStateOf(false) }

        // Evitamos componer dos pantallas pesadas a la vez. CameraX, SceneView y Audio
        // pueden chocar en algunos OEM si una transición mantiene viva la pantalla anterior
        // mientras crea la nueva. Primero cubrimos con el morph, luego cambiamos de forma.
        LaunchedEffect(uiMode) {
            if (uiMode != renderedMode) {
                LeoCrashRecorder.breadcrumb("morph_begin from=${renderedMode.id} to=${uiMode.id}")
                morphTarget = uiMode
                morphing = true
                delay(260L)
                renderedMode = uiMode
                LeoCrashRecorder.breadcrumb("morph_render mode=${uiMode.id}")
                delay(360L)
                morphing = false
                LeoCrashRecorder.breadcrumb("morph_complete mode=${uiMode.id}")
            }
        }

        Box(Modifier.fillMaxSize()) {
            val mode = renderedMode
            toolState.SaveableStateProvider(mode.id) {
                if (mode == NikoUiMode.ASSISTANT) {
                    val visualState = when (snapshot.state) {
                        NikoRuntimeState.State.IDLE -> NikoVisualState.IDLE
                        NikoRuntimeState.State.LISTENING -> NikoVisualState.LISTENING
                        NikoRuntimeState.State.THINKING -> NikoVisualState.THINKING
                        NikoRuntimeState.State.SPEAKING -> NikoVisualState.SPEAKING
                    }
                    Box(Modifier.fillMaxSize()) {
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
                            onTools = { NikoUiModeStore.set(applicationContext, NikoUiMode.TOOLBOX) },
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
                    NikoEmbeddedApp(
                        mode = mode,
                        onHome = { NikoUiModeStore.set(applicationContext, NikoUiMode.ASSISTANT) },
                    )
                }
            }

            LeoMorphTransitionOverlay(
                mode = morphTarget,
                visible = morphing,
            )
        }
    }
}
