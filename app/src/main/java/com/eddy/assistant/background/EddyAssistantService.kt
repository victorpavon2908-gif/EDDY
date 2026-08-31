package com.eddy.assistant.background

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eddy.assistant.EddyWakeActivity
import com.eddy.assistant.MainActivity
import com.eddy.assistant.R
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.ai.EddyAiClient
import com.eddy.assistant.ai.EddyAiReply
import com.eddy.assistant.ai.EddyFallbackConversation
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.EddyMathEngine
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.localai.EddyDeviceProfile
import com.eddy.assistant.localai.EddyLocalLlm
import com.eddy.assistant.localai.EddyModelManager
import com.eddy.assistant.localai.EddyModelProgress
import com.eddy.assistant.localai.EddyVoiceProfile
import com.eddy.assistant.memory.EddyMemory
import com.eddy.assistant.programming.EddyCodeAgent
import com.eddy.assistant.proactive.EddyProactiveScheduler
import com.eddy.assistant.smarthome.LocalSmartHomeClient
import com.eddy.assistant.voice.EddyLocalVoiceEngine
import com.eddy.assistant.voice.EddyNeuralTextToSpeech
import com.eddy.assistant.voice.EddySpeechRecognizer
import com.eddy.assistant.voice.EddyTextToSpeech
import com.eddy.assistant.voice.WakeResult
import com.eddy.assistant.voice.WakeWordGate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EddyAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var brain: LocalBrain
    private lateinit var executor: ActionExecutor
    private lateinit var smartHome: LocalSmartHomeClient
    private lateinit var memory: EddyMemory
    private lateinit var wakeGate: WakeWordGate
    private lateinit var webClient: EddyAiClient
    private lateinit var fallbackConversation: EddyFallbackConversation
    private lateinit var proactiveScheduler: EddyProactiveScheduler
    private lateinit var modelManager: EddyModelManager
    private lateinit var deviceProfile: EddyDeviceProfile
    private lateinit var ownerVoice: EddyVoiceProfile
    private lateinit var localLlm: EddyLocalLlm
    private lateinit var codeAgent: EddyCodeAgent
    private var localVoice: EddyLocalVoiceEngine? = null
    private var localVoiceActive = false
    private lateinit var compatibilityRecognizer: EddySpeechRecognizer
    private lateinit var platformTts: EddyTextToSpeech
    private lateinit var neuralTts: EddyNeuralTextToSpeech

    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var screenReceiverRegistered = false
    private var lastPartialWakeAt = 0L
    private var pendingWakeAck: Job? = null
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val bubblePrefs by lazy { getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE) }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { acquireCpuWakeLock(); rearmCompatibilityRecognizer(500L) }
                Intent.ACTION_SCREEN_ON -> rearmCompatibilityRecognizer(250L)
                Intent.ACTION_USER_PRESENT -> rearmCompatibilityRecognizer(150L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        brain = LocalBrain()
        executor = ActionExecutor(applicationContext)
        smartHome = LocalSmartHomeClient(applicationContext)
        memory = EddyMemory(applicationContext)
        wakeGate = WakeWordGate()
        webClient = EddyAiClient(applicationContext)
        fallbackConversation = EddyFallbackConversation()
        proactiveScheduler = EddyProactiveScheduler(applicationContext, memory)
        modelManager = EddyModelManager(applicationContext)
        deviceProfile = EddyDeviceProfile.detect(applicationContext)
        ownerVoice = EddyVoiceProfile(applicationContext)
        localLlm = EddyLocalLlm(applicationContext, modelManager)
        codeAgent = EddyCodeAgent(applicationContext)

        compatibilityRecognizer = EddySpeechRecognizer(
            context = applicationContext,
            onListeningChanged = { recognizerOpen ->
                if (!localVoiceActive) {
                    isListening = recognizerOpen && wakeGate.isArmed()
                    updateVisualState()
                }
            },
            onPartialResult = { partial ->
                if (!localVoiceActive) handlePartialWakeWord(partial)
            },
            onResult = { raw -> if (!localVoiceActive) handleRecognition(raw) },
            onError = { error ->
                if (!localVoiceActive) {
                    isThinking = false
                    if (wakeGate.isArmed()) {
                        EddyRuntimeState.setResponse(applicationContext, "Te escucho. Decime qué querés que haga.")
                    } else if (!error.startsWith("No te pude escuchar")) {
                        EddyRuntimeState.setResponse(applicationContext, error)
                    }
                    updateVisualState()
                }
            },
        )

        platformTts = EddyTextToSpeech(
            context = applicationContext,
            onReady = { ready -> EddyRuntimeState.setVoiceReady(applicationContext, ready) },
            onSpeakingChanged = ::onSpeakingChanged,
        )
        neuralTts = EddyNeuralTextToSpeech(
            models = modelManager,
            profile = deviceProfile,
            onSpeakingChanged = ::onSpeakingChanged,
        )

        acquireCpuWakeLock()
        registerScreenStateReceiver()
        EddyRuntimeState.setRunning(applicationContext, true)
        EddyRuntimeState.setResponse(applicationContext, "EDDY iniciando escucha privada…")
        createNotificationChannels()
        startAsForeground()

        if (!hasMicrophonePermission()) {
            EddyRuntimeState.setResponse(applicationContext, "Abrí EDDY y concedé el permiso de micrófono para mantenerme activo.")
            return
        }

        if (!startLocalVoiceIfReady()) startCompatibilityListening("Decí EDDY cuando me necesités.")
        startModelBootstrap()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
        }
        if (!localVoiceActive && hasMicrophonePermission() && !isSpeaking && !isThinking) compatibilityRecognizer.resume()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pendingWakeAck?.cancel()
        pendingWakeAck = null
        bubbleParams?.let(::saveBubblePosition)
        hideBubble()
        unregisterScreenStateReceiver()
        localVoice?.stop()
        compatibilityRecognizer.destroy()
        neuralTts.shutdown()
        platformTts.shutdown()
        localLlm.release()
        releaseCpuWakeLock()
        serviceScope.cancel()
        EddyRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    private fun startModelBootstrap() {
        serviceScope.launch(Dispatchers.IO) {
            modelManager.ensureRecommended(deviceProfile) { progress ->
                if (progress.state == EddyModelProgress.State.DOWNLOADING) {
                    val percent = if (progress.totalBytes > 0) (progress.downloadedBytes * 100 / progress.totalBytes).coerceIn(0, 100) else null
                    serviceScope.launch {
                        EddyRuntimeState.setResponse(applicationContext, "Preparando núcleo local: ${progress.modelId}${percent?.let { " $it%" }.orEmpty()}")
                    }
                }
            }
            withContext(Dispatchers.Main) {
                if (!localVoiceActive && modelManager.coreReady()) {
                    val started = startLocalVoiceIfReady()
                    if (!started) startCompatibilityListening("Modo de escucha compatible activo. Decí EDDY cuando me necesités.")
                }
                if (localVoiceActive) {
                    val tier = deviceProfile.tier.name.lowercase()
                    val llm = if (localLlm.isAvailable) " · cerebro generativo local listo" else ""
                    EddyRuntimeState.setResponse(applicationContext, "Núcleo privado activo · perfil $tier$llm · decí EDDY")
                }
            }
        }
    }

    private fun startLocalVoiceIfReady(): Boolean {
        if (localVoiceActive || !modelManager.coreReady() || !hasMicrophonePermission()) return localVoiceActive
        val engine = EddyLocalVoiceEngine(
            context = applicationContext,
            models = modelManager,
            profile = deviceProfile,
            ownerVoice = ownerVoice,
            onState = { voiceState -> serviceScope.launch {
                when (voiceState) {
                    EddyLocalVoiceEngine.State.PASSIVE -> { isListening = false; if (!isSpeaking && !isThinking) EddyRuntimeState.setState(applicationContext, EddyRuntimeState.State.IDLE) }
                    EddyLocalVoiceEngine.State.VERIFYING, EddyLocalVoiceEngine.State.PROCESSING -> { isThinking = true; updateVisualState() }
                    EddyLocalVoiceEngine.State.ACTIVE -> { isThinking = false; isListening = true; updateVisualState() }
                    EddyLocalVoiceEngine.State.SPEAKING -> { isListening = false; isSpeaking = true; updateVisualState() }
                    EddyLocalVoiceEngine.State.STOPPED -> Unit
                }
            }},
            onWake = { _, enrolled -> serviceScope.launch {
                EddyRuntimeState.setHeard(applicationContext, "EDDY")
                val profileText = if (enrolled) "voz verificada" else "aprendiendo tu voz"
                EddyRuntimeState.setResponse(applicationContext, "Te escucho · $profileText")
                revealEddyOnLockScreen()
                scheduleWakeAcknowledgement()
            }},
            onCommandSpeechStarted = {
                pendingWakeAck?.cancel()
                pendingWakeAck = null
            },
            onCommand = { text -> serviceScope.launch {
                pendingWakeAck?.cancel()
                pendingWakeAck = null
                EddyRuntimeState.setHeard(applicationContext, text)
                revealEddyOnLockScreen()
                isListening = false; isThinking = true; updateVisualState()
                try { handleCommand(text) } finally { isThinking = false; updateVisualState() }
            }},
            onUnauthorizedVoice = {},
            onError = { error -> serviceScope.launch { fallbackToCompatibilityRecognizer(error) } },
        )

        return if (engine.start()) {
            localVoice = engine
            localVoiceActive = true
            compatibilityRecognizer.stopContinuous()
            isListening = false
            EddyRuntimeState.setResponse(applicationContext, "Núcleo privado activo. Decí EDDY cuando me necesités.")
            true
        } else false
    }

    private fun scheduleWakeAcknowledgement() {
        pendingWakeAck?.cancel()
        pendingWakeAck = serviceScope.launch {
            delay(WAKE_ACK_DELAY_MS)
            if (localVoiceActive && isListening && !isThinking && !isSpeaking) speakResponse("Ajá.")
            pendingWakeAck = null
        }
    }

    private fun fallbackToCompatibilityRecognizer(error: String) {
        pendingWakeAck?.cancel()
        pendingWakeAck = null
        localVoice?.stop()
        localVoice = null
        localVoiceActive = false
        isListening = false
        isThinking = false
        if (hasMicrophonePermission() && !isSpeaking) {
            compatibilityRecognizer.startContinuous()
            compatibilityRecognizer.resume()
        }
        EddyRuntimeState.setResponse(applicationContext, "Escucha recuperada en modo compatible. Decí EDDY.")
        updateVisualState()
    }

    private fun startCompatibilityListening(message: String) {
        if (!hasMicrophonePermission() || isSpeaking) return
        localVoiceActive = false
        isListening = false
        compatibilityRecognizer.startContinuous()
        compatibilityRecognizer.resume()
        isThinking = false
        EddyRuntimeState.setResponse(applicationContext, message)
        updateVisualState()
    }

    private fun handlePartialWakeWord(partial: String) {
        if (!wakeGate.hasWakeWord(partial)) return
        val now = System.currentTimeMillis()
        if (now - lastPartialWakeAt < PARTIAL_WAKE_DEBOUNCE_MS) return
        lastPartialWakeAt = now
        wakeGate.arm(now)
        isListening = true
        EddyRuntimeState.setHeard(applicationContext, "EDDY")
        EddyRuntimeState.setResponse(applicationContext, "Te escucho. Decime qué querés que haga.")
        revealEddyOnLockScreen()
        updateVisualState()
    }

    private fun handleRecognition(raw: String) {
        when (val wakeResult = wakeGate.consume(raw)) {
            WakeResult.Ignored -> {
                isListening = false
                EddyRuntimeState.setHeard(applicationContext, "")
                updateVisualState()
                compatibilityRecognizer.resume()
            }
            WakeResult.Activated -> {
                isListening = true
                EddyRuntimeState.setHeard(applicationContext, "EDDY")
                EddyRuntimeState.setResponse(applicationContext, "Ajá.")
                revealEddyOnLockScreen()
                updateVisualState()
                speakOnly("Ajá.")
            }
            is WakeResult.Command -> {
                pendingWakeAck?.cancel()
                pendingWakeAck = null
                isListening = false
                EddyRuntimeState.setHeard(applicationContext, wakeResult.text)
                revealEddyOnLockScreen(); compatibilityRecognizer.pause(); isThinking = true; updateVisualState()
                serviceScope.launch { try { delay(80); handleCommand(wakeResult.text) } finally { isThinking = false; updateVisualState() } }
            }
        }
    }

    private suspend fun handleCommand(text: String) {
        memory.rememberUserTurn(text)
        EddyMathEngine.solve(text)?.let { speakResponse("El resultado es $it."); return }
        val commands = brain.understandMany(text)
        if (commands.size > 1) {
            val responses = mutableListOf<String>()
            for (command in commands) {
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                executeDirectCommand(command)?.takeIf { it.isNotBlank() }?.let(responses::add); delay(120L)
            }
            speakResponse(responses.joinToString(" ").ifBlank { "Listo. Ejecuté las acciones que entendí." }); return
        }
        val command = commands.firstOrNull() ?: AssistantCommand.Unknown(text)
        if (command == AssistantCommand.ClearMemory) { memory.clearAll(); speakResponse("De una. Borré mi memoria local. Empezamos de nuevo."); return }
        memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
        if (command is AssistantCommand.SearchWeb) { researchAndSpeak(command.query, true); return }
        if (command is AssistantCommand.Unknown) {
            memory.recallLearnedAnswer(command.originalText)?.let { speakResponse(it); return }
            val remote = if (webClient.isConfigured) webClient.reply(command.originalText, memory.contextForAi(), false) else null
            if (remote != null) {
                memory.rememberLearnedAnswer(command.originalText, remote.text, remote.webUsed)
                if (looksLikeCapabilityRequest(command.originalText)) {
                    val plan = codeAgent.analyze(command.originalText)
                    codeAgent.registerNativeProposal(
                        capability = plan.capability,
                        summary = "${plan.strategy}: ${plan.explanation}",
                        candidateCode = remote.text,
                        currentVersion = "0.5.1",
                    )
                }
                if (remote.webUsed) speakResearchResponse(command.originalText, remote) else speakResponse(remote.text)
                return
            }
            val localReply = localLlm.reply(command.originalText, memory.contextForAi())
            val finalReply = localReply ?: fallbackConversation.reply(command.originalText, memory)
            if (looksLikeCapabilityRequest(command.originalText)) {
                val plan = codeAgent.analyze(command.originalText)
                codeAgent.registerNativeProposal(
                    capability = plan.capability,
                    summary = "${plan.strategy}: ${plan.explanation}",
                    candidateCode = finalReply,
                    currentVersion = "0.5.1",
                )
            }
            speakResponse(finalReply); return
        }
        speakResponse(executeDirectCommand(command) ?: "Listo.")
    }

    private fun looksLikeCapabilityRequest(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return listOf(
            "aprende a", "aprendé a", "programate", "programáte", "prográmate", "mejorate", "mejoráte", "mejórate",
            "agrega una funcion", "agregá una función", "agrega una función", "crea una funcion", "creá una función",
            "quiero que puedas", "necesito que puedas", "haz que puedas", "hacé que puedas", "convertite en", "conviértete en",
        ).any(value::contains)
    }

    private fun executeDirectCommand(command: AssistantCommand): String? = when (command) {
        AssistantCommand.Greeting -> "Aquí estoy. Decime."
        AssistantCommand.TellTime -> "Son las ${SimpleDateFormat("h:mm a", Locale.forLanguageTag("es-NI")).format(Date())}."
        AssistantCommand.OpenCamera -> executor.openCamera().spokenMessage
        AssistantCommand.MemorySummary -> memory.describeLearnedPatterns()
        AssistantCommand.ClearMemory -> { memory.clearAll(); "Borré mi memoria local." }
        is AssistantCommand.OpenApp -> executor.openApp(command.app).spokenMessage
        is AssistantCommand.OpenAppByName -> executor.openAppByName(command.name).spokenMessage
        is AssistantCommand.Dial -> executor.dial(command.number).spokenMessage
        is AssistantCommand.ComposeMessage -> executor.composeMessage(command.number, command.message).spokenMessage
        is AssistantCommand.WhatsAppMessage -> executor.whatsappMessage(command.number, command.message).spokenMessage
        is AssistantCommand.PlaySpotify -> executor.playSpotify(command.query).spokenMessage
        is AssistantCommand.SetAlarm -> executor.setAlarm(command.hour, command.minute, command.label).spokenMessage
        is AssistantCommand.SetTimer -> executor.setTimer(command.seconds, command.label).spokenMessage
        is AssistantCommand.OpenMaps -> executor.openMaps(command.query).spokenMessage
        is AssistantCommand.SearchWeb -> null
        is AssistantCommand.ShareText -> executor.shareText(command.text).spokenMessage
        is AssistantCommand.SetTorch -> executor.setTorch(command.enabled).spokenMessage
        is AssistantCommand.SetVolume -> executor.setVolume(command.percent).spokenMessage
        is AssistantCommand.AdjustVolume -> executor.adjustVolume(command.direction).spokenMessage
        is AssistantCommand.SetBrightness -> executor.setBrightness(command.percent).spokenMessage
        is AssistantCommand.OpenSystemPanel -> executor.openSystemPanel(command.panel).spokenMessage
        AssistantCommand.BatteryStatus -> executor.batteryStatus().spokenMessage
        is AssistantCommand.Vibrate -> executor.vibrate(command.milliseconds).spokenMessage
        is AssistantCommand.SmartHomeControl -> smartHome.control(command.target, command.enabled).spokenMessage
        AssistantCommand.OpenSmartHomeSettings -> executor.openSmartHomeSettings().spokenMessage
        AssistantCommand.OpenAiSettings -> executor.openAiSettings().spokenMessage
        is AssistantCommand.Unknown -> null
    }

    private suspend fun researchAndSpeak(query: String, forceWeb: Boolean) {
        if (!webClient.isConfigured) { speakResponse("No tengo disponible el backend web de EDDY ahorita."); return }
        EddyRuntimeState.setResponse(applicationContext, "Investigando en Internet…")
        val reply = webClient.reply(query, memory.contextForAi(), forceWeb) ?: run { speakResponse("No pude consultar Internet ahorita."); return }
        speakResearchResponse(query, reply)
    }

    private suspend fun speakResearchResponse(question: String, reply: EddyAiReply) {
        val finalText = localLlm.reply(question, memory.contextForAi(), reply.evidence.ifBlank { reply.text }) ?: reply.text
        EddyRuntimeState.setAiResponse(applicationContext, finalText, reply.webUsed, reply.sources)
        memory.rememberAssistantTurn(finalText); speakOnly(finalText)
    }

    private fun speakResponse(text: String) { EddyRuntimeState.setResponse(applicationContext, text); memory.rememberAssistantTurn(text); speakOnly(text) }
    private fun speakOnly(text: String) {
        if (text.isBlank()) return
        if (localVoiceActive) localVoice?.setAssistantSpeaking(true) else compatibilityRecognizer.pause()
        isSpeaking = true; updateVisualState()
        val queued = if (neuralTts.isAvailable) neuralTts.speak(text) else platformTts.speak(text)
        if (!queued) onSpeakingChanged(false)
    }
    private fun onSpeakingChanged(speaking: Boolean) {
        isSpeaking = speaking
        if (localVoiceActive) localVoice?.setAssistantSpeaking(speaking) else if (!speaking && hasMicrophonePermission()) compatibilityRecognizer.resume()
        updateVisualState()
    }
    private fun updateVisualState() {
        EddyRuntimeState.setState(applicationContext, when { isSpeaking -> EddyRuntimeState.State.SPEAKING; isThinking -> EddyRuntimeState.State.THINKING; isListening -> EddyRuntimeState.State.LISTENING; else -> EddyRuntimeState.State.IDLE })
    }
    private fun rearmCompatibilityRecognizer(delayMs: Long) {
        if (localVoiceActive || !hasMicrophonePermission() || isSpeaking || isThinking) return
        serviceScope.launch { delay(delayMs); if (!localVoiceActive && hasMicrophonePermission() && !isSpeaking && !isThinking) compatibilityRecognizer.restart(0L) }
    }
    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); screenReceiverRegistered = true
    }
    private fun unregisterScreenStateReceiver() { if (screenReceiverRegistered) { runCatching { unregisterReceiver(screenStateReceiver) }; screenReceiverRegistered = false } }
    private fun hasMicrophonePermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "EDDY local activo", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(WAKE_CHANNEL_ID, "Despertar EDDY", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false); enableVibration(false); setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }
    private fun startAsForeground() {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 11, Intent(this, EddyAssistantService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_eddy_notification).setContentTitle("EDDY está atento").setContentText("Escucha privada. Solo interactúa cuando decís EDDY.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0, "Detener", stop).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTIFICATION_ID, notification)
    }
    private fun revealEddyOnLockScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) return
        val show = PendingIntent.getActivity(this, WAKE_REQUEST_CODE, Intent(this, EddyWakeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, WAKE_CHANNEL_ID).setSmallIcon(R.drawable.ic_eddy_notification).setContentTitle("EDDY").setContentText("Te escucho.").setContentIntent(show).setFullScreenIntent(show, true).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        getSystemService(NotificationManager::class.java)?.notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubbleView != null) return
        val wm = getSystemService(WindowManager::class.java); windowManager = wm
        val size = dp(66); val container = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(238, 255, 255, 255)); setStroke(dp(2), Color.rgb(0, 205, 160)) }
            elevation = dp(14).toFloat(); setPadding(dp(10), dp(10), dp(10), dp(10)); addView(ImageView(this@EddyAssistantService).apply { setImageResource(R.drawable.ic_eddy_mark); scaleType = ImageView.ScaleType.CENTER_INSIDE }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(18)); y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(220)) }
        bubbleParams = params
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragging = false
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = event.rawX - downX; val dy = event.rawY - downY; if (!dragging && (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6))) dragging = true; if (dragging) { params.x = originX + dx.toInt(); params.y = originY + dy.toInt(); runCatching { wm.updateViewLayout(container, params) } }; true }
                MotionEvent.ACTION_UP -> { if (dragging) saveBubblePosition(params) else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
                else -> false
            }
        }
        runCatching { wm.addView(container, params); bubbleView = container }
    }
    private fun hideBubble() { bubbleView?.let { view -> runCatching { windowManager?.removeView(view) } }; bubbleView = null }
    private fun saveBubblePosition(params: WindowManager.LayoutParams) { bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun acquireCpuWakeLock() { if (cpuWakeLock?.isHeld == true) return; cpuWakeLock = (getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EDDY:AlwaysOnWake")?.apply { setReferenceCounted(false); acquire() }) }
    private fun releaseCpuWakeLock() { cpuWakeLock?.let { if (it.isHeld) runCatching { it.release() } }; cpuWakeLock = null }

    companion object {
        private const val CHANNEL_ID = "eddy_assistant_channel"
        private const val WAKE_CHANNEL_ID = "eddy_wake_channel"
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_NOTIFICATION_ID = 2002
        private const val WAKE_REQUEST_CODE = 2102
        private const val PARTIAL_WAKE_DEBOUNCE_MS = 1_200L
        private const val WAKE_ACK_DELAY_MS = 950L
        private const val BUBBLE_PREFS = "eddy_bubble_prefs"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        const val ACTION_STOP = "com.eddy.assistant.action.STOP"
        const val ACTION_SHOW_BUBBLE = "com.eddy.assistant.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.eddy.assistant.action.HIDE_BUBBLE"
        const val ACTION_REFRESH_BUBBLE = "com.eddy.assistant.action.REFRESH_BUBBLE"
    }
}
