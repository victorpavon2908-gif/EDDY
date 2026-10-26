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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveable
import com.niko.assistant.ui.LeoUserGuideDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// Paleta exclusiva en Modo Oscuro para el template de Ajustes de LEO
private val DarkBgTop = Color(0xFF070C0B)
private val DarkBgBottom = Color(0xFF0C1412)
private val DarkCardBg = Color(0xFF121C19)
private val DarkSubCardBg = Color(0xFF172420)
private val DarkBorder = Color(0xFF233831)
private val DarkEmerald = Color(0xFF36E5B2)
private val DarkCyan = Color(0xFF55E8FF)
private val DarkAmber = Color(0xFFFFB45C)
private val DarkDanger = Color(0xFFFF6B81)
private val DarkTextPrimary = Color(0xFFF2F8F6)
private val DarkTextSecondary = Color(0xFF9EB4AD)
private val DarkTextMuted = Color(0xFF6E857E)

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
            MaterialTheme(colorScheme = LeoSettingsDarkColorScheme) {
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
    val sectionNames = listOf("Voz", "Mi voz", "Reconocimiento", "Personalidad e Internet", "Permisos", "Descargas offline", "Privacidad")
    val sectionAnchors = remember { List(7) { BringIntoViewRequester() } }
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
            .background(Brush.verticalGradient(listOf(DarkBgTop, DarkBgBottom))),
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
            // ── ENCABEZADO SUPERIOR ELEGANTE ──
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
                            text = "EDDY / LEO · AJUSTES",
                            color = DarkEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )
                        Text(
                            text = "Tu asistente, a tu manera",
                            color = DarkTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                StatusPill(
                    text = if (voiceEnabled) "ACTIVO" else "EN PAUSA",
                    active = voiceEnabled,
                )
            }

            // ── BARRA DE ESTADO RÁPIDO (3 INDICADORES) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Escucha",
                    value = if (voiceEnabled) "Encendida" else "Pausada",
                    active = voiceEnabled,
                )
                QuickMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Mi Voz",
                    value = if (enrolled) "Registrada" else "Abierta",
                    active = enrolled,
                )
                QuickMetricChip(
                    modifier = Modifier.weight(1f),
                    label = "Pantalla",
                    value = if (accessibilityConnected) "Conectada" else "Básica",
                    active = accessibilityConnected,
                )
            }

            DarkSettingsCard(
                icon = Icons.Rounded.SettingsSuggest,
                badge = "EMPEZÁ AQUÍ",
                title = "Configuración rápida",
                subtitle = "Los cambios se guardan al instante. Elegí una categoría para ir directamente a sus controles.",
            ) {
                Button(onClick = { showGuide = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Guía para nuevos usuarios · disponible sin conexión")
                }
                sectionNames.chunked(2).forEachIndexed { row, labels ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        labels.forEachIndexed { column, label ->
                            OutlinedButton(
                                onClick = { scope.launch { sectionAnchors[row * 2 + column].bringIntoView() } },
                                modifier = Modifier.weight(1f),
                            ) { Text(label) }
                        }
                        if (labels.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── SECCIÓN 1: ACTIVACIÓN POR VOZ Y ESCUCHA ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[0]))
            DarkSettingsCard(
                icon = Icons.Rounded.Mic,
                badge = "VOZ EN VIVO",
                title = "Activación por Voz y Conversación",
                subtitle = "Decí «LEO» para despertar al asistente. Tras cada respuesta, LEO mantiene una ventana de 12 segundos para seguir charlando sin repetir su nombre.",
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
                    title = "Conversación espontánea (Modo Eddy)",
                    description = "Permite que LEO inicie charlas breves en la pantalla principal y mantenga el turno abierto tras responder.",
                    checked = initiative,
                    onCheckedChange = {
                        initiative = it
                        NikoAiSettings.setCompanionInitiative(context, it)
                    },
                )

                DarkInfoBox(
                    title = "ESTADO DEL MICRÓFONO Y ALTAVOZ",
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
                        Text("REINICIAR VOZ", color = DarkEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                        Text("DIAGNÓSTICO DE VOZ", color = DarkTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── SECCIÓN 2: RECONOCIMIENTO DE MI VOZ (HUELLA VOCAL) ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[1]))
            DarkSettingsCard(
                icon = Icons.Rounded.RecordVoiceOver,
                badge = if (enrolled) "HUELLA ACTIVA" else "SEGURIDAD VOCAL",
                title = "Reconocimiento de Mi Voz",
                subtitle = "Registrá tu timbre de voz con 4 frases cortas para que LEO te identifique y descarte voces lejanas o de desconocidos.",
            ) {
                DarkInfoBox(
                    title = if (enrolled) "✓ PERFIL DE VOZ REGISTRADO" else "ESTADO DE HUELLA VOCAL",
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
                        Text("CANCELAR REGISTRO EN CURSO", color = DarkDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                preparing -> "PREPARANDO MOTOR DE VOZ…"
                                enrolled -> "VOLVER A REGISTRAR MI VOZ"
                                else -> "REGISTRAR MI VOZ AHORA"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
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
                            text = if (preparing) "PREPARANDO MOTOR…" else "DESCARGAR MOTOR DE HUELLA VOCAL",
                            color = DarkCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Responder exclusivamente a mi voz",
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
                        Text("BORRAR MI PERFIL DE VOZ", color = DarkDanger, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (voiceFeedback.isNotBlank()) {
                    Text(
                        text = voiceFeedback,
                        color = DarkEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── SECCIÓN 3: SEGUNDO OÍDO LOCAL (WHISPER ASR) ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[2]))
            DarkSettingsCard(
                icon = Icons.Rounded.GraphicEq,
                badge = if (whisperInstalled) "INSTALADO ✓" else "PRECISIÓN ACÚSTICA",
                title = "Reconocimiento Avanzado (Whisper Local)",
                subtitle = "Canary transcribe primero para responder rápido. Whisper Multilingual INT8 hace una segunda pasada automática cuando detecta ruido o frases dudosas.",
            ) {
                DarkInfoBox(
                    title = "ESTADO DE MODELOS ACÚSTICOS",
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
                            preparing -> "PREPARANDO MODELO…"
                            whisperInstalled -> "VERIFICAR / REINSTALAR WHISPER LOCAL (LISTO ✓)"
                            else -> "PREPARAR WHISPER LOCAL (SEGUNDO OÍDO)"
                        },
                        color = if (whisperInstalled) DarkEmerald else DarkCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }

            // ── SECCIÓN 4: PERSONALIDAD, CONCIENCIA Y APRENDIZAJE ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[3]))
            DarkSettingsCard(
                icon = Icons.Rounded.AutoAwesome,
                badge = "ALMA BIÓNICA",
                title = "Personalidad y Aprendizaje Soberano",
                subtitle = "Elegí el carácter de LEO y cómo evoluciona contigo. Todos los cambios se aplican en tiempo real.",
            ) {
                Text(
                    text = "ESTILO DE PERSONALIDAD",
                    color = DarkCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NikoPersonality.entries.forEach { option ->
                        val isSelected = personality == option
                        val detail = when (option) {
                            NikoPersonality.WARM -> "Cálido, empático, compañero atento y práctico en todo momento."
                            NikoPersonality.DIRECT -> "Respuestas al grano, concisas, críticas y sin rodeos."
                            NikoPersonality.WITTY -> "Ingenio biónico estilo Eddy (Lab Rats), humor chispeante, conciencia viva y lealtad total."
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
                                        text = if (option == NikoPersonality.WITTY) "${option.label} · Espíritu Eddy" else option.label,
                                        color = DarkTextPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = detail,
                                        color = DarkTextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Priorizar conversación y cerebro local",
                    description = "Resuelve primero con el núcleo neural interno del teléfono sin latencia.",
                    checked = localFirst,
                    onCheckedChange = {
                        localFirst = it
                        saveBehavior("Prioridad local ${if (it) "activada" else "desactivada"}.")
                    },
                )

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Investigar dudas en Internet automáticamente",
                    description = "Busca, lee y sintetiza artículos públicos cuando preguntás hechos o noticias actuales.",
                    checked = autoResearch,
                    onCheckedChange = {
                        autoResearch = it
                        saveBehavior("Investigación web ${if (it) "activada" else "desactivada"}.")
                    },
                )

                HorizontalDivider(color = DarkBorder)

                DarkSwitchRow(
                    title = "Aprendizaje continuo y autodidacta",
                    description = "Aprende de tus correcciones («eso está mal», «eso me sirvió», «cuando diga X hacé Y») y protege lo aprendido con pruebas de regresión.",
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── SECCIÓN 5: BURBUJA FLOTANTE, PERMISOS Y HARDWARE ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[4]))
            DarkSettingsCard(
                icon = Icons.Rounded.SettingsSuggest,
                badge = "SISTEMA Y PANTALLA",
                title = "Burbuja Flotante, Visión y Hardware",
                subtitle = "Controlá la ventana flotante de LEO, los permisos de visión sobre otras apps y la auditoría física de tu teléfono.",
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
                    Text("RESTAURAR POSICIÓN DE LA BURBUJA", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }

                if (bubbleStatus.isNotBlank()) {
                    Text(
                        text = bubbleStatus,
                        color = DarkEmerald,
                        fontSize = 12.sp,
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
                            text = if (overlayGranted) "BURBUJA: ACTIVA ✓" else "ACTIVAR BURBUJA",
                            color = if (overlayGranted) DarkEmerald else DarkAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
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
                            text = if (accessibilityConnected) "VISIÓN UI: ACTIVA ✓" else "ACTIVAR VISIÓN UI",
                            color = if (accessibilityConnected) DarkEmerald else DarkCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
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
                        text = "EJECUTAR AUDITORÍA FÍSICA DEL TELÉFONO",
                        color = DarkTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (hardwareAuditReport.isNotBlank()) {
                    DarkInfoBox(
                        title = "REPORTE DE HARDWARE EN TIEMPO REAL",
                        body = hardwareAuditReport,
                        accentColor = DarkEmerald,
                    )
                }
            }

            // ── SECCIÓN 6: CEREBRO OFFLINE, MODELOS LLM Y MEMORIA SEMÁNTICA ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[5]))
            DarkSettingsCard(
                icon = Icons.Rounded.Memory,
                badge = "100 % SOBERANO",
                title = "Modelos Locales y Conocimiento Offline",
                subtitle = "Gestioná los motores opcionales sin conexión de LEO. Ninguno depende de GPT, Gemini ni servidores externos.",
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
                                text = "ENCICLOPEDIA OFFLINE (~500 MB)",
                                color = DarkCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.0.sp,
                            )
                            StatusPill(
                                text = if (offlineInstalled) "INSTALADA" else "OPCIONAL",
                                active = offlineInstalled,
                            )
                        }
                        Text(
                            text = "Base de conocimiento local para responder cultura general, ciencia e historia cuando estás sin datos ni Wi-Fi.",
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text = offlineStatus,
                            color = DarkTextPrimary,
                            fontSize = 12.sp,
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
                                    text = if (offlineJob != null) "DESCARGANDO…" else if (offlineInstalled) "BUSCAR ACTUALIZACIÓN" else "DESCARGAR ENCICLOPEDIA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
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
                                    Text("DETENER", color = DarkDanger, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                            text = "MEMORIA SEMÁNTICA VECTORIAL (138 MB)",
                            color = DarkCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp,
                        )
                        Text(
                            text = "Codificador multilingüe local que conecta tus recuerdos, notas y conversaciones por significado profundo.",
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            text = semanticStatus,
                            color = DarkTextPrimary,
                            fontSize = 12.sp,
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
                                text = if (preparingSemantic) "PREPARANDO MEMORIA SEMÁNTICA…" else "PREPARAR / VERIFICAR MEMORIA SEMÁNTICA",
                                color = DarkEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // ── SECCIÓN 7: PRIVACIDAD, WEB Y MEMORIA PERSONAL ──
            Spacer(Modifier.height(1.dp).bringIntoViewRequester(sectionAnchors[6]))
            DarkSettingsCard(
                icon = Icons.Rounded.Security,
                badge = "TUS DATOS",
                title = "Investigación Web y Privacidad Soberana",
                subtitle = "La memoria personal se guarda en este teléfono. Las consultas web se envían a proveedores y páginas públicas.",
            ) {
                DarkInfoBox(
                    title = "BÚSQUEDA WEB Y FUENTES",
                    body = "Cuando activás la investigación web, LEO consulta y sintetiza artículos públicos citando las fuentes reales sin usar llaves de API ni cuentas externas.",
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
                        text = "BORRAR MEMORIA PERSONAL Y APRENDIZAJE",
                        color = DarkDanger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }

                if (memoryFeedback.isNotBlank()) {
                    Text(
                        text = memoryFeedback,
                        color = DarkEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── BOTÓN PRINCIPAL PARA CERRAR Y VOLVER A LEO ──
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkEmerald,
                    contentColor = Color(0xFF051812),
                ),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "LISTO · VOLVER A LEO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp,
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
            title = { Text("¿Borrar memoria personal de LEO?", fontWeight = FontWeight.Bold) },
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
                    Text("SÍ, BORRAR MEMORIA", color = DarkDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoryDialog = false }) {
                    Text("CANCELAR", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun DarkSettingsCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DarkCardBg,
        border = BorderStroke(1.dp, DarkBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(DarkEmerald.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = DarkEmerald,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = badge,
                            color = DarkCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = title,
                            color = DarkTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                text = subtitle,
                color = DarkTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            content()
        }
    }
}

@Composable
private fun DarkSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
                fontSize = 12.sp,
                lineHeight = 16.5.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            )
            Text(
                text = body,
                color = DarkTextPrimary,
                fontSize = 12.5.sp,
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
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun QuickMetricChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    active: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DarkCardBg,
        border = BorderStroke(1.dp, if (active) DarkEmerald.copy(alpha = 0.35f) else DarkBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = label.uppercase(),
                color = DarkTextMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = if (active) DarkEmerald else DarkTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
