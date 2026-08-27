package com.eddy.assistant

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
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

class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayLauncher: ActivityResultLauncher<Intent>
    private lateinit var fullScreenLauncher: ActivityResultLauncher<Intent>
    private var overlayPromptedThisSession = false
    private var fullScreenPromptedThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        fullScreenLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            // Android aplica el cambio de permiso al volver de Configuración.
        }

        overlayLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (Settings.canDrawOverlays(this)) {
                sendServiceAction(EddyAssistantService.ACTION_REFRESH_BUBBLE)
            }
            maybeRequestFullScreenIntentPermission()
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val micGranted = grants[Manifest.permission.RECORD_AUDIO]
                ?: hasMicrophonePermission()

            if (micGranted) {
                startAssistantService()
                maybeRequestOverlayPermission()
            } else {
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
        if (hasMicrophonePermission()) {
            startAssistantService()
            sendServiceAction(EddyAssistantService.ACTION_HIDE_BUBBLE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMicrophonePermission()) {
            startAssistantService()
            sendServiceAction(EddyAssistantService.ACTION_HIDE_BUBBLE)
        }
    }

    override fun onStop() {
        if (hasMicrophonePermission()) {
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
            startAssistantService()
            maybeRequestOverlayPermission()
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (fullScreenPromptedThisSession) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.canUseFullScreenIntent()) return

        fullScreenPromptedThisSession = true
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:$packageName"),
        )
        fullScreenLauncher.launch(intent)
    }

    private fun startAssistantService() {
        if (!hasMicrophonePermission()) return

        val intent = Intent(this, EddyAssistantService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.onFailure {
            EddyRuntimeState.setResponse(
                applicationContext,
                "No pude iniciar el modo permanente de EDDY. Abre la aplicación nuevamente.",
            )
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, EddyAssistantService::class.java).apply {
            this.action = action
        }
        runCatching { startService(intent) }
    }

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
    var snapshot by remember {
        mutableStateOf(EddyRuntimeState.read(context.applicationContext))
    }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = EddyRuntimeState.read(context.applicationContext)
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
        autoListeningEnabled = snapshot.running,
    )
}
