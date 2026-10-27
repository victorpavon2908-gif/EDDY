package com.niko.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import com.niko.assistant.ui.LeoUserGuideDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.niko.assistant.actions.LeoHardwareProtocolRunner
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ai.NikoPersonality
import com.niko.assistant.background.NikoAssistantService
import com.niko.assistant.background.NikoRuntimeState
import com.niko.assistant.background.NikoVoiceSettings
import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.devicecontrol.NikoAccessibilityService
import com.niko.assistant.inference.local.LeoLocalModelPanel
import com.niko.assistant.localai.LeoFrozenBrainManager
import com.niko.assistant.localai.LeoOfflineKnowledgeSettings
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelSpec
import com.niko.assistant.localai.NikoVoiceProfile
import com.niko.assistant.memory.LeoSovereignStore
import com.niko.assistant.memory.NikoMemory
import com.niko.assistant.memory.embedding.LeoSemanticMemory
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Slate surfaces and restrained sage accents for the settings workspace.
private val DarkBgTop = Color(0xFF111820)
private val DarkCardBg = Color(0xFF1B2530)
private val DarkSubCardBg = Color(0xFF222F3B)
private val DarkBorder = Color(0xFF364655)
private val DarkEmerald = Color(0xFFA9CCBC)
private val DarkCyan = Color(0xFFA9CCBC)
private val DarkAmber = Color(0xFFE6C48C)
private val DarkDanger = Color(0xFFFFAFB5)
private val DarkTextPrimary = Color(0xFFF2F5F7)
private val DarkTextSecondary = Color(0xFFBCC7D1)
private val DarkTextMuted = Color(0xFFAAB8C4)

private val LeoSettingsDarkColorScheme = darkColorScheme(
    primary = DarkEmerald,
    onPrimary = Color(0xFF051812),
    primaryContainer = Color(0xFF15382D),
    onPrimaryContainer = Color(0xFFB5F7E0),
    secondary = DarkCyan,
    onSecondary = Color(0xFF051A1F),
    background = DarkBgTop,
    onBackground = DarkTextPrimary,
    surface = DarkCardBg,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSubCardBg,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DarkDanger,
)

private val LeoSettingsTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

class AiSettingsActivity : ComponentActivity() {
    private val runtimeStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let(NikoRuntimeState::acceptExternalState)
        }
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceListener() else {
            NikoVoiceSettings.setEnabled(this, false)
            NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "Falta el permiso de micrófono para escuchar LEO.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ContextCompat.registerReceiver(
            this,
            runtimeStateReceiver,
            IntentFilter(NikoRuntimeState.ACTION_RUNTIME_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            MaterialTheme(
                colorScheme = LeoSettingsDarkColorScheme,
                typography = LeoSettingsTypography,
                shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)),
            ) {
                LeoSettingsScreen(
                    onClose = { finish() },
                    onVoiceEnabled = ::setVoiceEnabled,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (NikoVoiceSettings.enabled(this) && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) runCatching {
            startService(UpgradeIdentity.assistantService(this).apply { action = NikoAssistantService.ACTION_HIDE_BUBBLE })
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(runtimeStateReceiver) }
        super.onDestroy()
    }

    private fun setVoiceEnabled(enabled: Boolean) {
        NikoVoiceSettings.setEnabled(this, enabled)
        if (!enabled) {
            stopService(UpgradeIdentity.assistantService(this))
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVoiceListener()
        }
    }

    private fun startVoiceListener() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                UpgradeIdentity.assistantService(this).apply { action = NikoAssistantService.ACTION_START },
            )
        }.onFailure {
            NikoVoiceSettings.setEnabled(this, false)
            NikoRuntimeState.setInput(this, NikoRuntimeState.InputState.ERROR, "No pude iniciar el micrófono. Volvé a abrir LEO.")
        }
    }
}

@Composable
private fun LeoSettingsScreen(
    onClose: () -> Unit,
    onVoiceEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val settingsScroll = rememberScrollState()
    val modelManager = remember { NikoModelManager(context) }
    val ownerVoice = remember { NikoVoiceProfile(context) }
    val offlineKnowledge = remember { LeoFrozenBrainManager(context) }

    var enrolled by remember { mutableStateOf(ownerVoice.isEnrolled) }
    var ownerOnly by remember { mutableStateOf(ownerVoice.ownerOnly) }
    var registering by remember { mutableStateOf(ownerVoice.enrollmentActive) }
    var voiceSamples by remember { mutableStateOf(ownerVoice.enrollmentCount) }
    var voiceFeedback by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { ownerVoice.cancelEnrollment() } }

    var voiceEnabled by remember { mutableStateOf(NikoVoiceSettings.enabled(context)) }
    var voiceStatus by remember { mutableStateOf(NikoRuntimeState.read(context).inputStatus) }
    var outputVoiceStatus by remember { mutableStateOf(NikoRuntimeState.read(context).voiceStatus) }
    var bubbleStatus by remember { mutableStateOf("") }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityConnected by remember { mutableStateOf(NikoAccessibilityService.instance != null) }

    var speakerInstalled by remember { mutableStateOf(modelManager.isInstalled(NikoModelCatalog.speaker)) }
    var whisperInstalled by remember { mutableStateOf(modelManager.isInstalled(NikoModelCatalog.whisperAsr)) }
    var offlineAllowed by remember { mutableStateOf(LeoOfflineKnowledgeSettings.downloadAllowed(context)) }
    var offlineInstalled by remember { mutableStateOf(offlineKnowledge.isInstalled()) }
    var offlineJob by remember { mutableStateOf<Job?>(null) }
    var offlineStatus by remember {
        mutableStateOf(
            if (offlineKnowledge.isInstalled()) "Enciclopedia instalada y disponible sin Internet."
            else "Paquete opcional (~500 MB) aún no instalado.",
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            voiceEnabled = NikoVoiceSettings.enabled(context)
            val snap = NikoRuntimeState.read(context)
            voiceStatus = snap.inputStatus
            outputVoiceStatus = snap.voiceStatus
            enrolled = ownerVoice.isEnrolled
            ownerOnly = ownerVoice.ownerOnly
            registering = ownerVoice.enrollmentActive
            voiceSamples = ownerVoice.enrollmentCount
            overlayGranted = Settings.canDrawOverlays(context)
            accessibilityConnected = NikoAccessibilityService.instance != null
            speakerInstalled = modelManager.isInstalled(NikoModelCatalog.speaker)
            whisperInstalled = modelManager.isInstalled(NikoModelCatalog.whisperAsr)
            offlineInstalled = offlineKnowledge.isInstalled()
            delay(500L)
        }
    }

    LaunchedEffect(Unit) { NikoAiSettings.retireCloudCredentials(context) }

    var initiative by remember { mutableStateOf(NikoAiSettings.companionInitiative(context)) }
    var personality by remember { mutableStateOf(NikoAiSettings.personality(context)) }
    var localFirst by remember { mutableStateOf(NikoAiSettings.localFirst(context)) }
    var autoResearch by remember { mutableStateOf(NikoAiSettings.autoResearch(context)) }
    var learning by remember { mutableStateOf(NikoAiSettings.adaptiveLearning(context)) }
    var behaviorFeedback by remember { mutableStateOf("") }

    val semanticMemory = remember { LeoSemanticMemory.get(context) }
    var semanticStatus by remember { mutableStateOf(semanticMemory.status) }
    var preparingSemantic by remember { mutableStateOf(false) }

    var preparing by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Los modelos acústicos se verifican e instalan localmente en tu teléfono.") }

    var hardwareAuditReport by remember { mutableStateOf("") }
    var showClearMemoryDialog by remember { mutableStateOf(false) }
    var memoryFeedback by remember { mutableStateOf("") }

    fun saveBehavior(notice: String = "Preferencia guardada al instante.") {
        NikoAiSettings.saveBehavior(context, personality, localFirst, autoResearch, learning)
        behaviorFeedback = notice
    }

    fun prepareModel(spec: NikoModelSpec, onReadyAfter: (() -> Unit)? = null) {
        preparing = true
        modelStatus = "Preparando ${spec.id}…"
        scope.launch {
            try {
                val ready = withContext(Dispatchers.IO) {
                    modelManager.ensure(spec) { progress ->
                        scope.launch {
                            if (preparing) {
                                val total = if (progress.totalBytes > 0L) " / ${progress.totalBytes / 1_000_000} MB" else ""
                                modelStatus = "${progress.modelId}: ${progress.downloadedBytes / 1_000_000} MB$total"
                            }
                        }
                    }
                }
                speakerInstalled = modelManager.isInstalled(NikoModelCatalog.speaker)
                whisperInstalled = modelManager.isInstalled(NikoModelCatalog.whisperAsr)
                modelStatus = if (ready) {
                    onReadyAfter?.invoke()
                    when (spec) {
                        NikoModelCatalog.whisperAsr -> "Whisper local instalado y listo. LEO lo usará como segundo oído para transcripciones dudosas."
                        NikoModelCatalog.speaker -> "Motor de huella vocal listo. Ya podés registrar las 4 frases de tu voz."
                        else -> "Modelo preparado para uso 100 % sin Internet."
                    }
                } else {
                    "No se pudo completar la descarga. Comprobá tu conexión y espacio libre."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                modelStatus = "No pude preparar el modelo. Revisá conexión y espacio libre; podés reintentar."
            } finally {
                preparing = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgTop),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(settingsScroll)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Encabezado de ajustes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(onClick = onClose),
                        shape = CircleShape,
                        color = DarkCardBg,
                        border = BorderStroke(1.dp, DarkBorder),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "LEO",
                            color = DarkEmerald,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = "Ajustes",
                            color = DarkTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                StatusPill(
                    text = if (voiceEnabled) "Activo" else "En pausa",
                    active = voiceEnabled,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Guardado automático", color = DarkTextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showGuide = true }) { Text("Guía de uso") }
            }

            // ── SECCIÓN 1: ACTIVACIÓN POR VOZ Y ESCUCHA ──
            DarkSettingsCard(
                icon = Icons.Rounded.Mic,
                title = "Voz y conversación",
                subtitle = "Activación y respuestas por voz.",
            ) {
                DarkSwitchRow(
                    title = "Escuchar la palabra «LEO»",
                    description = "Mantiene la detección local activa para que puedas hablarle sin tocar la pantalla.",
                    checked = voiceEnabled,
                    onCheckedChange = {
                        voiceEnabled = it
                        onVoiceEnabled(it)
                    },
                )

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Iniciar conversaciones",
                    description = "Permite que LEO inicie charlas breves en la pantalla principal y mantenga el turno abierto tras responder.",
                    checked = initiative,
                    onCheckedChange = {
                        initiative = it
                        NikoAiSettings.setCompanionInitiative(context, it)
                    },
                )

                DarkInfoBox(
                    title = "Estado de voz",
                    body = buildString {
                        append(voiceStatus.ifBlank { if (voiceEnabled) "Micrófono listo para escucharte." else "Escucha en pausa." })
                        if (outputVoiceStatus.isNotBlank()) {
                            append("\n• Salida de voz: ")
                            append(outputVoiceStatus)
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            voiceEnabled = true
                            onVoiceEnabled(true)
                            voiceStatus = "Reiniciando motor de micrófono de LEO…"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkEmerald.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = DarkEmerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reiniciar voz", color = DarkEmerald, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            NikoUiModeStore.set(context, NikoUiMode.VOICE_DIAGNOSTICS)
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                    ) {
                        Text("Diagnóstico", color = DarkTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── SECCIÓN 2: RECONOCIMIENTO DE MI VOZ (HUELLA VOCAL) ──
            DarkSettingsCard(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "Mi voz",
                subtitle = "Registrá 4 frases para que LEO te reconozca.",
            ) {
                DarkInfoBox(
                    title = if (enrolled) "Perfil de voz registrado" else "Estado de huella vocal",
                    body = when {
                        registering -> "Grabando muestra $voiceSamples de 4. Hablá cerca del teléfono diciendo una frase de 2 a 6 segundos (por ejemplo: «Leo, quiero que reconozcas mi voz cuando te hablo»)."
                        enrolled -> "Tu huella vocal numérica está guardada en este teléfono. No se almacenan ni envían grabaciones de audio."
                        speakerInstalled -> "Motor de huella vocal instalado. Tocá «Registrar mi voz» para grabar tus 4 muestras."
                        else -> "Al tocar «Registrar mi voz», LEO preparará el motor biométrico local y comenzará el registro."
                    },
                    accentColor = if (enrolled || registering) DarkEmerald else DarkCyan,
                )

                if (registering) {
                    // Barra visual de progreso de las 4 muestras
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(4) { index ->
                            val done = index < voiceSamples
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(
                                        color = if (done) DarkEmerald else DarkBorder,
                                        shape = RoundedCornerShape(100.dp),
                                    ),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            ownerVoice.cancelEnrollment()
                            registering = false
                            voiceSamples = 0
                            voiceFeedback = "Registro de voz cancelado."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkDanger),
                    ) {
                        Text("Cancelar registro", color = DarkDanger, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            voiceFeedback = ""
                            if (speakerInstalled) {
                                ownerVoice.beginEnrollment()
                                registering = true
                                voiceSamples = ownerVoice.enrollmentCount
                                if (!voiceEnabled) {
                                    voiceEnabled = true
                                    onVoiceEnabled(true)
                                }
                                voiceFeedback = "Registro iniciado (0/4). Decí 4 frases empezando con «Leo…»."
                            } else {
                                voiceFeedback = "Descargando e instalando primero el motor de huella vocal…"
                                prepareModel(NikoModelCatalog.speaker) {
                                    ownerVoice.beginEnrollment()
                                    registering = true
                                    voiceSamples = ownerVoice.enrollmentCount
                                    if (!voiceEnabled) {
                                        voiceEnabled = true
                                        onVoiceEnabled(true)
                                    }
                                    voiceFeedback = "Motor listo y registro iniciado (0/4). Decí 4 frases empezando con «Leo…»."
                                }
                            }
                        },
                        enabled = !preparing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkEmerald,
                            contentColor = Color(0xFF061510),
                            disabledContainerColor = DarkBorder,
                            disabledContentColor = DarkTextSecondary,
                        ),
                    ) {
                        Text(
                            text = when {
                                preparing -> "Preparando voz…"
                                enrolled -> "Registrar de nuevo"
                                else -> "Registrar mi voz"
                            },
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }

                if (!speakerInstalled) {
                    OutlinedButton(
                        onClick = { prepareModel(NikoModelCatalog.speaker) },
                        enabled = !preparing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkCyan.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = if (preparing) "Preparando motor…" else "Preparar reconocimiento",
                            color = DarkCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Responder solo a mi voz",
                    description = if (enrolled) "Rechaza órdenes de voz cuyo timbre no coincida con tu perfil registrado."
                    else "Requiere registrar tus 4 frases de voz primero para activarse.",
                    checked = ownerOnly,
                    onCheckedChange = { desired ->
                        if (!enrolled) {
                            voiceFeedback = "Primero tocá «Registrar mi voz ahora» y completá las 4 frases para activar este filtro."
                        } else {
                            ownerVoice.setOwnerOnly(desired)
                            ownerOnly = desired
                            voiceFeedback = if (desired) "Filtro activo: LEO priorizará únicamente tu voz." else "Filtro desactivado: LEO responderá a cualquier voz."
                        }
                    },
                )

                if (enrolled) {
                    OutlinedButton(
                        onClick = {
                            ownerVoice.reset()
                            enrolled = false
                            ownerOnly = false
                            registering = false
                            voiceSamples = 0
                            voiceFeedback = "Perfil de voz eliminado correctamente de este teléfono."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkDanger.copy(alpha = 0.5f)),
                    ) {
                        Text("Eliminar perfil de voz", color = DarkDanger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (preparing) {
                    Text(modelStatus, color = DarkTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }

                if (voiceFeedback.isNotBlank()) {
                    Text(
                        text = voiceFeedback,
                        color = DarkEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── SECCIÓN 3: SEGUNDO OÍDO LOCAL (WHISPER ASR) ──
            DarkSettingsCard(
                icon = Icons.Rounded.GraphicEq,
                title = "Precisión de voz",
                subtitle = "Reconocimiento adicional para frases poco claras.",
            ) {
                DarkInfoBox(
                    title = "Estado de descarga",
                    body = modelStatus,
                    accentColor = if (whisperInstalled) DarkEmerald else DarkCyan,
                )

                OutlinedButton(
                    onClick = { prepareModel(NikoModelCatalog.whisperAsr) },
                    enabled = !preparing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (whisperInstalled) DarkEmerald else DarkCyan),
                ) {
                    Text(
                        text = when {
                            preparing -> "Preparando modelo…"
                            whisperInstalled -> "Verificar reconocimiento"
                            else -> "Descargar reconocimiento"
                        },
                        color = if (whisperInstalled) DarkEmerald else DarkCyan,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }

            // ── SECCIÓN 4: PERSONALIDAD, CONCIENCIA Y APRENDIZAJE ──
            DarkSettingsCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Personalidad e Internet",
                subtitle = "Estilo de respuesta, búsqueda y aprendizaje.",
            ) {
                Text(
                    text = "Estilo de respuesta",
                    color = DarkCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NikoPersonality.entries.forEach { option ->
                        val isSelected = personality == option
                        val detail = when (option) {
                            NikoPersonality.WARM -> "Cercano, atento y práctico."
                            NikoPersonality.DIRECT -> "Respuestas al grano, concisas, críticas y sin rodeos."
                            NikoPersonality.WITTY -> "Respuestas con ingenio y un toque de humor."
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    personality = option
                                    saveBehavior("Personalidad cambiada a: ${option.label}")
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) DarkEmerald.copy(alpha = 0.12f) else DarkSubCardBg,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) DarkEmerald else DarkBorder,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        personality = option
                                        saveBehavior("Personalidad cambiada a: ${option.label}")
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = DarkEmerald,
                                        unselectedColor = DarkTextSecondary,
                                    ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        color = DarkTextPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = detail,
                                        color = DarkTextSecondary,
                                        fontSize = 14.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Preferir respuestas locales",
                    description = "Intenta responder primero con lo disponible en el teléfono.",
                    checked = localFirst,
                    onCheckedChange = {
                        localFirst = it
                        saveBehavior("Prioridad local ${if (it) "activada" else "desactivada"}.")
                    },
                )

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Buscar en Internet",
                    description = "Busca, lee y sintetiza artículos públicos cuando preguntás hechos o noticias actuales.",
                    checked = autoResearch,
                    onCheckedChange = {
                        autoResearch = it
                        saveBehavior("Investigación web ${if (it) "activada" else "desactivada"}.")
                    },
                )

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Aprender de mis correcciones",
                    description = "Usa tus correcciones para mejorar futuras respuestas.",
                    checked = learning,
                    onCheckedChange = {
                        learning = it
                        saveBehavior("Aprendizaje continuo ${if (it) "activado" else "desactivado"}.")
                    },
                )

                if (behaviorFeedback.isNotBlank()) {
                    Text(
                        text = "✓ $behaviorFeedback",
                        color = DarkEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── SECCIÓN 5: BURBUJA FLOTANTE, PERMISOS Y HARDWARE ──
            DarkSettingsCard(
                icon = Icons.Rounded.SettingsSuggest,
                title = "Permisos y teléfono",
                subtitle = "Burbuja, acceso a otras apps y sensores.",
            ) {
                Button(
                    onClick = {
                        // Limpia siempre las coordenadas en SharedPreferences y además notifica al servicio en vivo
                        runCatching {
                            context.getSharedPreferences(UpgradeIdentity.bubblePreferences, Context.MODE_PRIVATE)
                                .edit()
                                .remove("bubble_x")
                                .remove("bubble_y")
                                .apply()
                        }
                        runCatching {
                            context.startService(
                                UpgradeIdentity.assistantService(context).apply {
                                    action = NikoAssistantService.ACTION_RESET_BUBBLE
                                },
                            )
                        }
                        bubbleStatus = "✓ Posición restaurada. La burbuja flotante volvió a su ubicación original."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkEmerald,
                        contentColor = Color(0xFF061510),
                    ),
                ) {
                    Text("Restablecer burbuja", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                if (bubbleStatus.isNotBlank()) {
                    Text(
                        text = bubbleStatus,
                        color = DarkEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (overlayGranted) DarkEmerald else DarkAmber),
                    ) {
                        Text(
                            text = if (overlayGranted) "Burbuja activa" else "Permitir burbuja",
                            color = if (overlayGranted) DarkEmerald else DarkAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (accessibilityConnected) DarkEmerald else DarkCyan),
                    ) {
                        Text(
                            text = if (accessibilityConnected) "Acceso activo" else "Accesibilidad",
                            color = if (accessibilityConnected) DarkEmerald else DarkCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val snap = LeoHardwareProtocolRunner(context).captureSensorSnapshot()
                        hardwareAuditReport = "Batería: ${snap.batteryPercent}% (${"%.1f".format(snap.batteryTempC)} °C) · Luz ambiente: ${snap.ambientLux.toInt()} lx · Acelerómetro: ${"%.2f".format(snap.accelMagnitudeG)} g"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                ) {
                    Text(
                        text = "Consultar sensores",
                        color = DarkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (hardwareAuditReport.isNotBlank()) {
                    DarkInfoBox(
                        title = "Reporte de hardware en tiempo real",
                        body = hardwareAuditReport,
                        accentColor = DarkEmerald,
                    )
                }
            }

            // ── SECCIÓN 6: CEREBRO OFFLINE, MODELOS LLM Y MEMORIA SEMÁNTICA ──
            DarkSettingsCard(
                icon = Icons.Rounded.Memory,
                title = "Descargas",
                subtitle = "Modelos y conocimiento para usar sin Internet.",
            ) {
                // Panel de modelos GGUF Qwen3
                LeoLocalModelPanel()

                // Sub-tarjeta: Enciclopedia Offline
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSubCardBg,
                    border = BorderStroke(1.dp, DarkBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Enciclopedia · 500 MB aprox.",
                                modifier = Modifier.weight(1f),
                                color = DarkCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.sp,
                            )
                            StatusPill(
                                text = if (offlineInstalled) "Instalada" else "Opcional",
                                active = offlineInstalled,
                            )
                        }
                        Text(
                            text = "Base de conocimiento local para responder cultura general, ciencia e historia cuando estás sin datos ni Wi-Fi.",
                            color = DarkTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text = offlineStatus,
                            color = DarkTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    LeoOfflineKnowledgeSettings.allowDownload(context, true)
                                    offlineAllowed = true
                                    offlineStatus = "Preparando descarga de enciclopedia offline…"
                                    offlineJob = scope.launch {
                                        try {
                                            offlineKnowledge.ensureInstalled(checkForUpdates = true) { progress ->
                                                scope.launch {
                                                    val mb = if (progress.totalBytes > 0) " · ${progress.downloadedBytes / 1_000_000}/${progress.totalBytes / 1_000_000} MB" else ""
                                                    offlineStatus = progress.message + mb
                                                }
                                            }
                                            offlineInstalled = offlineKnowledge.isInstalled()
                                        } catch (cancelled: CancellationException) {
                                            offlineStatus = "Descarga detenida. Podés reintentar desde esta pantalla."
                                            throw cancelled
                                        } catch (_: Exception) {
                                            offlineStatus = "No pude completar la descarga. Revisá tu conexión y espacio libre."
                                        } finally {
                                            offlineJob = null
                                        }
                                    }
                                },
                                enabled = offlineJob == null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkEmerald,
                                    contentColor = Color(0xFF061510),
                                    disabledContainerColor = DarkBorder,
                                    disabledContentColor = DarkTextSecondary,
                                ),
                            ) {
                                Text(
                                    text = if (offlineJob != null) "Descargando…" else if (offlineInstalled) "Buscar actualización" else "Descargar",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                            }

                            if (offlineAllowed || offlineJob != null) {
                                OutlinedButton(
                                    onClick = {
                                        LeoOfflineKnowledgeSettings.allowDownload(context, false)
                                        offlineAllowed = false
                                        offlineJob?.cancel()
                                        offlineStatus = "Descargas automáticas detenidas. El conocimiento ya instalado se conserva."
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, DarkDanger),
                                ) {
                                    Text("Detener", color = DarkDanger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // Sub-tarjeta: Memoria Semántica
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSubCardBg,
                    border = BorderStroke(1.dp, DarkBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Memoria por significado · 138 MB",
                            color = DarkCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = "Relaciona tus notas y conversaciones por su significado.",
                            color = DarkTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text = semanticStatus,
                            color = DarkTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(
                            onClick = {
                                preparingSemantic = true
                                scope.launch {
                                    try {
                                        semanticMemory.install { downloaded, total ->
                                            scope.launch {
                                                semanticStatus = "Instalando memoria semántica: ${downloaded / 1_000_000} / ${total / 1_000_000} MB"
                                            }
                                        }
                                        semanticStatus = semanticMemory.status
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        semanticStatus = "No se completó: ${error.message}. Podés reintentar."
                                    } finally {
                                        preparingSemantic = false
                                    }
                                }
                            },
                            enabled = !preparingSemantic,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkEmerald),
                        ) {
                            Text(
                                text = if (preparingSemantic) "Preparando memoria…" else "Preparar memoria",
                                color = DarkEmerald,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // ── SECCIÓN 7: PRIVACIDAD, WEB Y MEMORIA PERSONAL ──
            DarkSettingsCard(
                icon = Icons.Rounded.Security,
                title = "Privacidad",
                subtitle = "Datos personales y uso de Internet.",
            ) {
                DarkInfoBox(
                    title = "Consultas en Internet",
                    body = "Las búsquedas se envían a proveedores y páginas públicas. Las respuestas incluyen sus fuentes.",
                )

                OutlinedButton(
                    onClick = { showClearMemoryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkDanger.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = DarkDanger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Borrar memoria personal",
                        color = DarkDanger,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }

                if (memoryFeedback.isNotBlank()) {
                    Text(
                        text = memoryFeedback,
                        color = DarkEmerald,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── BOTÓN PRINCIPAL PARA CERRAR Y VOLVER A LEO ──
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkEmerald,
                    contentColor = Color(0xFF051812),
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Volver a LEO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showGuide) LeoUserGuideDialog(onClose = { showGuide = false })

    if (showClearMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoryDialog = false },
            containerColor = DarkCardBg,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("¿Borrar memoria personal de LEO?", fontWeight = FontWeight.Medium) },
            text = {
                Text(
                    "Se eliminarán tus recuerdos personales, notas rápidas, lecciones autodidactas y línea de tiempo en este teléfono. Los modelos descargados y tu huella de voz se conservarán.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearMemoryDialog = false
                        memoryFeedback = "Borrando memoria…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val personal = runCatching { NikoMemory(context).clearAll() }
                                val sovereign = runCatching { LeoSovereignStore(context.filesDir).clear() }
                                personal.isSuccess && sovereign.isSuccess
                            }
                            memoryFeedback = if (result) "Memoria personal y aprendizaje reiniciados."
                                else "No se pudo borrar toda la memoria. Revisá el almacenamiento y reintentá."
                        }
                    },
                ) {
                    Text("Borrar memoria", color = DarkDanger, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoryDialog = false }) {
                    Text("Cancelar", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
internal fun DarkSettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var visited by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkCardBg,
        border = BorderStroke(1.dp, DarkBorder),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" }
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Contraer" else "Expandir") {
                        expanded = !expanded
                        visited = true
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(icon, contentDescription = null, tint = DarkEmerald, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = DarkTextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, color = DarkTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = DarkTextSecondary,
                )
            }
            if (visited) {
                Layout(content = {
                    Column(
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        HorizontalDivider(color = DarkBorder)
                        content()
                    }
                }) { measurables, constraints ->
                    // Unplaced content is neither visible nor accessible, but its jobs survive.
                    if (expanded) {
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    } else {
                        layout(constraints.minWidth, 0) {}
                    }
                }
            }
        }
    }
}

@Composable
internal fun DarkSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = DarkTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                color = DarkTextSecondary,
                fontSize = 14.sp,
                lineHeight = 16.5.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF041711),
                checkedTrackColor = DarkEmerald,
                uncheckedThumbColor = DarkTextSecondary,
                uncheckedTrackColor = DarkSubCardBg,
                uncheckedBorderColor = DarkBorder,
            ),
        )
    }
}

@Composable
private fun DarkInfoBox(
    title: String,
    body: String,
    accentColor: Color = DarkCyan,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = DarkSubCardBg,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
            Text(
                text = body,
                color = DarkTextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean,
) {
    val tint = if (active) DarkEmerald else DarkTextMuted
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (active) DarkEmerald.copy(alpha = 0.14f) else DarkSubCardBg,
        border = BorderStroke(1.dp, if (active) DarkEmerald.copy(alpha = 0.45f) else DarkBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(tint, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = if (active) DarkEmerald else DarkTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
        }
    }
}
