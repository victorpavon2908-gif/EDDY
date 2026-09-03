package com.niko.assistant.background

import com.niko.assistant.compat.UpgradeIdentity

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
import com.niko.assistant.NikoWakeActivity
import com.niko.assistant.MainActivity
import com.niko.assistant.R
import com.niko.assistant.actions.ActionExecutor
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ai.AutonomousResearch
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.NikoAiClient
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.ai.NikoFallbackConversation
import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.brain.LocalBrain
import com.niko.assistant.brain.NikoSemanticActionResolver
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.learning.AdaptiveIntentStore
import com.niko.assistant.learning.OnlineIntentNetwork
import com.niko.assistant.learning.LearnedIntent
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoLocalLlm
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelProgress
import com.niko.assistant.localai.NikoVoiceProfile
import com.niko.assistant.memory.NikoMemory
import com.niko.assistant.programming.NikoCodeAgent
import com.niko.assistant.proactive.NikoProactiveScheduler
import com.niko.assistant.smarthome.LocalSmartHomeClient
import com.niko.assistant.voice.NikoLocalVoiceEngine
import com.niko.assistant.voice.NikoNeuralTextToSpeech
import com.niko.assistant.voice.NikoTextToSpeech
import com.niko.assistant.voice.VoiceRecoveryPolicy
import com.niko.assistant.voice.SpeechOutputPolicy
import com.niko.assistant.voice.SpeechProsody
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.niko.assistant.voice.VoiceControl
import com.niko.assistant.voice.LeoRealtimeTurnBus
import kotlinx.coroutines.withContext

open class NikoAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var brain: LocalBrain
    private lateinit var semanticActions: NikoSemanticActionResolver
    private lateinit var executor: ActionExecutor
    private lateinit var smartHome: LocalSmartHomeClient
    private lateinit var memory: NikoMemory
    private lateinit var webClient: NikoAiClient
    private lateinit var fallbackConversation: NikoFallbackConversation
    private lateinit var proactiveScheduler: NikoProactiveScheduler
    private lateinit var modelManager: NikoModelManager
    private lateinit var deviceProfile: NikoDeviceProfile
    private lateinit var ownerVoice: NikoVoiceProfile
    private lateinit var localLlm: NikoLocalLlm
    private lateinit var codeAgent: NikoCodeAgent
    private val adaptiveStore by lazy { AdaptiveIntentStore(File(filesDir, "adaptive_learning")) }
    private var adaptiveNetwork: OnlineIntentNetwork? = null
    private var adaptiveUnavailable = false
    private var replyProsody = SpeechProsody()
    private val speechOutput = SpeechOutputPolicy()
    private var localVoice: NikoLocalVoiceEngine? = null
    private var localVoiceActive = false
    private lateinit var platformTts: NikoTextToSpeech
    private lateinit var neuralTts: NikoNeuralTextToSpeech

    private var destroyed = false
    private var foregroundReady = false
    private var localVoiceStarting = false
    private var localVoiceEpoch = 0
    private var isTranscribing = false
    private var commandJob: Job? = null
    private var turnEpoch = 0L
    private val turnInterrupter: () -> Unit = { serviceScope.launch { interruptCurrentTurn() }; Unit }
    private var speechTimeout: Job? = null
    private var continueAfterSpeech = false
    private var recoveryJob: Job? = null
    private val voiceRecovery = VoiceRecoveryPolicy()
    private var initializationFailure: NikoLocalVoiceEngine.InitializationFailure? = null
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
        LeoRealtimeTurnBus.registerTurnInterrupter(turnInterrupter)
        brain = LocalBrain()
        executor = ActionExecutor(applicationContext)
        smartHome = LocalSmartHomeClient(applicationContext)
        memory = NikoMemory(applicationContext)
        webClient = NikoAiClient(applicationContext)
        fallbackConversation = NikoFallbackConversation()
        proactiveScheduler = NikoProactiveScheduler(applicationContext, memory)
        modelManager = NikoModelManager(applicationContext)
        deviceProfile = NikoDeviceProfile.detect(applicationContext)
        ownerVoice = NikoVoiceProfile(applicationContext)
        localLlm = NikoLocalLlm(applicationContext, modelManager)
        semanticActions = NikoSemanticActionResolver(brain) { prompt -> localLlm.completeStructured(prompt) }
        codeAgent = NikoCodeAgent(applicationContext)

        platformTts = NikoTextToSpeech(
            context = applicationContext,
            onReady = { ready ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) NikoRuntimeState.setVoiceReady(applicationContext, ready)
            },
            onVoiceSelected = { description ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) NikoRuntimeState.setVoiceStatus(applicationContext, description)
            },
            onSpeakingChanged = ::onSpeakingChanged,
        )
        neuralTts = NikoNeuralTextToSpeech(
            models = modelManager,
            profile = deviceProfile,
            onSpeakingChanged = ::onSpeakingChanged,
            canUseFallback = { platformTts.isReady },
            onFailure = { text, audioStarted -> serviceScope.launch {
                if (!destroyed) {
                    speechOutput.neuralFailed()
                    NikoRuntimeState.setVoiceStatus(applicationContext, "La voz local se interrumpió. ${platformTts.voiceDescription}")
                    if (audioStarted || !platformTts.speak(text, replyProsody)) onSpeakingChanged(false)
                    NikoRuntimeState.setVoiceReady(applicationContext, platformTts.isReady)
                }
            } },
        )

        if (!NikoVoiceSettings.enabled(this) || !hasMicrophonePermission()) {
            NikoRuntimeState.setResponse(applicationContext, "Abrí LEO y concedé el permiso de micrófono.")
            stopSelf()
            return
        }
        createNotificationChannels()
        try { startAsForeground() } catch (_: RuntimeException) {
            NikoRuntimeState.setResponse(applicationContext, "Abrí LEO para activar el micrófono.")
            stopSelf()
            return
        }
        foregroundReady = true
        acquireCpuWakeLock()
        registerScreenStateReceiver()
        NikoRuntimeState.setRunning(applicationContext, true)
        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Preparando activación por voz…")
        NikoRuntimeState.setResponse(applicationContext, "Estoy preparando la escucha local. Cuando esté lista, decí LEO.")
        ensureVoiceListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { NikoVoiceSettings.setEnabled(this, false); stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
        }
        if (!foregroundReady || !NikoVoiceSettings.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        ensureVoiceListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        ++turnEpoch
        LeoRealtimeTurnBus.unregisterTurnInterrupter(turnInterrupter)
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
        NikoRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    /** One owner for preparation, native startup and recovery. No Android recognition sessions. */
    private fun ensureVoiceListening(initialDelay: Long = 0L) {
        if (destroyed || !foregroundReady || localVoiceActive || recoveryJob?.isActive == true || !NikoVoiceSettings.enabled(this)) return
        recoveryJob = serviceScope.launch {
            if (initialDelay > 0) delay(initialDelay)
            while (!destroyed && NikoVoiceSettings.enabled(this@NikoAssistantService)) {
                if (!hasMicrophonePermission()) {
                    inputUnavailable("Concedé permiso de micrófono en los ajustes de Android y volvé a abrir LEO.")
                    return@launch
                }
                while (isThinking || isSpeaking || commandJob?.isActive == true) delay(250L)
                localVoiceStarting = true
                try {
                    val previous = localVoice
                    val released = withContext(Dispatchers.IO) { previous?.stopAndAwait() ?: true }
                    if (!released) {
                        inputUnavailable("El micrófono anterior aún se está cerrando. Voy a reintentar.")
                    } else {
                        localVoice = null
                        val failure = initializationFailure
                        initializationFailure = null
                        val failedModel = failure?.model
                        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Preparando escucha local…")
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

    private fun onModelProgress(progress: NikoModelProgress) {
        if (progress.state !in setOf(NikoModelProgress.State.INSTALLING, NikoModelProgress.State.DOWNLOADING)) return
        val amount = if (progress.totalBytes > 0) " · ${progress.downloadedBytes * 100 / progress.totalBytes}%" else ""
        serviceScope.launch {
            if (!destroyed && localVoiceStarting) NikoRuntimeState.setInputStatus(applicationContext, "Preparando voz local$amount")
        }
    }

    private fun inputUnavailable(message: String) {
        isListening = false
        isTranscribing = false
        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.ERROR, message)
        if (!isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, message)
        updateVisualState()
    }

    private suspend fun startLocalVoiceIfReady(): Boolean {
        if (localVoiceActive || !modelManager.coreReady() || !hasMicrophonePermission()) return localVoiceActive
        val epoch = ++localVoiceEpoch
        val engine = NikoLocalVoiceEngine(
            context = applicationContext,
            models = modelManager,
            profile = deviceProfile,
            ownerVoice = ownerVoice,
            onState = { voiceState -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                when (voiceState) {
                    NikoLocalVoiceEngine.State.PASSIVE -> {
                        if (isListening && !isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                    NikoLocalVoiceEngine.State.VERIFYING, NikoLocalVoiceEngine.State.PROCESSING -> { isTranscribing = true; isListening = false; updateVisualState() }
                    NikoLocalVoiceEngine.State.ACTIVE -> { isTranscribing = false; isListening = !isThinking && !isSpeaking; updateVisualState() }
                    NikoLocalVoiceEngine.State.SPEAKING -> { isListening = false; updateVisualState() }
                    NikoLocalVoiceEngine.State.STOPPED -> {
                        if (localVoiceActive) recoverLocalVoice("El motor local se detuvo. Voy a recuperar la escucha.")
                    }
                }
            }},
            onWake = { _, _ -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                NikoRuntimeState.setHeard(applicationContext, "LEO")
                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                revealNikoOnLockScreen()
                if (localLlm.isAvailable) serviceScope.launch { localLlm.prewarm() }
            }},
            onAwaitingCommand = { prompt, retry -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                isTranscribing = false
                if (!isThinking && !isSpeaking) {
                    NikoRuntimeState.setResponse(applicationContext, prompt)
                    speakOnly(prompt, continueCommand = retry)
                }
            } },
            onCommand = { text -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) { isTranscribing = false; submitCommand(text) }
            } },
            onUnauthorizedVoice = { serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    isTranscribing = false
                    NikoRuntimeState.setResponse(applicationContext, "No distinguí tu voz con claridad. Repetí la frase cerca del teléfono.")
                    updateVisualState()
                }
            } },
            onMicrophoneSilenced = { silenced -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    if (silenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
                    else {
                        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, "Activación local lista · decí LEO")
                        if (!isSpeaking && !isThinking) NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                    }
                }
            } },
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    NikoRuntimeState.setInputStatus(applicationContext, error)
                    if (localVoiceActive) recoverLocalVoice(error)
                }
            } },
        )

        NikoRuntimeState.setInputStatus(applicationContext, "Iniciando micrófono local…")
        localVoice = engine
        val started = withContext(Dispatchers.IO) { engine.start() }
        if (destroyed) { engine.stop(); return false }
        return if (started && engine.isRunning) {
            localVoiceActive = true
            neuralTts.prewarm()
            isListening = false
            initializationFailure = null
            voiceRecovery.started(SystemClock.elapsedRealtime())
            if (engine.isMicrophoneSilenced) inputUnavailable("Android silenció el micrófono. Revisá su interruptor de privacidad o cerrá la otra app que lo usa.")
            else {
                NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, "Activación local lista · decí LEO")
                NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
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

    /** Main-thread turn ownership; canceled jobs must never reset a newer command window. */
    private fun interruptCurrentTurn() {
        if (destroyed) return
        ++turnEpoch
        commandJob?.cancel()
        commandJob = null
        speechTimeout?.cancel()
        speechTimeout = null
        continueAfterSpeech = false
        isThinking = false
        isSpeaking = false
        isTranscribing = false
        localVoice?.setAssistantBusy(false)
        NikoRuntimeState.setSearching(applicationContext, false)
        updateVisualState()
    }

    private fun submitCommand(text: String) {
        if (destroyed) return
        VoiceControl.parse(text)?.let { control ->
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
            localVoice?.cancelConversation()
            isListening = false
            NikoRuntimeState.setHeard(applicationContext, text)
            if (control == VoiceControl.DEACTIVATE) {
                NikoVoiceSettings.setEnabled(this, false)
                ++localVoiceEpoch
                localVoiceActive = false
                localVoice?.stop()
                NikoRuntimeState.setResponse(applicationContext, "LEO desactivado. Podés activarme de nuevo desde la app.")
                stopSelf()
            } else {
                NikoRuntimeState.setResponse(applicationContext, "Detenido. Decí LEO cuando me necesités.")
            }
            updateVisualState()
            return
        }
        if (isSpeaking || isThinking || commandJob?.isActive == true) return
        val epoch = ++turnEpoch
        isListening = false
        isThinking = true
        NikoRuntimeState.setResponse(applicationContext, "Procesando tu petición…")
        isTranscribing = false
        localVoice?.setAssistantBusy(true)
        NikoRuntimeState.setHeard(applicationContext, text)
        revealNikoOnLockScreen()
        updateVisualState()
        commandJob = serviceScope.launch {
            try {
                handleCommand(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == turnEpoch) speakResponse("No pude completar eso. Volvé a llamarme y lo intentamos.")
            } finally {
                if (epoch == turnEpoch && !destroyed) {
                    isThinking = false
                    if (!isSpeaking) finishTurn()
                    updateVisualState()
                }
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
        WebQueryRouter.explicitQuery(text)?.takeIf { AutonomousResearch.allowedFor(text) }?.let { query ->
            learnIntent(query, LearnedIntent.SEARCH)
            speakResearchResponse(query, researchReply(query))
            return
        }
        com.niko.assistant.ai.NikoIdentity.replyTo(text)?.let { speakResponse(it); return }
        withContext(Dispatchers.IO) { memory.learnExplicitly(text) }?.let {
            learnIntent(text, LearnedIntent.MEMORY)
            speakResponse(it); return
        }
        NikoMathEngine.solve(text)?.let { learnIntent(text, LearnedIntent.ACTION); speakResponse("El resultado es $it."); return }
        val commands = semanticActions.resolveMany(text)
        if (commands.size > 1) {
            val responses = mutableListOf<String>()
            val sources = mutableListOf<NikoWebSource>()
            for (command in commands) {
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                if (command is AssistantCommand.SearchWeb) {
                    learnIntent(command.query, LearnedIntent.SEARCH)
                    val answer = researchReply(command.query, openBrowser = true)
                    responses.add(answer.text)
                    sources.addAll(answer.sources)
                    continue
                }
                val direct = executeDirectCommand(command)
                if (!direct.isNullOrBlank()) {
                    responses.add(direct)
                    withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
                }
                delay(120L)
            }
            val answer = responses.joinToString(" ").ifBlank { "No entendí qué acciones querés que haga." }
            if (sources.isEmpty()) speakResponse(answer)
            else speakResearchResponse(text, NikoAiReply(answer, true, sources.distinctBy { it.url }.take(8)))
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
            val remoteContext = withContext(Dispatchers.IO) { memory.contextForAi(false, text) }
            val history = memory.historyForAi(text)
            val answer = ConversationCoordinator.reply(
                message = text,
                localFirst = NikoAiSettings.localFirst(applicationContext),
                autoResearch = NikoAiSettings.autoResearch(applicationContext),
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
            if (WebQueryRouter.needsCurrentInformation(text) && AutonomousResearch.allowedFor(text)) learnIntent(text, LearnedIntent.SEARCH)
            if (looksLikeCapabilityRequest(text)) {
                val plan = codeAgent.analyze(text)
                codeAgent.registerNativeProposal(plan.capability, "${plan.strategy}: ${plan.explanation}", answer.text, com.niko.assistant.BuildConfig.VERSION_NAME)
            }
            speakResearchResponse(text, answer)
            return
        }
        learnIntent(text, when (command) {
            AssistantCommand.Greeting -> LearnedIntent.CONVERSATION
            AssistantCommand.MemorySummary -> LearnedIntent.MEMORY
            else -> LearnedIntent.ACTION
        })
        val direct = executeDirectCommand(command) ?: "Listo."
        withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
        speakResponse(direct)
    }

    private suspend fun predictIntent(text: String): OnlineIntentNetwork.Prediction? = withContext(Dispatchers.IO) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext null
        try {
            val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
            network.predict(text)
        } catch (_: Exception) {
            adaptiveUnavailable = true
            NikoRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
            null
        }
    }

    private suspend fun learnIntent(text: String, intent: LearnedIntent) = withContext(Dispatchers.IO) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext
        try {
            val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
            network.learn(text, intent)
            adaptiveStore.save(network)
        } catch (_: Exception) {
            adaptiveUnavailable = true
            NikoRuntimeState.setInputStatus(applicationContext, "No pude guardar el aprendizaje. Las órdenes siguen disponibles.")
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

    private suspend fun researchReply(query: String, forceWeb: Boolean = true, openBrowser: Boolean = false): NikoAiReply {
        fun unavailable(message: String) = NikoAiReply(message, false, emptyList())
        if (AutonomousResearch.offlineOnly(query)) return unavailable("Una búsqueda web necesita conexión. Puedo seguir con las funciones locales.")
        if (!webClient.isConfigured) {
            val browser = if (openBrowser) " ${executor.searchWeb(query).spokenMessage}" else ""
            return unavailable("Para verificar información web, configurá GroqCloud en Ajustes.$browser")
        }
        val researchEpoch = turnEpoch
        NikoRuntimeState.setSearching(applicationContext, true)
        NikoRuntimeState.setResponse(applicationContext, "Investigando en Internet…")
        return try {
            val current = NikoRuntimeState.read(applicationContext).heardText
            val context = withContext(Dispatchers.IO) { memory.contextForAi(false, query) }
            val reply = webClient.reply(query, context, forceWeb, memory.historyForAi(current))
            when {
                reply == null -> unavailable(webClient.lastError ?: "No pude consultar Internet. Volvé a intentarlo.")
                forceWeb && !reply.webUsed -> reply
                else -> reply
            }
        } finally { if (researchEpoch == turnEpoch) NikoRuntimeState.setSearching(applicationContext, false) }
    }

    private suspend fun speakResearchResponse(question: String, reply: NikoAiReply) {
        currentCoroutineContext().ensureActive()
        val finalText = reply.text
        val evidenceNote = if (reply.webUsed) AutonomousResearch.evidenceNote(reply.sources.map { it.url }) else ""
        val displayed = if (evidenceNote.isBlank()) finalText else "$finalText\n\n$evidenceNote"
        NikoRuntimeState.setAiResponse(applicationContext, displayed, reply.webUsed, reply.sources)
        speakOnly(finalText)
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(finalText) }
    }

    private suspend fun speakResponse(text: String) {
        currentCoroutineContext().ensureActive()
        NikoRuntimeState.setResponse(applicationContext, text)
        speakOnly(text)
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(text) }
    }
    private fun speakOnly(text: String, continueCommand: Boolean = false) {
        if (text.isBlank() || destroyed) return
        continueAfterSpeech = continueCommand
        if (localVoiceActive) localVoice?.setAssistantSpeaking(true, continueCommand)
        isSpeaking = true
        updateVisualState()
        speechTimeout?.cancel()
        speechTimeout = serviceScope.launch {
            delay((text.length * 110L + 5_000L).coerceIn(12_000L, 360_000L))
            if (!destroyed && isSpeaking) {
                platformTts.stop()
                neuralTts.stop()
                onSpeakingChanged(false)
            }
        }
        val backend = speechOutput.choose(neuralTts.isAvailable)
        val queued = if (backend == SpeechOutputPolicy.Backend.NEURAL) {
            NikoRuntimeState.setVoiceStatus(applicationContext, "Voz local de LEO · español de México · sin conexión")
            if (neuralTts.speak(text, replyProsody.speed)) true else {
                speechOutput.neuralFailed()
                NikoRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
                platformTts.speak(text, replyProsody)
            }
        } else {
            NikoRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
            platformTts.speak(text, replyProsody)
        }
        if (queued) NikoRuntimeState.setVoiceReady(applicationContext, true)
        else { NikoRuntimeState.setVoiceReady(applicationContext, false); onSpeakingChanged(false) }
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
        NikoRuntimeState.setState(applicationContext, when { isSpeaking -> NikoRuntimeState.State.SPEAKING; isThinking || isTranscribing -> NikoRuntimeState.State.THINKING; isListening -> NikoRuntimeState.State.LISTENING; else -> NikoRuntimeState.State.IDLE })
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
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "LEO local activo", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(WAKE_CHANNEL_ID, "Despertar LEO", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false); enableVibration(false); setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }
    private fun startAsForeground() {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 11, UpgradeIdentity.assistantService(this).apply { action = ACTION_STOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_niko_notification).setContentTitle("Activación por voz de LEO").setContentText("Escucha local habilitada. Abrí LEO para ver su estado.").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0, "Detener", stop).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTIFICATION_ID, notification)
    }
    private fun revealNikoOnLockScreen() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) return
        val show = PendingIntent.getActivity(this, WAKE_REQUEST_CODE, UpgradeIdentity.wakeActivity(this).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, WAKE_CHANNEL_ID).setSmallIcon(R.drawable.ic_niko_notification).setContentTitle("LEO").setContentText("Te escucho.").setContentIntent(show).setFullScreenIntent(show, true).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        getSystemService(NotificationManager::class.java)?.notify(WAKE_NOTIFICATION_ID, notification)
    }

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this) || bubbleView != null) return
        val wm = getSystemService(WindowManager::class.java); windowManager = wm
        val size = dp(66); val container = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(238, 255, 255, 255)); setStroke(dp(2), Color.rgb(0, 205, 160)) }
            elevation = dp(14).toFloat(); setPadding(dp(10), dp(10), dp(10), dp(10)); addView(ImageView(this@NikoAssistantService).apply { setImageResource(R.drawable.ic_niko_mark); scaleType = ImageView.ScaleType.CENTER_INSIDE }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
    private fun acquireCpuWakeLock() { if (cpuWakeLock?.isHeld == true) return; cpuWakeLock = (getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NIKO:AlwaysOnWake")?.apply { setReferenceCounted(false); acquire() }) }
    private fun releaseCpuWakeLock() { cpuWakeLock?.let { if (it.isHeld) runCatching { it.release() } }; cpuWakeLock = null }

    companion object {
        private const val CHANNEL_ID = UpgradeIdentity.assistantChannel
        private const val WAKE_CHANNEL_ID = UpgradeIdentity.wakeChannel
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_NOTIFICATION_ID = 2002
        private const val WAKE_REQUEST_CODE = 2102
        private const val BUBBLE_PREFS = UpgradeIdentity.bubblePreferences
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        const val ACTION_STOP = UpgradeIdentity.ACTION_STOP
        const val ACTION_SHOW_BUBBLE = UpgradeIdentity.ACTION_SHOW_BUBBLE
        const val ACTION_HIDE_BUBBLE = UpgradeIdentity.ACTION_HIDE_BUBBLE
        const val ACTION_REFRESH_BUBBLE = UpgradeIdentity.ACTION_REFRESH_BUBBLE
    }
}
