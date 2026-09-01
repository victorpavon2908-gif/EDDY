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
import android.os.SystemClock
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
import com.eddy.assistant.ai.EddyWebSource
import com.eddy.assistant.ai.EddyFallbackConversation
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.EddyMathEngine
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.brain.WebQueryRouter
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
import kotlinx.coroutines.CancellationException
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

    private var destroyed = false
    private var localVoiceStarting = false
    private var localVoiceEpoch = 0
    private var localRecoveryAttempts = 0
    private var pendingListen = false
    private var isTranscribing = false
    private var compatibilityCommandPending = false
    private var commandJob: Job? = null
    private var speechTimeout: Job? = null
    private var continueAfterSpeech = false
    private var recoveryJob: Job? = null
    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var screenReceiverRegistered = false
    private var lastPartialWakeAt = 0L
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
                if (!localVoiceActive && !localVoiceStarting && !destroyed) {
                    if (recognizerOpen) EddyRuntimeState.setInputStatus(applicationContext, "Micrófono compatible activo")
                    isListening = recognizerOpen && wakeGate.isArmed(SystemClock.elapsedRealtime())
                    updateVisualState()
                }
            },
            onPartialResult = { partial ->
                if (!localVoiceActive && !localVoiceStarting) handlePartialWakeWord(partial)
            },
            onResult = { raw ->
                compatibilityCommandPending = false
                if (!localVoiceActive && !localVoiceStarting) handleRecognition(raw)
            },
            onError = { error ->
                compatibilityCommandPending = false
                if (!localVoiceActive && !localVoiceStarting && !destroyed) {
                    EddyRuntimeState.setInputStatus(applicationContext, error)
                    if (wakeGate.isArmed(SystemClock.elapsedRealtime())) {
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
            onFailure = { text -> serviceScope.launch { if (!destroyed && !platformTts.speak(text)) onSpeakingChanged(false) } },
        )

        if (!hasMicrophonePermission()) {
            EddyRuntimeState.setResponse(applicationContext, "Abrí EDDY y concedé el permiso de micrófono.")
            stopSelf()
            return
        }
        createNotificationChannels()
        try { startAsForeground() } catch (_: RuntimeException) {
            EddyRuntimeState.setResponse(applicationContext, "Abrí EDDY para activar el micrófono.")
            stopSelf()
            return
        }
        acquireCpuWakeLock()
        registerScreenStateReceiver()
        EddyRuntimeState.setRunning(applicationContext, true)
        startCompatibilityListening("Decí EDDY o tocá Hablar para pedirme algo.")
        startModelBootstrap()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN_NOW -> listenNow()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
        }
        if (!localVoiceActive && !localVoiceStarting && hasMicrophonePermission() && !isSpeaking && !isThinking) compatibilityRecognizer.resume()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        commandJob?.cancel()
        speechTimeout?.cancel()
        recoveryJob?.cancel()
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
        // Downloads must not keep the user waiting without any microphone.
        serviceScope.launch {
            try {
                var lastProgress = ""
                val ready = withContext(Dispatchers.IO) {
                    modelManager.ensureRecommended(deviceProfile) { progress ->
                        val label = "Escucha compatible · preparando ${progress.modelId}"
                        if (label != lastProgress && progress.state in setOf(EddyModelProgress.State.INSTALLING, EddyModelProgress.State.DOWNLOADING)) {
                            lastProgress = label
                            serviceScope.launch { if (!destroyed && !localVoiceActive) EddyRuntimeState.setInputStatus(applicationContext, label) }
                        }
                    }
                    modelManager.coreReady()
                }
                if (ready) {
                    awaitIdleForVoiceSwitch()
                    localVoiceStarting = true
                    if (!startLocalVoiceIfReady()) startCompatibilityListening("No pude iniciar la voz local. Podés usar Hablar en modo compatible.")
                } else {
                    EddyRuntimeState.setInputStatus(applicationContext, "Escucha compatible · faltan modelos para voz local sin conexión")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                startCompatibilityListening("No pude preparar la voz local. Escucha compatible activa.")
            } finally {
                localVoiceStarting = false
                if (pendingListen && !destroyed) listenNow()
            }
        }
    }

    private suspend fun awaitIdleForVoiceSwitch() {
        while (!destroyed && (isThinking || isSpeaking || isTranscribing || compatibilityCommandPending || commandJob?.isActive == true || wakeGate.isArmed(SystemClock.elapsedRealtime()))) delay(250L)
    }

    private fun listenNow() {
        if (destroyed || isThinking || isSpeaking || isTranscribing) return
        if (localVoiceStarting) { pendingListen = true; return }
        pendingListen = false
        wakeGate.arm(SystemClock.elapsedRealtime(), 30_000L)
        EddyRuntimeState.setHeard(applicationContext, "")
        EddyRuntimeState.setResponse(applicationContext, "Te escucho. Decime qué necesitás.")
        isListening = true
        if (localVoiceActive) localVoice?.activate() else compatibilityRecognizer.resume()
        updateVisualState()
    }

    private suspend fun startLocalVoiceIfReady(): Boolean {
        if (localVoiceActive || !modelManager.coreReady() || !hasMicrophonePermission()) return localVoiceActive
        val epoch = ++localVoiceEpoch
        val engine = EddyLocalVoiceEngine(
            context = applicationContext,
            models = modelManager,
            profile = deviceProfile,
            ownerVoice = ownerVoice,
            onState = { voiceState -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                when (voiceState) {
                    EddyLocalVoiceEngine.State.PASSIVE -> { isTranscribing = false; isListening = false; if (!isSpeaking && !isThinking) EddyRuntimeState.setState(applicationContext, EddyRuntimeState.State.IDLE) }
                    EddyLocalVoiceEngine.State.VERIFYING, EddyLocalVoiceEngine.State.PROCESSING -> { isTranscribing = true; isListening = false; updateVisualState() }
                    EddyLocalVoiceEngine.State.ACTIVE -> { isTranscribing = false; isListening = !isThinking && !isSpeaking; updateVisualState() }
                    EddyLocalVoiceEngine.State.SPEAKING -> { isListening = false; updateVisualState() }
                    EddyLocalVoiceEngine.State.STOPPED -> {
                        if (localVoiceActive) fallbackToCompatibilityRecognizer("El motor local se detuvo. Recuperando escucha.")
                    }
                }
            }},
            onWake = { _, _ -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                EddyRuntimeState.setHeard(applicationContext, "EDDY")
                EddyRuntimeState.setResponse(applicationContext, "Te escucho.")
                revealEddyOnLockScreen()
            }},
            onAwaitingCommand = { prompt, retry -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                isTranscribing = false
                if (!isThinking && !isSpeaking) {
                    EddyRuntimeState.setResponse(applicationContext, prompt)
                    speakOnly(prompt, continueCommand = retry)
                }
            } },
            onCommand = { text -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) { isTranscribing = false; submitCommand(text) }
            } },
            onUnauthorizedVoice = {},
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    EddyRuntimeState.setInputStatus(applicationContext, error)
                    if (localVoiceActive) fallbackToCompatibilityRecognizer(error)
                }
            } },
        )

        // Release the Android recognizer before asking AudioRecord to own the microphone.
        compatibilityRecognizer.stopContinuous()
        EddyRuntimeState.setInputStatus(applicationContext, "Iniciando micrófono local…")
        localVoice = engine
        val started = withContext(Dispatchers.IO) { engine.start() }
        if (destroyed) { engine.stop(); return false }
        return if (started && engine.isRunning) {
            localVoiceActive = true
            isListening = false
            EddyRuntimeState.setInputStatus(applicationContext, "Micrófono local activo · disponible sin conexión")
            true
        } else {
            ++localVoiceEpoch
            engine.stop(); localVoice = null
            val stage = engine.lastInitializationFailure?.stage ?: "micrófono"
            EddyRuntimeState.setInputStatus(applicationContext, "No inició $stage local · escucha compatible")
            false
        }
    }

    private fun fallbackToCompatibilityRecognizer(error: String) {
        if (destroyed) return
        ++localVoiceEpoch
        localVoice?.stop()
        localVoice = null
        isTranscribing = false
        localVoiceActive = false
        isListening = false
        if (hasMicrophonePermission() && !isSpeaking && !isThinking) {
            compatibilityRecognizer.startContinuous()
            compatibilityRecognizer.resume()
        }
        EddyRuntimeState.setResponse(applicationContext, "Escucha compatible activa. $error")
        updateVisualState()
        recoveryJob?.cancel()
        if (localRecoveryAttempts++ >= 1) return
        recoveryJob = serviceScope.launch {
            delay(30_000L)
            awaitIdleForVoiceSwitch()
            if (!destroyed && !isSpeaking && !isThinking && modelManager.coreReady()) {
                localVoiceStarting = true
                try {
                    if (!startLocalVoiceIfReady()) startCompatibilityListening("Escucha compatible activa. Decí EDDY.")
                } finally { localVoiceStarting = false; if (pendingListen) listenNow() }
            }
        }
    }

    private fun startCompatibilityListening(message: String) {
        if (destroyed || !hasMicrophonePermission() || isSpeaking || isThinking) return
        localVoiceActive = false
        isListening = false
        compatibilityRecognizer.startContinuous()
        compatibilityRecognizer.resume()
        isThinking = false
        EddyRuntimeState.setResponse(applicationContext, message)
        updateVisualState()
    }

    private fun handlePartialWakeWord(partial: String) {
        if (destroyed || isThinking || isSpeaking || !wakeGate.hasWakeWord(partial)) return
        compatibilityCommandPending = true
        val now = SystemClock.elapsedRealtime()
        if (now - lastPartialWakeAt < PARTIAL_WAKE_DEBOUNCE_MS) return
        lastPartialWakeAt = now
        // Partial transcripts only update the UI; final results authorize a command.
        isListening = true
        EddyRuntimeState.setHeard(applicationContext, "EDDY")
        EddyRuntimeState.setResponse(applicationContext, "Te escucho. Decime qué querés que haga.")
        revealEddyOnLockScreen()
        updateVisualState()
    }

    private fun handleRecognition(raw: String) {
        if (destroyed || isThinking || isSpeaking) return
        when (val wakeResult = wakeGate.consume(raw, SystemClock.elapsedRealtime())) {
            WakeResult.Ignored -> {
                isListening = false
                updateVisualState()
                compatibilityRecognizer.resume()
            }
            WakeResult.Activated -> {
                isListening = true
                EddyRuntimeState.setHeard(applicationContext, "EDDY")
                EddyRuntimeState.setResponse(applicationContext, "Ajá.")
                revealEddyOnLockScreen()
                updateVisualState()
                speakOnly("Ajá.", continueCommand = true)
            }
            is WakeResult.Command -> submitCommand(wakeResult.text)
        }
    }

    private fun submitCommand(text: String) {
        if (destroyed || isSpeaking || isThinking || commandJob?.isActive == true) return
        wakeGate.disarm()
        isListening = false
        isThinking = true
        EddyRuntimeState.setResponse(applicationContext, "Procesando tu petición…")
        isTranscribing = false
        localVoice?.setAssistantBusy(true)
        compatibilityRecognizer.pause()
        EddyRuntimeState.setHeard(applicationContext, text)
        revealEddyOnLockScreen()
        updateVisualState()
        commandJob = serviceScope.launch {
            try {
                handleCommand(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                speakResponse("No pude completar eso. Volvé a llamarme y lo intentamos.")
            } finally {
                isThinking = false
                if (!isSpeaking) finishTurn()
                updateVisualState()
            }
        }
    }

    private fun finishTurn() {
        wakeGate.disarm()
        localVoice?.finishTurn()
        if (!destroyed && !localVoiceActive && !localVoiceStarting && hasMicrophonePermission()) compatibilityRecognizer.resume()
    }

    private suspend fun handleCommand(text: String) {
        memory.rememberUserTurn(text)
        memory.learnExplicitly(text)?.let { speakResponse(it); return }
        EddyMathEngine.solve(text)?.let { speakResponse("El resultado es $it."); return }
        val commands = brain.understandMany(text)
        if (commands.size > 1) {
            val responses = mutableListOf<String>()
            val sources = mutableListOf<EddyWebSource>()
            for (command in commands) {
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                if (command is AssistantCommand.SearchWeb) {
                    val answer = researchReply(command.query)
                    responses.add(answer.text)
                    sources.addAll(answer.sources)
                    continue
                }
                executeDirectCommand(command)?.takeIf { it.isNotBlank() }?.let(responses::add); delay(120L)
            }
            val answer = responses.joinToString(" ").ifBlank { "No entendí qué acciones querés que haga." }
            if (sources.isEmpty()) speakResponse(answer)
            else speakResearchResponse(text, EddyAiReply(answer, true, sources.distinctBy { it.url }.take(8)))
            return
        }
        val command = commands.firstOrNull() ?: AssistantCommand.Unknown(text)
        if (command == AssistantCommand.ClearMemory) { memory.clearAll(); speakResponse("De una. Borré mi memoria local. Empezamos de nuevo."); return }
        memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
        if (command is AssistantCommand.SearchWeb) { researchAndSpeak(command.query, true); return }
        if (command is AssistantCommand.Unknown) {
            // Learned answers must never shadow real commands such as clearing memory.
            memory.personalReply(text)?.let { speakResponse(it); return }
            if (WebQueryRouter.needsCurrentInformation(command.originalText)) { researchAndSpeak(text, true); return }
            val remote = if (webClient.isConfigured) webClient.reply(command.originalText, memory.contextForAi(false), false, memory.historyForAi(text)) else null
            if (remote != null) {
                if (looksLikeCapabilityRequest(command.originalText)) {
                    val plan = codeAgent.analyze(command.originalText)
                    codeAgent.registerNativeProposal(
                        capability = plan.capability,
                        summary = "${plan.strategy}: ${plan.explanation}",
                        candidateCode = remote.text,
                        currentVersion = "0.6.0",
                    )
                }
                if (remote.webUsed) speakResearchResponse(command.originalText, remote) else speakResponse(remote.text)
                return
            }
            val localReply = localLlm.reply(command.originalText, memory.contextForAi(currentMessage = text))
            val finalReply = localReply ?: fallbackConversation.reply(command.originalText, memory, webClient.lastError)
            if (looksLikeCapabilityRequest(command.originalText)) {
                val plan = codeAgent.analyze(command.originalText)
                codeAgent.registerNativeProposal(
                    capability = plan.capability,
                    summary = "${plan.strategy}: ${plan.explanation}",
                    candidateCode = finalReply,
                    currentVersion = "0.6.0",
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
        speakResearchResponse(query, researchReply(query, forceWeb))
    }

    private suspend fun researchReply(query: String, forceWeb: Boolean = true): EddyAiReply {
        fun unavailable(message: String) = EddyAiReply(message, false, emptyList())
        if (!webClient.isConfigured) {
            return unavailable("Para leerte una respuesta con fuentes, configurá tu clave de Gemini en Ajustes. ${executor.searchWeb(query).spokenMessage}")
        }
        EddyRuntimeState.setSearching(applicationContext, true)
        EddyRuntimeState.setResponse(applicationContext, "Investigando en Internet…")
        return try {
            val current = EddyRuntimeState.read(applicationContext).heardText
            val reply = webClient.reply(query, memory.contextForAi(false), forceWeb, memory.historyForAi(current))
            when {
                reply == null -> unavailable(webClient.lastError ?: "No pude consultar Internet. Volvé a intentarlo.")
                // Search-enabled does not mean Google actually supplied evidence.
                forceWeb && !reply.webUsed -> unavailable("No obtuve fuentes web para verificarlo. Podés pedirme otra búsqueda más específica.")
                else -> reply
            }
        } finally { EddyRuntimeState.setSearching(applicationContext, false) }
    }

    private suspend fun speakResearchResponse(question: String, reply: EddyAiReply) {
        val finalText = reply.text
        EddyRuntimeState.setAiResponse(applicationContext, finalText, reply.webUsed, reply.sources)
        memory.rememberAssistantTurn(finalText); speakOnly(finalText)
    }

    private fun speakResponse(text: String) { EddyRuntimeState.setResponse(applicationContext, text); memory.rememberAssistantTurn(text); speakOnly(text) }
    private fun speakOnly(text: String, continueCommand: Boolean = false) {
        if (text.isBlank() || destroyed) return
        continueAfterSpeech = continueCommand
        if (localVoiceActive) localVoice?.setAssistantSpeaking(true, continueCommand) else compatibilityRecognizer.pause()
        isSpeaking = true
        updateVisualState()
        speechTimeout?.cancel()
        speechTimeout = serviceScope.launch {
            delay((text.length * 110L + 5_000L).coerceIn(12_000L, 120_000L))
            if (!destroyed && isSpeaking) {
                platformTts.stop()
                neuralTts.stop()
                onSpeakingChanged(false)
            }
        }
        val queued = if (neuralTts.isAvailable) neuralTts.speak(text) else platformTts.speak(text)
        if (queued) EddyRuntimeState.setVoiceReady(applicationContext, true)
        else { EddyRuntimeState.setVoiceReady(applicationContext, false); onSpeakingChanged(false) }
    }
    private fun onSpeakingChanged(speaking: Boolean) {
        serviceScope.launch {
            if (destroyed || (!speaking && !isSpeaking)) return@launch
            isSpeaking = speaking
            if (localVoiceActive) localVoice?.setAssistantSpeaking(speaking, continueAfterSpeech)
            if (!speaking) {
                speechTimeout?.cancel()
                speechTimeout = null
                if (continueAfterSpeech) {
                    isListening = true
                    wakeGate.arm(SystemClock.elapsedRealtime())
                    if (!localVoiceActive) compatibilityRecognizer.resume()
                } else if (!isThinking) finishTurn()
            }
            updateVisualState()
        }
    }
    private fun updateVisualState() {
        EddyRuntimeState.setState(applicationContext, when { isSpeaking -> EddyRuntimeState.State.SPEAKING; isThinking || isTranscribing -> EddyRuntimeState.State.THINKING; isListening -> EddyRuntimeState.State.LISTENING; else -> EddyRuntimeState.State.IDLE })
    }
    private fun rearmCompatibilityRecognizer(delayMs: Long) {
        if (localVoiceActive || localVoiceStarting || !hasMicrophonePermission() || isSpeaking || isThinking) return
        serviceScope.launch { delay(delayMs); if (!localVoiceActive && !localVoiceStarting && hasMicrophonePermission() && !isSpeaking && !isThinking) compatibilityRecognizer.resume() }
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_eddy_notification).setContentTitle("EDDY está atento").setContentText("Decí EDDY para pedir algo. Podés detener la escucha aquí.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0, "Detener", stop).build()
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
        private const val BUBBLE_PREFS = "eddy_bubble_prefs"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        const val ACTION_LISTEN_NOW = "com.eddy.assistant.LISTEN_NOW"
        const val ACTION_STOP = "com.eddy.assistant.action.STOP"
        const val ACTION_SHOW_BUBBLE = "com.eddy.assistant.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.eddy.assistant.action.HIDE_BUBBLE"
        const val ACTION_REFRESH_BUBBLE = "com.eddy.assistant.action.REFRESH_BUBBLE"
    }
}
