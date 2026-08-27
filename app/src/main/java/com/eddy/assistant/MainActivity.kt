package com.eddy.assistant

import android.Manifest
import android.app.NotificationManager
import android.content.Context
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.eddy.assistant.background.EddyAssistantService
import com.eddy.assistant.background.EddyRuntimeState
import com.eddy.assistant.ui.EddyReferenceScreen
import com.eddy.assistant.ui.EddyVisualState
import com.eddy.assistant.ui.theme.EddyTheme
import kotlinx.coroutines.delay

private const val CONTROL_PREFS = "eddy_control"
private const val KEY_ASSISTANT_ENABLED = "assistant_enabled"

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

        batteryLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshLockScreenSetupStatus()
        }

        fullScreenLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            maybeRequestBatteryOptimizationExemption()
            refreshLockScreenSetupStatus()
        }

        overlayLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (Settings.canDrawOverlays(this) && assistantEnabled()) {
                sendServiceAction(EddyAssistantService.ACTION_REFRESH_BUBBLE)
            }
            maybeRequestFullScreenIntentPermission()
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val micGranted = grants[Manifest.permission.RECORD_AUDIO]
                ?: hasMicrophonePermission()

            if (micGranted && assistantEnabled()) {
                startAssistantService()
                maybeRequestOverlayPermission()
            } else if (!micGranted) {
                EddyRuntimeState.setResponse(
                    applicationContext,
                    "Necesito permiso de micrófono para funcionar fuera de la aplicación.",
                )
            }
        }

        setContent {
            EddyTheme {
                EddyAppScreen()
            }
        }

        requestAssistantPermissions()
    }

    override fun onStart() {
        super.onStart()
        if (hasMicrophonePermission() && assistantEnabled()) {
            startAssistantService()
            sendServiceAction(EddyAssistantService.ACTION_HIDE_BUBBLE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMicrophonePermission() && assistantEnabled()) {
            startAssistantService()
            sendServiceAction(EddyAssistantService.ACTION_HIDE_BUBBLE)
        }
        refreshLockScreenSetupStatus()
    }

    override fun onStop() {
        if (hasMicrophonePermission() && assistantEnabled()) {
            sendServiceAction(EddyAssistantService.ACTION_SHOW_BUBBLE)
        }
        super.onStop()
    }

    private fun requestAssistantPermissions() {
        val missing = buildList {
            if (!hasMicrophonePermission()) {
                add(Manifest.permission.RECORD_AUDIO)
            }

            if (
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.CAMERA,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.CAMERA)
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            if (assistantEnabled()) {
                startAssistantService()
                maybeRequestOverlayPermission()
            }
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeRequestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            maybeRequestFullScreenIntentPermission()
            return
        }
        if (overlayPromptedThisSession) return
        overlayPromptedThisSession = true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        overlayLauncher.launch(intent)
    }

    private fun maybeRequestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            maybeRequestBatteryOptimizationExemption()
            return
        }
        if (fullScreenPromptedThisSession) {
            maybeRequestBatteryOptimizationExemption()
            return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.canUseFullScreenIntent()) {
            maybeRequestBatteryOptimizationExemption()
            return
        }

        fullScreenPromptedThisSession = true
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:$packageName"),
        )
        fullScreenLauncher.launch(intent)
    }

    private fun maybeRequestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (batteryPromptedThisSession) return

        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        batteryPromptedThisSession = true
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )

        runCatching {
            batteryLauncher.launch(directIntent)
        }.onFailure {
            runCatching {
                batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun refreshLockScreenSetupStatus() {
        if (!assistantEnabled() || !hasMicrophonePermission()) return

        val fullScreenReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            true
        }

        val batteryReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true
        } else {
            true
        }

        if (!fullScreenReady) {
            EddyRuntimeState.setResponse(
                applicationContext,
                "Activá pantalla completa para que EDDY pueda mostrarse con el teléfono bloqueado.",
            )
        } else if (!batteryReady) {
            EddyRuntimeState.setResponse(
                applicationContext,
                "Permití a EDDY funcionar sin optimización de batería para mantener activa la palabra EDDY con la pantalla apagada.",
            )
        }
    }

    private fun startAssistantService() {
        if (!hasMicrophonePermission() || !assistantEnabled()) return

        val intent = Intent(this, EddyAssistantService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.onFailure {
            EddyRuntimeState.setResponse(
                applicationContext,
                "No pude iniciar el modo permanente de EDDY. Abrí la aplicación nuevamente.",
            )
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, EddyAssistantService::class.java).apply {
            this.action = action
        }
        runCatching { startService(intent) }
    }

    private fun assistantEnabled(): Boolean = getSharedPreferences(
        CONTROL_PREFS,
        Context.MODE_PRIVATE,
    ).getBoolean(KEY_ASSISTANT_ENABLED, true)

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun EddyAppScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val controlPrefs = remember {
        appContext.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE)
    }
    var snapshot by remember {
        mutableStateOf(EddyRuntimeState.read(appContext))
    }
    var assistantEnabled by remember {
        mutableStateOf(controlPrefs.getBoolean(KEY_ASSISTANT_ENABLED, true))
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = EddyRuntimeState.read(appContext)
            assistantEnabled = controlPrefs.getBoolean(KEY_ASSISTANT_ENABLED, true)
            delay(180)
        }
    }

    val visualState = when (snapshot.state) {
        EddyRuntimeState.State.IDLE -> EddyVisualState.IDLE
        EddyRuntimeState.State.LISTENING -> EddyVisualState.LISTENING
        EddyRuntimeState.State.THINKING -> EddyVisualState.THINKING
        EddyRuntimeState.State.SPEAKING -> EddyVisualState.SPEAKING
    }

    EddyReferenceScreen(
        visualState = visualState,
        heardText = snapshot.heardText,
        responseText = snapshot.responseText,
        voiceReady = snapshot.voiceReady,
        autoListeningEnabled = assistantEnabled && snapshot.running,
        webUsed = snapshot.webUsed,
        webSources = snapshot.webSources,
        onToggleAssistant = {
            val enable = !assistantEnabled
            assistantEnabled = enable
            controlPrefs.edit().putBoolean(KEY_ASSISTANT_ENABLED, enable).apply()

            val serviceIntent = Intent(appContext, EddyAssistantService::class.java)
            if (enable) {
                val micGranted = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (micGranted) {
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                    EddyRuntimeState.setResponse(appContext, "EDDY está activo. Decí EDDY para llamarme.")
                } else {
                    EddyRuntimeState.setResponse(appContext, "Concedé el permiso de micrófono para activar EDDY.")
                }
            } else {
                appContext.stopService(serviceIntent)
                EddyRuntimeState.setResponse(appContext, "EDDY está pausado. Tocá Activar cuando querás continuar.")
            }
        },
    )
}
