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
import com.eddy.assistant.ai.EddyAiSettings
import com.eddy.assistant.ai.AutonomousResearch
import com.eddy.assistant.ai.ConversationCoordinator
import com.eddy.assistant.ai.EddyAiClient
import com.eddy.assistant.ai.EddyAiReply
import com.eddy.assistant.ai.EddyWebSource
import com.eddy.assistant.ai.EddyFallbackConversation
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.EddyMathEngine
import com.eddy.assistant.brain.LocalBrain
import com.eddy.assistant.brain.WebQueryRouter
import com.eddy.assistant.learning.AdaptiveIntentStore
import com.eddy.assistant.learning.OnlineIntentNetwork
import com.eddy.assistant.learning.LearnedIntent
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
import com.eddy.assistant.voice.EddyTextToSpeech
import com.eddy.assistant.voice.VoiceRecoveryPolicy
import com.eddy.assistant.voice.SpeechOutputPolicy
import com.eddy.assistant.voice.SpeechProsody
import java.io.File
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
    private lateinit var webClient: EddyAiClient
    private lateinit var fallbackConversation: EddyFallbackConversation
    private lateinit var proactiveScheduler: EddyProactiveScheduler
    private lateinit var modelManager: EddyModelManager
    private lateinit var deviceProfile: EddyDeviceProfile
    private lateinit var ownerVoice: EddyVoiceProfile
    private lateinit var localLlm: EddyLocalLlm
    private lateinit var codeAgent: EddyCodeAgent
    private val adaptiveStore by lazy { AdaptiveIntentStore(File(filesDir, "adaptive_learning")) }
    private var adaptiveNetwork: OnlineIntentNetwork? = null
    private var adaptiveUnavailable = false
    private var replyProsody = SpeechProsody()
    private val speechOutput = SpeechOutputPolicy()
    private var localVoice: EddyLocalVoiceEngine? = null
    private var localVoiceActive = false
    private lateinit var platformTts: EddyTextToSpeech
    private lateinit var neuralTts: EddyNeuralTextToSpeech

    private var destroyed = false
    private var foregroundReady = false
    private var localVoiceStarting = false
    private var localVoiceEpoch = 0
    private var isTranscribing = false
    private var commandJob: Job? = null
    private var speechTimeout: Job? = null
    private var continueAfterSpeech = false
    private var recoveryJob: Job? = null
    private val voiceRecovery = VoiceRecoveryPolicy()
    private var initializationFailure: EddyLocalVoiceEngine.InitializationFailure? = null
    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var screenReceiverRegistered = false
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val bubblePrefs by lazy { getSharedPreferences(BUBBLE_PREFS, Context.MODE_PRIVATE) }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { acquireCpuWakeLock(); ensureVoiceListening() }
                Intent.ACTION_SCREEN_ON -> ensureVoiceListening()
                Intent.ACTION_USER_PRESENT -> ensureVoiceListening()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        brain = LocalBrain()
        executor = ActionExecutor(applicationContext)
        smartHome = LocalSmartHomeClient(applicationContext)
        memory = EddyMemory(applicationContext)
        webClient = EddyAiClient(applicationContext)
        fallbackConversation = EddyFallbackConversation()
        proactiveScheduler = EddyProactiveScheduler(applicationContext, memory)
        modelManager = EddyModelManager(applicationContext)
        deviceProfile = EddyDeviceProfile.detect(applicationContext)
        ownerVoice = EddyVoiceProfile(applicationContext)
        localLlm = EddyLocalLlm(applicationContext, modelManager)
        codeAgent = EddyCodeAgent(applicationContext)

        platformTts = EddyTextToSpeech(
            context = applicationContext,
            onReady = { ready ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) EddyRuntimeState.setVoiceReady(applicationContext, ready)
            },
            onVoiceSelected = { description ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) EddyRuntimeState.setVoiceStatus(applicationContext, description)
            },
            onSpeakingChanged = ::onSpeakingChanged,
        )
        neuralTts = EddyNeuralTextToSpeech(
            models = modelManager,
            profile = deviceProfile,
            onSpeakingChanged = ::onSpeakingChanged,
            onFailure = { text, audioStarted -> serviceScope.launch {
                if (!destroyed) {
                    speechOutput.neuralFailed()
                    EddyRuntimeState.setVoiceStatus(applicationContext, "La voz local se interrumpió. ${platformTts.voiceDescription}")
                    // Once audio has started, retain the visible answer instead of repeating it in another voice.
                    if (audioStarted || !platformTts.speak(text, replyProsody)) onSpeakingChanged(false)
                    EddyRuntimeState.setVoiceReady(applicationContext, platformTts.isReady)
                }
            } },
        )

        if (!EddyVoiceSettings.enabled(this) || !hasMicrophonePermission()) {
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
        foregroundReady = true
        acquireCpuWakeLock()
        registerScreenStateReceiver()
        EddyRuntimeState.setRunning(applicationContext, true)
        EddyRuntimeState.setInput(applicationContext, EddyRuntimeState.InputState.PREPARING, "Preparando activación por voz…")
        EddyRuntimeState.setResponse(applicationContext, "Estoy preparando la escucha local. Cuando esté lista, decí EDDY.")
        ensureVoiceListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { EddyVoiceSettings.setEnabled(this, false); stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
        }
        if (!foregroundReady || !EddyVoiceSettings.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        ensureVoiceListening()
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
        neuralTts.shutdown()
        platformTts.shutdown()
        localLlm.release()
        releaseCpuWakeLock()
        serviceScope.cancel()
        EddyRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    /** One owner for preparation, native startup and recovery. No Android recognition sessions. */
    private fun ensureVoiceListening(initialDelay: Long = 0L) {
        if (destroyed || !foregroundReady || localVoiceActive || recoveryJob?.isActive == true || !EddyVoiceSettings.enabled(this)) return
        recoveryJob = serviceScope.launch {
            if (initialDelay > 0) delay(initialDelay)
            while (!destroyed && EddyVoiceSettings.enabled(this@EddyAssistantService)) {
                if (!hasMicrophonePermission()) {
                    inputUnavailable("Concedé permiso de micrófono en los ajustes de Android y volvé a abrir EDDY.")
                    return@launch
                }
                while (isThinking || isSpeaking || commandJob?.isActive == true) delay(250L)
                localVoiceStarting = true
                try {
                    // Do not open a second recorder while the old worker still owns native resources.
                    val previous = localVoice
                    val released = withContext(Dispatchers.IO) { previous?.stopAndAwait() ?: true }
                    if (!released) {
                        inputUnavailable("El micrófono anterior aún se está cerrando. Voy a reintentar.")
                    } else {
                        localVoice = null
                        val failure = initializationFailure
                        initializationFailure = null
                        val failedModel = failure?.model
                        EddyRuntimeState.setInput(applicationContext, EddyRuntimeState.InputState.PREPARING, "Preparando escucha local…")
                        val ready = withContext(Dispatchers.IO) {
                            if (failedModel != null && voiceRecovery.allowModelRepair(failedModel.id)) {
                                modelManager.repair(failedModel, ::onModelProgress)
                            }
                            modelManager.ensureRecommended(deviceProfile, ::onModelProgress)
                        }
                        if (ready && startLocalVoiceIfReady()) return@launch
                        if (!ready) inputUnavailable("Faltan modelos de voz. Conectate a Internet y comprobá el espacio disponible; reintentaré automáticamente.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    inputUnavailable("No pude preparar la escucha local. Comprobá conexión, almacenamiento y permiso de micrófono.")
                } finally { localVoiceStarting = false }
                delay(voiceRecovery.nextDelayMillis(SystemClock.elapsedRealtime()))
            }
        }
    }

    private fun onModelProgress(progress: EddyModelProgress) {
        if (progress.state !in setOf(EddyModelProgress.State.INSTALLING, EddyModelProgress.State.DOWNLOADING)) return
        val amount = if (progress.totalBytes > 0) " · ${progress.downloadedBytes * 100 / progress.totalBytes}%" else ""
        serviceScope.launch {
            if (!destroyed && localVoiceStarting) EddyRuntimeState.setInputStatus(applicationContext, "Preparando voz local$amount")
        }
    }

    private fun inputUnavailable(message: String) {
        isListening = false
        isTranscribing = false
        EddyRuntimeState.setInput(applicationContext, EddyRuntimeState.InputState.ERROR, message)
        if (!isSpeaking && !isThinking) EddyRuntimeState.setResponse(applicationContext, message)
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
                    EddyLocalVoiceEngine.State.PASSIVE -> {
                        if (isListening && !isSpeaking && !isThinking) EddyRuntimeState.setResponse(applicationContext, "Decí EDDY para hablar conmigo.")
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                    EddyLocalVoiceEngine.State.VERIFYING, EddyLocalVoiceEngine.State.PROCESSING -> { isTranscribing = true; isListening = false; updateVisualState() }
                    EddyLocalVoiceEngine.State.ACTIVE -> { isTranscribing = false; isListening = !isThinking && !isSpeaking; updateVisualState() }
                    EddyLocalVoiceEngine.State.SPEAKING -> { isListening = false; updateVisualState() }
                    EddyLocalVoiceEngine.State.STOPPED -> {
                        if (localVoiceActive) recoverLocalVoice("El motor local se detuvo. Voy a recuperar la escucha.")
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
            onMicrophoneSilenced = { silenced -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    if (silenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
                    else {
                        EddyRuntimeState.setInput(applicationContext, EddyRuntimeState.InputState.READY, "Activación local lista · decí EDDY")
                        if (!isSpeaking && !isThinking) EddyRuntimeState.setResponse(applicationContext, "Decí EDDY para hablar conmigo.")
                    }
                }
            } },
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    EddyRuntimeState.setInputStatus(applicationContext, error)
                    if (localVoiceActive) recoverLocalVoice(error)
                }
            } },
        )

        EddyRuntimeState.setInputStatus(applicationContext, "Iniciando micrófono local…")
        localVoice = engine
        val started = withContext(Dispatchers.IO) { engine.start() }
        if (destroyed) { engine.stop(); return false }
        return if (started && engine.isRunning) {
            localVoiceActive = true
            isListening = false
            initializationFailure = null
            voiceRecovery.started(SystemClock.elapsedRealtime())
            if (engine.isMicrophoneSilenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
            else {
                EddyRuntimeState.setInput(applicationContext, EddyRuntimeState.InputState.READY, "Activación local lista · decí EDDY")
                EddyRuntimeState.setResponse(applicationContext, "Decí EDDY para hablar conmigo.")
            }
            updateVisualState()
            true
        } else {
            ++localVoiceEpoch
            engine.stop()
            initializationFailure = engine.lastInitializationFailure
            val stage = initializationFailure?.stage ?: "micrófono"
            val detail = initializationFailure?.detail?.take(160).orEmpty()
            inputUnavailable("No inició $stage local. $detail. Voy a reintentar.")
            false
        }
    }

    private fun recoverLocalVoice(error: String) {
        if (destroyed) return
        ++localVoiceEpoch
        localVoiceActive = false
        localVoice?.stop()
        inputUnavailable(error)
        ensureVoiceListening(voiceRecovery.nextDelayMillis(SystemClock.elapsedRealtime()))
    }

    private fun submitCommand(text: String) {
        if (destroyed || isSpeaking || isThinking || commandJob?.isActive == true) return
        isListening = false
        isThinking = true
        EddyRuntimeState.setResponse(applicationContext, "Procesando tu petición…")
        isTranscribing = false
        localVoice?.setAssistantBusy(true)
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
        localVoice?.finishTurn()
        isListening = false
    }

    private suspend fun handleCommand(text: String) {
        replyProsody = SpeechProsody.forInput(text)
        withContext(Dispatchers.IO) { memory.rememberUserTurn(text) }
        withContext(Dispatchers.IO) { memory.learnExplicitly(text) }?.let {
            learnIntent(text, LearnedIntent.MEMORY)
            speakResponse(it); return
        }
        EddyMathEngine.solve(text)?.let { learnIntent(text, LearnedIntent.ACTION); speakResponse("El resultado es $it."); return }
        val commands = brain.understandMany(text)
        if (commands.size > 1) {
            val responses = mutableListOf<String>()
            val sources = mutableListOf<EddyWebSource>()
            for (command in commands) {
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                if (command is AssistantCommand.SearchWeb) {
                    learnIntent(command.query, LearnedIntent.SEARCH)
                    val answer = researchReply(command.query, openBrowser = true)
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
        if (command == AssistantCommand.ClearMemory) { clearLocalMemory(); speakResponse("De una. Borré mi memoria local. Empezamos de nuevo."); return }
        memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
        if (command is AssistantCommand.SearchWeb) {
            learnIntent(command.query, LearnedIntent.SEARCH)
            speakResearchResponse(command.query, researchReply(command.query, openBrowser = true)); return
        }
        if (command is AssistantCommand.Unknown) {
            withContext(Dispatchers.IO) { memory.personalReply(text) }?.let {
                learnIntent(text, LearnedIntent.MEMORY); speakResponse(it); return
            }
            val prediction = predictIntent(text)
            val remoteContext = withContext(Dispatchers.IO) { memory.contextForAi(false) }
            val history = memory.historyForAi(text)
            val answer = ConversationCoordinator.reply(
                message = text,
                localFirst = EddyAiSettings.localFirst(applicationContext),
                autoResearch = EddyAiSettings.autoResearch(applicationContext),
                learnedSearch = prediction?.let { it.reliable && it.intent == LearnedIntent.SEARCH } == true,
                local = {
                    val context = withContext(Dispatchers.IO) { memory.contextForAi(currentMessage = text) }
                    localLlm.reply(text, context)
                },
                cloud = { requireSources ->
                    if (requireSources) researchReply(text)
                    else if (webClient.isConfigured) webClient.reply(text, remoteContext, false, history)
                    else null
                },
                fallback = { withContext(Dispatchers.IO) {
                    val error = if (AutonomousResearch.offlineOnly(text)) localLlm.lastError else webClient.lastError ?: localLlm.lastError
                    fallbackConversation.reply(text, memory, error)
                } },
            )
            // Only deterministic rules label training examples; never train on a prediction.
            if (WebQueryRouter.needsCurrentInformation(text) && AutonomousResearch.allowedFor(text)) learnIntent(text, LearnedIntent.SEARCH)
            if (looksLikeCapabilityRequest(text)) {
                val plan = codeAgent.analyze(text)
                codeAgent.registerNativeProposal(plan.capability, "${plan.strategy}: ${plan.explanation}", answer.text, "0.6.0")
            }
            speakResearchResponse(text, answer)
            return
        }
        learnIntent(text, when (command) {
            AssistantCommand.Greeting -> LearnedIntent.CONVERSATION
            AssistantCommand.MemorySummary -> LearnedIntent.MEMORY
            else -> LearnedIntent.ACTION
        })
        speakResponse(executeDirectCommand(command) ?: "Listo.")
    }

    private suspend fun predictIntent(text: String): OnlineIntentNetwork.Prediction? = withContext(Dispatchers.IO) {
        if (!EddyAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext null
        try {
            val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
            network.predict(text)
        } catch (_: Exception) {
            adaptiveUnavailable = true
            EddyRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
            null
        }
    }

    private suspend fun learnIntent(text: String, intent: LearnedIntent) = withContext(Dispatchers.IO) {
        if (!EddyAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext
        try {
            val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
            network.learn(text, intent)
            adaptiveStore.save(network)
        } catch (_: Exception) {
            adaptiveUnavailable = true
            EddyRuntimeState.setInputStatus(applicationContext, "No pude guardar el aprendizaje. Las órdenes siguen disponibles.")
        }
    }

    private suspend fun clearLocalMemory() = withContext(Dispatchers.IO) {
        memory.clearAll()
        adaptiveStore.clear()
        adaptiveNetwork = null
        adaptiveUnavailable = false
    }

    private fun looksLikeCapabilityRequest(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return listOf(
            "aprende a", "aprendé a", "programate", "programáte", "prográmate", "mejorate", "mejoráte", "mejórate",
            "agrega una funcion", "agregá una función", "agrega una función", "crea una funcion", "creá una función",
            "quiero que puedas", "necesito que puedas", "haz que puedas", "hacé que puedas", "convertite en", "conviértete en",
        ).any(value::contains)
    }

    private suspend fun executeDirectCommand(command: AssistantCommand): String? = when (command) {
        AssistantCommand.Greeting -> "Aquí estoy. Decime."
        AssistantCommand.TellTime -> "Son las ${SimpleDateFormat("h:mm a", Locale.forLanguageTag("es-NI")).format(Date())}."
        AssistantCommand.OpenCamera -> executor.openCamera().spokenMessage
        AssistantCommand.MemorySummary -> withContext(Dispatchers.IO) { memory.describeLearnedPatterns() }
        AssistantCommand.ClearMemory -> { clearLocalMemory(); "Borré mi memoria local." }
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

    private suspend fun researchReply(query: String, forceWeb: Boolean = true, openBrowser: Boolean = false): EddyAiReply {
        fun unavailable(message: String) = EddyAiReply(message, false, emptyList())
        if (AutonomousResearch.offlineOnly(query)) return unavailable("Una búsqueda web necesita conexión. Puedo seguir con las funciones locales.")
        if (!webClient.isConfigured) {
            val browser = if (openBrowser) " ${executor.searchWeb(query).spokenMessage}" else ""
            return unavailable("Para verificar información web, configurá GroqCloud en Ajustes.$browser")
        }
        EddyRuntimeState.setSearching(applicationContext, true)
        EddyRuntimeState.setResponse(applicationContext, "Investigando en Internet…")
        return try {
            val current = EddyRuntimeState.read(applicationContext).heardText
            val context = withContext(Dispatchers.IO) { memory.contextForAi(false) }
            val reply = webClient.reply(query, context, forceWeb, memory.historyForAi(current))
            when {
                reply == null -> unavailable(webClient.lastError ?: "No pude consultar Internet. Volvé a intentarlo.")
                // Search-enabled does not mean Google actually supplied evidence.
                forceWeb && !reply.webUsed -> unavailable("No obtuve fuentes web para verificarlo. Podés pedirme otra búsqueda más específica.")
                else -> reply.copy(evidence = AutonomousResearch.evidenceNote(reply.sources.map { it.url }))
            }
        } finally { EddyRuntimeState.setSearching(applicationContext, false) }
    }

    private suspend fun speakResearchResponse(question: String, reply: EddyAiReply) {
        val finalText = reply.text
        val evidenceNote = if (reply.webUsed) AutonomousResearch.evidenceNote(reply.sources.map { it.url }) else ""
        val displayed = if (evidenceNote.isBlank()) finalText else "$finalText\n\n$evidenceNote"
        EddyRuntimeState.setAiResponse(applicationContext, displayed, reply.webUsed, reply.sources)
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(finalText) }; speakOnly(finalText)
    }

    private suspend fun speakResponse(text: String) { EddyRuntimeState.setResponse(applicationContext, text); withContext(Dispatchers.IO) { memory.rememberAssistantTurn(text) }; speakOnly(text) }
    private fun speakOnly(text: String, continueCommand: Boolean = false) {
        if (text.isBlank() || destroyed) return
        continueAfterSpeech = continueCommand
        if (localVoiceActive) localVoice?.setAssistantSpeaking(true, continueCommand)
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
        val backend = speechOutput.choose(neuralTts.isAvailable)
        val queued = if (backend == SpeechOutputPolicy.Backend.NEURAL) {
            EddyRuntimeState.setVoiceStatus(applicationContext, "Voz local de EDDY · español de México · sin conexión")
            if (neuralTts.speak(text, replyProsody.speed)) true else {
                speechOutput.neuralFailed()
                EddyRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
                platformTts.speak(text, replyProsody)
            }
        } else {
            EddyRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
            platformTts.speak(text, replyProsody)
        }
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
                    isListening = localVoiceActive && localVoice?.isMicrophoneSilenced != true
                } else if (!isThinking) finishTurn()
            }
            updateVisualState()
        }
    }
    private fun updateVisualState() {
        EddyRuntimeState.setState(applicationContext, when { isSpeaking -> EddyRuntimeState.State.SPEAKING; isThinking || isTranscribing -> EddyRuntimeState.State.THINKING; isListening -> EddyRuntimeState.State.LISTENING; else -> EddyRuntimeState.State.IDLE })
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_eddy_notification).setContentTitle("Activación por voz de EDDY").setContentText("Escucha local habilitada. Abrí EDDY para ver su estado.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0, "Detener", stop).build()
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
        private const val BUBBLE_PREFS = "eddy_bubble_prefs"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        const val ACTION_STOP = "com.eddy.assistant.action.STOP"
        const val ACTION_SHOW_BUBBLE = "com.eddy.assistant.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.eddy.assistant.action.HIDE_BUBBLE"
        const val ACTION_REFRESH_BUBBLE = "com.eddy.assistant.action.REFRESH_BUBBLE"
    }
}
