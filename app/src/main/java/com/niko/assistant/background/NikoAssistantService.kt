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
import com.niko.assistant.ui.robot.RobotMotion
import com.niko.assistant.ui.robot.RobotMotionBus
import com.niko.assistant.ui.generated.GeneratedToolStore
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
import com.niko.assistant.media.CaptureGate
import kotlinx.coroutines.cancelAndJoin
import com.niko.assistant.actions.ActionExecutor
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ai.AutonomousResearch
import com.niko.assistant.ai.ConversationCoordinator
import com.niko.assistant.ai.GeneratedToolPlanner
import com.niko.assistant.ai.NikoAiClient
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.ai.NikoFallbackConversation
import com.niko.assistant.brain.AssistantCommand
import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.brain.LocalBrain
import com.niko.assistant.brain.NikoSemanticActionResolver
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.devicecontrol.NikoUiAutomationAgent
import com.niko.assistant.devicecontrol.NikoUiTaskPolicy
import com.niko.assistant.learning.AdaptiveIntentStore
import com.niko.assistant.learning.InteractionCorrection
import com.niko.assistant.learning.LearnedIntent
import com.niko.assistant.learning.LearnedActionCodec
import com.niko.assistant.learning.LearnedActionStore
import com.niko.assistant.learning.LeoIntentTrainingCorpus
import com.niko.assistant.learning.OnlineIntentNetwork
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoLocalLlm
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelProgress
import com.niko.assistant.localai.NikoVoiceProfile
import com.niko.assistant.memory.NikoMemory
import com.niko.assistant.programming.NikoCodeAgent
import com.niko.assistant.ui.NikoUiMode
import com.niko.assistant.ui.NikoUiModeStore
import com.niko.assistant.proactive.NikoProactiveScheduler
import com.niko.assistant.smarthome.LocalSmartHomeClient
import com.niko.assistant.voice.NikoLocalVoiceEngine
import com.niko.assistant.voice.NikoNeuralTextToSpeech
import com.niko.assistant.voice.NikoTextToSpeech
import com.niko.assistant.voice.VoiceRecoveryPolicy
import com.niko.assistant.voice.SpeechOutputPolicy
import com.niko.assistant.voice.ProgressiveSpeech
import com.niko.assistant.voice.SpeechProsody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
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
import com.niko.assistant.voice.LeoVoiceDiagnostics
import com.niko.assistant.voice.LeoRealtimeTurnBus
import com.niko.assistant.voice.LeoPlatformVoiceEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

open class NikoAssistantService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // El servicio de micrófono debe llegar a startForeground() antes de construir
    // componentes pesados. Todo lo demás se crea únicamente cuando una orden lo necesita.
    private val brain by lazy(LazyThreadSafetyMode.NONE) { LocalBrain() }
    private val executor by lazy(LazyThreadSafetyMode.NONE) { ActionExecutor(applicationContext) }
    private val smartHome by lazy(LazyThreadSafetyMode.NONE) { LocalSmartHomeClient(applicationContext) }
    private val memory by lazy(LazyThreadSafetyMode.NONE) { NikoMemory(applicationContext) }
    private val webClient by lazy(LazyThreadSafetyMode.NONE) { NikoAiClient(applicationContext) }
    private val generatedToolPlanner by lazy(LazyThreadSafetyMode.NONE) { GeneratedToolPlanner(applicationContext) }
    private val fallbackConversation by lazy(LazyThreadSafetyMode.NONE) { NikoFallbackConversation() }
    private val proactiveScheduler by lazy(LazyThreadSafetyMode.NONE) { NikoProactiveScheduler(applicationContext, memory) }
    private val modelManager by lazy(LazyThreadSafetyMode.NONE) { NikoModelManager(applicationContext) }
    private val deviceProfile by lazy(LazyThreadSafetyMode.NONE) { NikoDeviceProfile.detect(applicationContext) }
    private val ownerVoice by lazy(LazyThreadSafetyMode.NONE) { NikoVoiceProfile(applicationContext) }

    private val localLlmDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoLocalLlm(applicationContext, modelManager)
    }
    private val localLlm: NikoLocalLlm get() = localLlmDelegate.value

    private val uiAutomation by lazy(LazyThreadSafetyMode.NONE) { NikoUiAutomationAgent(localLlm) }
    private val semanticActions by lazy(LazyThreadSafetyMode.NONE) {
        NikoSemanticActionResolver(brain) { prompt -> localLlm.completeStructured(prompt) }
    }
    private val codeAgent by lazy(LazyThreadSafetyMode.NONE) { NikoCodeAgent(applicationContext) }
    private val adaptiveStore by lazy {
        AdaptiveIntentStore(File(filesDir, "adaptive_learning")) {
            runCatching { assets.open(LeoIntentTrainingCorpus.ASSET_NAME).use { it.readBytes() } }.getOrNull()
        }
    }
    private val learnedActionStore by lazy { LearnedActionStore(File(filesDir, "adaptive_learning")) }
    @Volatile private var adaptiveNetwork: OnlineIntentNetwork? = null
    @Volatile private var adaptiveUnavailable = false
    @Volatile private var learningEpoch = 0L
    private val adaptiveLoading = AtomicBoolean(false)
    private val learningMutex = Mutex()
    private var lastTrainableUtterance: String? = null
    private var lastCommandSucceeded = false
    private var replyProsody = SpeechProsody()
    private val speechOutput = SpeechOutputPolicy()
    private var localVoice: NikoLocalVoiceEngine? = null
    private var platformVoice: LeoPlatformVoiceEngine? = null
    private var localVoiceActive = false
    private val platformTtsDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoTextToSpeech(
            context = applicationContext,
            onReady = { ready ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) {
                    NikoRuntimeState.setVoiceReady(applicationContext, ready)
                }
            },
            onVoiceSelected = { description ->
                if (speechOutput.selected != SpeechOutputPolicy.Backend.NEURAL) {
                    NikoRuntimeState.setVoiceStatus(applicationContext, description)
                }
            },
            onSpeakingChanged = ::onSpeakingChanged,
        )
    }
    private val platformTts: NikoTextToSpeech get() = platformTtsDelegate.value

    private val neuralTtsDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NikoNeuralTextToSpeech(
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
    }
    private val neuralTts: NikoNeuralTextToSpeech get() = neuralTtsDelegate.value

    private var destroyed = false
    private var foregroundReady = false
    @Volatile private var voiceRequested = false
    @Volatile private var platformVoiceDisabledForSession = false
    private var localVoiceStarting = false
    private var localVoiceEpoch = 0
    private var isTranscribing = false
    private var commandJob: Job? = null
    private var progressiveSpeech: ProgressiveSpeech? = null
    private val agent = com.niko.assistant.agent.LeoAgentRuntime()
    private val taskExecutor = com.niko.assistant.agent.LeoTaskExecutor()
    private val skillRegistry by lazy {
        com.niko.assistant.skills.LeoSkillRegistry().also {
            com.niko.assistant.skills.LeoBuiltInSkills.install(it, ::dispatchDirectCommand)
        }
    }
    private val turnEpoch: Long get() = agent.turns.current
    private val turnInterrupter: () -> Unit = {
        agent.turns.cancel() // Invalidate producer callbacks before posting to the main thread.
        serviceScope.launch { interruptCurrentTurn() }; Unit
    }
    private var speechTimeout: Job? = null
    private var continueAfterSpeech = false
    private var recoveryJob: Job? = null
    private val voiceRecovery = VoiceRecoveryPolicy()
    private var initializationFailure: NikoLocalVoiceEngine.InitializationFailure? = null
    private val initiativeStore by lazy { com.niko.assistant.proactive.LeoInitiativeStore(applicationContext) }
    private val companionInitiative by lazy { com.niko.assistant.proactive.LeoInitiativeEngine().apply { restore(initiativeStore.read()) } }
    private var initiativeJob: Job? = null
    private var conversationWindowJob: Job? = null
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
        if (!hasMicrophonePermission()) {
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

        // Inicializá la voz del sistema cuanto antes. Antes se construía por primera vez
        // al terminar una orden; si todavía no estaba lista, el código elegía la voz neural
        // ONNX y podía abortar el proceso justo después de mostrar "Procesando tu petición…".
        // Esto NO cambia la escucha: solo prepara la salida de voz segura de Android.
        runCatching { platformTts.isReady }

        CaptureGate.pauseVoice = {
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
            ++localVoiceEpoch
            recoveryJob?.cancelAndJoin()
            localVoiceActive = false
            stopActiveVoiceAndAwait()
        }
        CaptureGate.resumeVoice = { ensureVoiceListening() }
        acquireCpuWakeLock()
        registerScreenStateReceiver()
        NikoRuntimeState.setRunning(applicationContext, true)
        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Preparando activación por voz…")
        NikoRuntimeState.setResponse(applicationContext, "LEO está listo para activar el micrófono.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                voiceRequested = true
                NikoVoiceSettings.setEnabled(this, true)
            }
            ACTION_STOP -> {
                voiceRequested = false
                NikoVoiceSettings.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_BUBBLE -> showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
            ACTION_REFRESH_BUBBLE -> { hideBubble(); showBubble() }
            ACTION_RESET_BUBBLE -> resetBubblePosition()
        }
        if (!foregroundReady) { stopSelf(); return START_NOT_STICKY }
        if (!voiceRequested && intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureVoiceListening()
        if (initiativeJob?.isActive != true) initiativeJob = serviceScope.launch {
            while (!destroyed) {
                delay(15_000L)
                val foreground = com.niko.assistant.LeoApplication.foregroundActivity?.get()
                val audio = getSystemService(android.media.AudioManager::class.java)
                val available = foreground is MainActivity && localVoiceActive &&
                    audio != null && audio.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL &&
                    audio.mode == android.media.AudioManager.MODE_NORMAL && !audio.isMusicActive &&
                    NikoUiModeStore.read(applicationContext) == NikoUiMode.ASSISTANT &&
                    !isListening && !isThinking && !isSpeaking && !isTranscribing &&
                    commandJob?.isActive != true && !CaptureGate.held &&
                    !activeVoiceMicrophoneSilenced() && platformTts.isReady
                val now = System.currentTimeMillis()
                val calendar = java.util.Calendar.getInstance()
                val enabled = NikoAiSettings.companionInitiative(applicationContext)
                val candidates = if (enabled && available) withContext(Dispatchers.IO) { memory.initiativeCandidates(now) } else emptyList()
                // Recheck after IO; a user may have started speaking while evidence was loading.
                val stillAvailable = available && !isListening && !isThinking && !isSpeaking && !isTranscribing && commandJob?.isActive != true
                val invitation = companionInitiative.decide(now,
                    "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}",
                    calendar.get(java.util.Calendar.HOUR_OF_DAY), enabled, stillAvailable, candidates)
                initiativeStore.save(companionInitiative.state)
                if (invitation != null) {
                    try { speakResponse(invitation.text) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        com.niko.assistant.diagnostics.LeoCrashRecorder.recordHandled("CompanionInitiative", error)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        CaptureGate.pauseVoice = null
        CaptureGate.resumeVoice = null
        agent.interrupt()
        LeoRealtimeTurnBus.unregisterTurnInterrupter(turnInterrupter)
        LeoVoiceDiagnostics.cancelResponseTiming()
        progressiveSpeech?.cancel()
        progressiveSpeech = null
        commandJob?.cancel()
        speechTimeout?.cancel()
        recoveryJob?.cancel()
        bubbleParams?.let(::saveBubblePosition)
        hideBubble()
        unregisterScreenStateReceiver()
        stopActiveVoice()
        if (neuralTtsDelegate.isInitialized()) neuralTts.shutdown()
        if (platformTtsDelegate.isInitialized()) platformTts.shutdown()
        if (localLlmDelegate.isInitialized()) localLlm.release()
        releaseCpuWakeLock()
        serviceScope.cancel()
        NikoRuntimeState.reset(applicationContext)
        super.onDestroy()
    }

    /** One owner for preparation, native startup and recovery. No Android recognition sessions. */
    private fun ensureVoiceListening(initialDelay: Long = 0L) {
        if (CaptureGate.held || destroyed || !foregroundReady || localVoiceActive || recoveryJob?.isActive == true || !voiceRequested) return
        recoveryJob = serviceScope.launch {
            if (initialDelay > 0) delay(initialDelay)
            while (!CaptureGate.held && !destroyed && voiceRequested) {
                if (!hasMicrophonePermission()) {
                    inputUnavailable("Concedé permiso de micrófono en los ajustes de Android y volvé a abrir LEO.")
                    return@launch
                }
                while (isThinking || isSpeaking || commandJob?.isActive == true) delay(250L)
                localVoiceStarting = true
                try {
                    val released = stopActiveVoiceAndAwait()
                    if (!released) {
                        inputUnavailable("El micrófono anterior aún se está cerrando. Voy a reintentar.")
                    } else {
                        localVoice = null
                        platformVoice = null
                        NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.PREPARING, "Iniciando micrófono…")

                        // Ruta primaria: el reconocedor de Android evita cargar JNI/ONNX solo para
                        // poder abrir el micrófono. Es especialmente útil en dispositivos donde
                        // el KWS nativo puede abortar el proceso.
                        if (!platformVoiceDisabledForSession && startPlatformVoiceIfAvailable()) return@launch

                        // Respaldo totalmente local: solo se usa si Android no ofrece un
                        // RecognitionService compatible.
                        val failure = initializationFailure
                        initializationFailure = null
                        val failedModel = failure?.model
                        NikoRuntimeState.setInputStatus(applicationContext, "Preparando activación local…")
                        val ready = withContext(Dispatchers.IO) {
                            if (failedModel != null && voiceRecovery.allowModelRepair(failedModel.id)) {
                                modelManager.repair(failedModel, ::onModelProgress)
                            }
                            modelManager.ensureRecommended(deviceProfile, ::onModelProgress)
                        }
                        if (ready && startLocalVoiceIfReady()) return@launch
                        if (!ready) inputUnavailable("No pude preparar el motor de voz. Revisá Internet, espacio y permiso de micrófono.")
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

    private fun startPlatformVoiceIfAvailable(): Boolean {
        if (CaptureGate.held || !hasMicrophonePermission()) return false
        val epoch = ++localVoiceEpoch
        val engine = LeoPlatformVoiceEngine(
            context = applicationContext,
            onState = { state -> serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                when (state) {
                    LeoPlatformVoiceEngine.State.PASSIVE -> {
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.ACTIVE -> {
                        isTranscribing = false
                        isListening = !isThinking && !isSpeaking
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.PROCESSING -> {
                        isTranscribing = true
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.SPEAKING -> {
                        isListening = false
                        updateVisualState()
                    }
                    LeoPlatformVoiceEngine.State.STOPPED -> {
                        isTranscribing = false
                        isListening = false
                        updateVisualState()
                    }
                }
            }},
            onWake = { serviceScope.launch {
                if (destroyed || epoch != localVoiceEpoch) return@launch
                NikoRuntimeState.setHeard(applicationContext, "LEO")
                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                revealNikoOnLockScreen()
            }},
            onCommand = { text -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    isTranscribing = false
                    submitCommand(text)
                }
            }},
            onStatus = { status -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    when {
                        status.startsWith("Micrófono listo") -> {
                            NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, status)
                            if (!isSpeaking && !isThinking) {
                                NikoRuntimeState.setResponse(applicationContext, "Decí LEO para hablar conmigo.")
                            }
                        }
                        status.startsWith("Te escucho") || status.startsWith("LEO detectado") -> {
                            NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.READY, status)
                            if (!isSpeaking && !isThinking) {
                                NikoRuntimeState.setResponse(applicationContext, "Te escucho.")
                            }
                        }
                        else -> NikoRuntimeState.setInputStatus(applicationContext, status)
                    }
                }
            }},
            onError = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    NikoRuntimeState.setInput(applicationContext, NikoRuntimeState.InputState.ERROR, error)
                }
            }},
            onFatal = { error -> serviceScope.launch {
                if (!destroyed && epoch == localVoiceEpoch) {
                    platformVoiceDisabledForSession = true
                    ++localVoiceEpoch
                    localVoiceActive = false
                    platformVoice?.stop()
                    platformVoice = null
                    NikoRuntimeState.setInput(
                        applicationContext,
                        NikoRuntimeState.InputState.PREPARING,
                        "$error Probando el motor local de respaldo…",
                    )
                    ensureVoiceListening(250L)
                }
            }},
        )
        platformVoice = engine
        localVoice = null
        val started = engine.start()
        if (!started) {
            platformVoiceDisabledForSession = true
            platformVoice = null
            engine.stop()
            return false
        }

        localVoiceActive = true
        initializationFailure = null
        voiceRecovery.started(SystemClock.elapsedRealtime())
        isListening = false
        NikoRuntimeState.setInput(
            applicationContext,
            NikoRuntimeState.InputState.PREPARING,
            "Abriendo micrófono con el servicio de voz de Android…",
        )
        NikoRuntimeState.setResponse(applicationContext, "Preparando el micrófono de LEO…")
        updateVisualState()
        prewarmAdaptiveLearning()
        return true
    }

    private suspend fun stopActiveVoiceAndAwait(): Boolean {
        val platform = platformVoice
        val native = localVoice
        platformVoice = null
        localVoice = null
        val platformReleased = platform?.stopAndAwait() ?: true
        val nativeReleased = withContext(Dispatchers.IO) { native?.stopAndAwait() ?: true }
        return platformReleased && nativeReleased
    }

    private fun stopActiveVoice() {
        platformVoice?.stop()
        platformVoice = null
        localVoice?.stop()
        localVoice = null
    }

    private fun setActiveVoiceBusy(value: Boolean) {
        platformVoice?.setAssistantBusy(value)
        localVoice?.setAssistantBusy(value)
    }

    private fun cancelActiveVoiceConversation() {
        platformVoice?.cancelConversation()
        localVoice?.cancelConversation()
    }

    private fun finishActiveVoiceTurn() {
        platformVoice?.finishTurn()
        localVoice?.finishTurn()
    }

    private fun setActiveVoiceSpeaking(value: Boolean, continueCommand: Boolean = false) {
        platformVoice?.setAssistantSpeaking(value, continueCommand)
        localVoice?.setAssistantSpeaking(value, continueCommand)
    }

    private fun activeVoiceMicrophoneSilenced(): Boolean =
        platformVoice?.isMicrophoneSilenced == true || localVoice?.isMicrophoneSilenced == true

    private suspend fun startLocalVoiceIfReady(): Boolean {
        if (CaptureGate.held) return false
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
            platformVoice = null
            prewarmAdaptiveLearning()
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
        stopActiveVoice()
        inputUnavailable(error)
        ensureVoiceListening(voiceRecovery.nextDelayMillis(SystemClock.elapsedRealtime()))
    }

    /** Main-thread turn ownership; canceled jobs must never reset a newer command window. */
    private fun interruptCurrentTurn() {
        if (destroyed) return
        conversationWindowJob?.cancel()
        agent.interrupt()
        RobotMotionBus.clear()
        LeoVoiceDiagnostics.cancelResponseTiming()
        progressiveSpeech?.cancel()
        progressiveSpeech = null
        commandJob?.cancel()
        commandJob = null
        speechTimeout?.cancel()
        speechTimeout = null
        continueAfterSpeech = false
        isThinking = false
        isSpeaking = false
        isTranscribing = false
        setActiveVoiceBusy(false)
        NikoRuntimeState.setSearching(applicationContext, false)
        updateVisualState()
    }

    private fun submitCommand(text: String) {
        if (destroyed) return
        conversationWindowJob?.cancel()
        companionInitiative.interacted(System.currentTimeMillis(), com.niko.assistant.memory.MemoryLearning.key(text) in setOf("no", "mejor no", "ahora no"))
        initiativeStore.save(companionInitiative.state)
        VoiceControl.parse(text)?.let { control ->
            companionInitiative.silence(System.currentTimeMillis())
            initiativeStore.save(companionInitiative.state)
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
            cancelActiveVoiceConversation()
            isListening = false
            NikoRuntimeState.setHeard(applicationContext, text)
            if (control == VoiceControl.DEACTIVATE) {
                NikoVoiceSettings.setEnabled(this, false)
                ++localVoiceEpoch
                localVoiceActive = false
                stopActiveVoice()
                NikoRuntimeState.setResponse(applicationContext, "LEO desactivado. Podés activarme de nuevo desde la app.")
                stopSelf()
            } else {
                NikoRuntimeState.setResponse(applicationContext, "Detenido. Decí LEO cuando me necesités.")
            }
            updateVisualState()
            return
        }
        if (isSpeaking || isThinking || commandJob?.isActive == true) {
            interruptCurrentTurn()
            LeoRealtimeTurnBus.interruptSpeech()
        }
        val turn = agent.begin(text)
        val epoch = turn.generation
        LeoVoiceDiagnostics.recordResponseStarted()
        isListening = false
        isThinking = true
        NikoRuntimeState.setResponse(applicationContext, "Procesando tu petición…")
        isTranscribing = false
        setActiveVoiceBusy(true)
        NikoRuntimeState.setHeard(applicationContext, text)
        revealNikoOnLockScreen()
        updateVisualState()
        commandJob = serviceScope.launch {
            try {
                val completed = withTimeoutOrNull(COMMAND_EXECUTION_TIMEOUT_MS) {
                    withContext(turn) { handleCommand(text) }
                    true
                } == true
                if (!completed && epoch == turnEpoch && !destroyed) {
                    progressiveSpeech?.cancel()
                    progressiveSpeech = null
                    LeoRealtimeTurnBus.interruptSpeech()
                    speakResponse("La orden tardó demasiado. Ya liberé el procesamiento; decime de nuevo qué querés hacer.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (epoch == turnEpoch) {
                    progressiveSpeech?.cancel()
                    progressiveSpeech = null
                    LeoRealtimeTurnBus.interruptSpeech()
                    speakResponse("No pude completar eso. Volvé a llamarme y lo intentamos.")
                }
            } finally {
                if (epoch == turnEpoch && !destroyed) {
                    isThinking = false
                    if (!isSpeaking) {
                        if (continueAfterSpeech && localVoiceActive) {
                            scheduleConversationWindow()
                            isListening = !activeVoiceMicrophoneSilenced()
                        } else finishTurn()
                    }
                    updateVisualState()
                }
            }
        }
    }

    private fun finishTurn() {
        finishActiveVoiceTurn()
        isListening = false
    }

    private suspend fun handleCommand(rawText: String) {
        agent.turns.checkpoint()
        when (val confirmation = agent.confirmation(rawText)) {
            is com.niko.assistant.agent.LeoAgentRuntime.ConfirmationReply.Approved -> {
                if (confirmation.command == AssistantCommand.ClearMemory) {
                    clearLocalMemory()
                    agent.conversation.clear()
                    initiativeStore.clear()
                    companionInitiative.restore(com.niko.assistant.proactive.LeoInitiativeEngine.State())
                    speakResponse("Borré mi memoria local.")
                    return
                }
            }
            com.niko.assistant.agent.LeoAgentRuntime.ConfirmationReply.Rejected -> {
                speakResponse("De acuerdo. Conservé tu memoria.")
                return
            }
            com.niko.assistant.agent.LeoAgentRuntime.ConfirmationReply.None -> Unit
        }
        // Persist user before assistant for every route; no detached history writer can reorder turns.
        withContext(Dispatchers.IO) { memory.rememberUserTurn(rawText) }
        agent.turns.checkpoint()
        val initiativeOrder = rawText.lowercase(java.util.Locale.ROOT).trim().trimEnd('.', '!', '?')
            .replace("conversación", "conversacion").replace("espontánea", "espontanea")
        when (initiativeOrder) {
            "activa conversacion espontanea", "desactiva conversacion espontanea" -> {
                val enabled = initiativeOrder.startsWith("activa ")
                NikoAiSettings.setCompanionInitiative(applicationContext, enabled)
                speakResponse(if (enabled) "De acuerdo. A veces iniciaré una charla cuando estés aquí y no estés ocupado."
                    else "Listo. Voy a esperar a que vos me hablés.")
                return
            }
        }
        val correctedText = InteractionCorrection.correctedText(rawText)
        val text = correctedText ?: rawText
        val correctionAlias = if (correctedText != null) lastTrainableUtterance else null
        lastTrainableUtterance = text
        replyProsody = SpeechProsody.forInput(text)

        if (com.niko.assistant.devicecontrol.LeoVisionContext.isExplicitScreenRequest(text)) {
            agent.conversation.tool("screen_accessibility")
            val capture = withContext(Dispatchers.IO) { com.niko.assistant.devicecontrol.NikoVisualContext.capture() }
            agent.turns.checkpoint()
            val observation = capture.observation
            val answer = if (observation == null) capture.problem ?: "No recibí una observación de pantalla."
                else webClient.describeObservation(text, observation)
            speakResponse(answer)
            return
        }

        // Ruta inmediata para acciones claras del teléfono. No toca el motor de escucha.
        // Antes estas órdenes esperaban memoria/IA/planificador aunque LocalBrain ya sabía
        // exactamente qué hacer, por eso la UI podía quedarse en "Procesando tu petición…".
        if (executeDeterministicLocalAction(text, correctionAlias)) return

        // Animaciones y cálculos también son 100% locales: deben responder antes de abrir
        // memoria, red o cualquier componente pesado.
        RobotMotion.parse(text)?.let { motion ->
            learnIntent(text, LearnedIntent.ACTION, correctionAlias)
            RobotMotionBus.perform(motion)
            speakResponse(when (motion) {
                RobotMotion.DANCE -> "Va, mirá cómo bailo."
                RobotMotion.JUMP -> "¡Ahí va un salto!"
                RobotMotion.SPIN -> "¡Doy una vuelta!"
                RobotMotion.WAVE -> "¡Hola! Aquí estoy con vos."
            })
            return
        }
        NikoMathEngine.solve(text)?.let {
            learnIntent(text, LearnedIntent.ACTION, correctionAlias)
            speakResponse("El resultado es $it.")
            return
        }

        val generatedTool = brain.understandMany(text).singleOrNull() as? AssistantCommand.GenerateTool
        if (generatedTool != null) {
            learnIntent(text, LearnedIntent.ACTION, correctionAlias)
            val response = executeDirectCommand(generatedTool) ?: "No pude crear la herramienta."
            speakResponse(response)
            return
        }

        // Memoria y conversación quedan después de todas las acciones inmediatas.
        // Tool transformations are local and must not become web research requests.
        val directTool = com.niko.assistant.brain.LocalBrain().understandMany(text).singleOrNull()
        if (directTool is AssistantCommand.OpenAppByName &&
            (directTool.name.startsWith("NIKO_TOOL") || directTool.name.startsWith("LEO_MUSIC_QUERY:"))) {
            speakResponse(executor.openAppByName(directTool.name).spokenMessage)
            return
        }
        WebQueryRouter.explicitQuery(text)?.takeIf { AutonomousResearch.allowedFor(text) }?.let { query ->
            rememberCorrectedAction(correctionAlias, listOf(AssistantCommand.SearchWeb(query)))
            learnIntent(text, LearnedIntent.SEARCH, correctionAlias)
            speakResearchResponse(query, researchReply(query))
            return
        }
        if (NikoAiSettings.autoResearch(applicationContext) && AutonomousResearch.allowedFor(text) &&
            agent.conversation.shouldResearchFollowUp(text)) {
            speakResearchResponse(text, researchReply(text))
            return
        }
        val learningEnabled = NikoAiSettings.adaptiveLearning(applicationContext)
        val identityReply = com.niko.assistant.ai.NikoIdentity.replyTo(
            text,
            adaptiveLearningEnabled = learningEnabled,
        )
        if (identityReply != null) {
            val response = if (com.niko.assistant.ai.NikoIdentity.isLearningQuestion(text)) {
                val stats = learningStats()
                com.niko.assistant.ai.NikoIdentity.replyTo(
                    text,
                    adaptiveLearningEnabled = learningEnabled,
                    trainingUpdates = stats.updates,
                    learnedCorrections = stats.corrections,
                ) ?: identityReply
            } else identityReply
            learnIntent(text, LearnedIntent.CONVERSATION, correctionAlias)
            speakResponse(response)
            return
        }
        withContext(Dispatchers.IO) { memory.learnExplicitly(text) }?.let {
            learnIntent(text, LearnedIntent.MEMORY, correctionAlias)
            speakResponse(it); return
        }
        val learnedCommands = if (learningEnabled) withContext(Dispatchers.IO) {
            runCatching { learnedActionStore.resolve(text) }.getOrNull()
        }?.let(semanticActions::parseDsl)?.takeIf { it.isNotEmpty() } else null
        val commands = learnedCommands ?: semanticActions.resolveMany(text)
        rememberCorrectedAction(correctionAlias, commands)
        if (commands.size > 1) {
            learnIntent(
                text,
                if (commands.all { it is AssistantCommand.SearchWeb }) LearnedIntent.SEARCH else LearnedIntent.ACTION,
                correctionAlias,
            )
            val responses = mutableListOf<String>()
            val stepReplies = mutableListOf<NikoAiReply>()
            taskExecutor.run(text, commands) { command ->
                agent.turns.checkpoint()
                memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
                if (command is AssistantCommand.SearchWeb) {
                    learnIntent(command.query, LearnedIntent.SEARCH)
                    val answer = researchReply(command.query, openBrowser = true)
                    responses.add(answer.text)
                    stepReplies.add(answer)
                    return@run answer.text
                }
                val direct = executeDirectCommand(command)
                if (!direct.isNullOrBlank()) {
                    responses.add(direct)
                    stepReplies.add(NikoAiReply(direct, false, emptyList()))
                    if (lastCommandSucceeded) withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
                }
                delay(120L)
                direct.orEmpty()
            }
            val answer = responses.joinToString(" ").ifBlank { "No entendí qué acciones querés que haga." }
            val combined = com.niko.assistant.ai.ResearchCitationPolicy.combine(stepReplies)
            if (!combined.webUsed) speakResponse(answer)
            else speakResearchResponse(text, combined)
            return
        }
        val command = commands.firstOrNull() ?: AssistantCommand.Unknown(text)
        if (command == AssistantCommand.ClearMemory) { speakResponse(requestMemoryDeletion()); return }
        memory.rememberCommand(command); proactiveScheduler.maybeSchedule(command)
        if (command is AssistantCommand.SearchWeb) {
            learnIntent(text, LearnedIntent.SEARCH, correctionAlias)
            speakResearchResponse(command.query, researchReply(command.query, openBrowser = true)); return
        }
        if (command is AssistantCommand.Unknown) {
            if (NikoUiTaskPolicy.looksLikeExplicitUiTask(text)) {
                learnIntent(text, LearnedIntent.ACTION, correctionAlias)
                speakResponse(executeDirectCommand(AssistantCommand.AutomateUi(text)) ?: "No pude iniciar la automatización.")
                return
            }
            withContext(Dispatchers.IO) { memory.personalReply(text) }?.let {
                learnIntent(text, LearnedIntent.MEMORY, correctionAlias); speakResponse(it); return
            }
            val prediction = predictIntent(text)
            val remoteContext = agent.conversation.context() + "\n" + withContext(Dispatchers.IO) { memory.contextForAi(false, text) }
            val history = memory.historyForAi(text)
            val streamedText = StringBuilder()
            var lastPreviewAt = 0L
            val answer = ConversationCoordinator.reply(
                message = text,
                localFirst = NikoAiSettings.localFirst(applicationContext),
                autoResearch = NikoAiSettings.autoResearch(applicationContext),
                learnedSearch = prediction?.let { it.reliable && it.intent == LearnedIntent.SEARCH } == true,
                local = {
                    val context = withContext(Dispatchers.IO) { memory.contextForAi(currentMessage = text) }
                    localLlm.reply(text, agent.conversation.context() + "\n" + context)
                },
                cloud = { requireSources ->
                    if (requireSources) researchReply(text)
                    else if (webClient.isConfigured) webClient.reply(text, remoteContext, false, history) { delta ->
                        agent.turns.checkpoint()
                        if (!destroyed) {
                            val queue = progressiveSpeech ?: ProgressiveSpeech().also { progressiveSpeech = it }
                            if (delta.isNotBlank()) LeoVoiceDiagnostics.recordResponseText()
                            streamedText.append(delta)
                            val now = SystemClock.elapsedRealtime()
                            if (lastPreviewAt == 0L || now - lastPreviewAt >= 80L) {
                                NikoRuntimeState.previewResponse(streamedText.toString())
                                lastPreviewAt = now
                            }
                            queue.append(delta)
                            if (!isSpeaking) playNextProgressivePhrase()
                        }
                    }
                    else null
                },
                fallback = { withContext(Dispatchers.IO) {
                    val error = if (AutonomousResearch.offlineOnly(text)) localLlm.lastError else webClient.lastError ?: localLlm.lastError
                    fallbackConversation.reply(text, memory, error)
                } },
            )
            agent.turns.checkpoint()
            val learnedLabel = if (answer.webUsed || WebQueryRouter.needsCurrentInformation(text) && AutonomousResearch.allowedFor(text)) {
                LearnedIntent.SEARCH
            } else LearnedIntent.CONVERSATION
            learnIntent(text, learnedLabel, correctionAlias)
            if (looksLikeCapabilityRequest(text)) {
                val plan = codeAgent.analyze(text)
                codeAgent.registerNativeProposal(plan.capability, "${plan.strategy}: ${plan.explanation}", answer.text, com.niko.assistant.BuildConfig.VERSION_NAME)
            }
            if (progressiveSpeech != null) {
                progressiveSpeech?.finish()
                agent.conversation.reply(answer)
                NikoRuntimeState.setAiResponse(applicationContext, answer.text, answer.webUsed, answer.sources)
                if (!isSpeaking) playNextProgressivePhrase()
                withContext(Dispatchers.IO) { memory.rememberAssistantTurn(answer.text) }
            } else speakResearchResponse(text, answer)
            return
        }
        learnIntent(
            text,
            when (command) {
                AssistantCommand.Greeting -> LearnedIntent.CONVERSATION
                AssistantCommand.MemorySummary -> LearnedIntent.MEMORY
                else -> LearnedIntent.ACTION
            },
            correctionAlias,
        )
        val direct = executeDirectCommand(command) ?: "Listo."
        speakResponse(direct)
        if (lastCommandSucceeded) withContext(Dispatchers.IO) { memory.rememberCompletedCommand(command, direct) }
    }

    private suspend fun executeDeterministicLocalAction(
        text: String,
        correctionAlias: String?,
    ): Boolean {
        val commands = brain.understandMany(text)
        if (commands.isEmpty() || commands.any {
                it is AssistantCommand.Unknown || it is AssistantCommand.SearchWeb || it is AssistantCommand.GenerateTool
            }) {
            return false
        }

        val responses = mutableListOf<String>()
        val completedCommands = mutableListOf<Pair<AssistantCommand, String>>()
        for (command in commands) {
            when (command) {
                AssistantCommand.ClearMemory -> {
                    responses += requestMemoryDeletion()
                }
                AssistantCommand.MemorySummary -> {
                    responses += withContext(Dispatchers.IO) { memory.describeLearnedPatterns() }
                }
                else -> {
                    val result = executeDirectCommand(command)
                    if (!result.isNullOrBlank()) {
                        responses += result
                        if (lastCommandSucceeded) completedCommands += command to result
                    }
                }
            }
        }

        if (responses.isEmpty()) return false

        rememberCorrectedAction(correctionAlias, commands)
        learnIntent(
            text,
            if (commands.all { it == AssistantCommand.Greeting }) LearnedIntent.CONVERSATION else LearnedIntent.ACTION,
            correctionAlias,
        )

        // Structured persistence finishes inside the owning turn, so a later clear cannot race it.
        withContext(Dispatchers.IO) {
            completedCommands.forEach { (command, result) ->
                memory.rememberCommand(command)
                memory.rememberCompletedCommand(command, result)
            }
        }
        agent.turns.checkpoint()

        speakResponse(responses.joinToString(" "))
        return true
    }

    private suspend fun predictIntent(text: String): OnlineIntentNetwork.Prediction? = withContext(Dispatchers.IO) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return@withContext null
        val loaded = adaptiveNetwork ?: run {
            prewarmAdaptiveLearning()
            return@withContext null
        }
        learningMutex.withLock {
            try {
                loaded.predict(text)
            } catch (_: Exception) {
                adaptiveUnavailable = true
                NikoRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
                null
            }
        }
    }

    private fun prewarmAdaptiveLearning() {
        if (destroyed || adaptiveUnavailable || adaptiveNetwork != null ||
            !NikoAiSettings.adaptiveLearning(applicationContext) || !adaptiveLoading.compareAndSet(false, true)
        ) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                learningMutex.withLock {
                    if (adaptiveNetwork == null && !adaptiveUnavailable) adaptiveNetwork = adaptiveStore.load()
                }
            } catch (_: Exception) {
                adaptiveUnavailable = true
                NikoRuntimeState.setInputStatus(applicationContext, "Aprendizaje no disponible; conservé los datos para recuperación.")
            } finally {
                adaptiveLoading.set(false)
            }
        }
    }

    /** Training and fsync must never delay command execution or the first spoken audio. */
    private fun learnIntent(text: String, intent: LearnedIntent, correctionAlias: String? = null) {
        if (!NikoAiSettings.adaptiveLearning(applicationContext) || adaptiveUnavailable) return
        val epoch = learningEpoch
        serviceScope.launch(Dispatchers.IO) {
            learningMutex.withLock {
                if (epoch != learningEpoch) return@withLock
                try {
                    val network = adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it }
                    network.learn(text, intent)
                    correctionAlias?.takeIf { it != text }?.let { network.learn(it, intent) }
                    adaptiveStore.save(network)
                } catch (_: Exception) {
                    adaptiveUnavailable = true
                    NikoRuntimeState.setInputStatus(applicationContext, "No pude guardar el aprendizaje. Las órdenes siguen disponibles.")
                }
            }
        }
    }

    private suspend fun clearLocalMemory() {
        ++learningEpoch
        withContext(Dispatchers.IO) {
            learningMutex.withLock {
                memory.clearAll()
                adaptiveStore.clear()
                learnedActionStore.clear()
                adaptiveNetwork = null
                adaptiveUnavailable = false
                lastTrainableUtterance = null
            }
        }
    }

    private fun rememberCorrectedAction(alias: String?, commands: List<AssistantCommand>) {
        alias ?: return
        if (!NikoAiSettings.adaptiveLearning(applicationContext)) return
        val dsl = LearnedActionCodec.encode(commands) ?: return
        val epoch = learningEpoch
        serviceScope.launch(Dispatchers.IO) {
            learningMutex.withLock {
                if (epoch != learningEpoch) return@withLock
                runCatching { learnedActionStore.remember(alias, dsl) }
                    .onFailure { NikoRuntimeState.setInputStatus(applicationContext, "No pude guardar esa corrección; el resto del aprendizaje sigue activo.") }
            }
        }
    }

    private suspend fun learningStats(): LearningStats = withContext(Dispatchers.IO) {
        learningMutex.withLock {
            val network = runCatching { adaptiveNetwork ?: adaptiveStore.load().also { adaptiveNetwork = it } }.getOrNull()
            val corrections = runCatching { learnedActionStore.count() }.getOrDefault(0)
            LearningStats(network?.observations ?: 0L, corrections)
        }
    }

    private data class LearningStats(val updates: Long, val corrections: Int)

    private fun looksLikeCapabilityRequest(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return listOf(
            "aprende a", "aprendé a", "programate", "programáte", "prográmate", "mejorate", "mejoráte", "mejórate",
            "agrega una funcion", "agregá una función", "agrega una función", "crea una funcion", "creá una función",
            "quiero que puedas", "necesito que puedas", "haz que puedas", "hacé que puedas",
        ).any(value::contains)
    }

    private suspend fun createGeneratedTool(request: String): com.niko.assistant.skills.LeoSkillResult {
        NikoRuntimeState.setResponse(applicationContext, "Diseñando una herramienta para vos…")
        val spec = withContext(Dispatchers.IO) { generatedToolPlanner.generate(request) }
        agent.turns.checkpoint()
        GeneratedToolStore.save(applicationContext, spec)
        val opened = executor.openAppByName("herramienta generada")
        val message = if (opened.success) {
            "Listo. Me convertí en ${spec.title.lowercase(Locale.forLanguageTag("es-NI"))}."
        } else {
            "Creé ${spec.title}, pero Android no me dejó abrirla ahora mismo."
        }
        return com.niko.assistant.skills.LeoSkillResult(message, opened.success)
    }

    private fun requestMemoryDeletion(): String {
        agent.requestConfirmation(AssistantCommand.ClearMemory)
        return "Voy a borrar tu memoria local, incluidas preferencias y recuerdos. ¿Confirmás el borrado?"
    }

    private suspend fun executeDirectCommand(command: AssistantCommand): String? {
        agent.turns.checkpoint()
        lastCommandSucceeded = false
        if (command == AssistantCommand.ClearMemory) return requestMemoryDeletion()
        val skill = skillRegistry.select(command) ?: return null
        agent.conversation.tool(skill.descriptor.name)
        val result = skillRegistry.execute(command)
        agent.turns.checkpoint()
        lastCommandSucceeded = result?.success == true
        return result?.message
    }

    private suspend fun dispatchDirectCommand(command: AssistantCommand): com.niko.assistant.skills.LeoSkillResult? = when (command) {
        AssistantCommand.Greeting -> localResult("Aquí estoy. Decime.")
        AssistantCommand.TellTime -> localResult("Son las ${SimpleDateFormat("h:mm a", Locale.forLanguageTag("es-NI")).format(Date())}.")
        AssistantCommand.OpenCamera -> executor.openCamera().skillResult()
        AssistantCommand.MemorySummary -> localResult(withContext(Dispatchers.IO) { memory.describeLearnedPatterns() })
        AssistantCommand.ClearMemory -> null
        is AssistantCommand.OpenApp -> executor.openApp(command.app).skillResult()
        is AssistantCommand.OpenAppByName -> executor.openAppByName(command.name).skillResult()
        is AssistantCommand.GenerateTool -> createGeneratedTool(command.request)
        is AssistantCommand.Dial -> executor.dial(command.number).skillResult()
        is AssistantCommand.ComposeMessage -> executor.composeMessage(command.number, command.message).skillResult()
        is AssistantCommand.WhatsAppMessage -> executor.whatsappMessage(command.number, command.message).skillResult()
        is AssistantCommand.PlaySpotify -> executor.playSpotify(command.query).skillResult()
        is AssistantCommand.SetAlarm -> executor.setAlarm(command.hour, command.minute, command.label).skillResult()
        is AssistantCommand.SetTimer -> executor.setTimer(command.seconds, command.label).skillResult()
        is AssistantCommand.OpenMaps -> executor.openMaps(command.query).skillResult()
        is AssistantCommand.SearchWeb -> null
        is AssistantCommand.ShareText -> executor.shareText(command.text).skillResult()
        is AssistantCommand.SetTorch -> executor.setTorch(command.enabled).skillResult()
        is AssistantCommand.SetVolume -> executor.setVolume(command.percent).skillResult()
        is AssistantCommand.AdjustVolume -> executor.adjustVolume(command.direction).skillResult()
        is AssistantCommand.SetBrightness -> executor.setBrightness(command.percent).skillResult()
        is AssistantCommand.OpenSystemPanel -> executor.openSystemPanel(command.panel).skillResult()
        is AssistantCommand.NavigateDevice -> executor.navigateDevice(command.destination).skillResult()
        is AssistantCommand.AutomateUi -> uiAutomation.run(command.task).let { com.niko.assistant.skills.LeoSkillResult(it.message, it.success) }
        AssistantCommand.BatteryStatus -> executor.batteryStatus().skillResult()
        is AssistantCommand.Vibrate -> executor.vibrate(command.milliseconds).skillResult()
        is AssistantCommand.SmartHomeControl -> smartHome.controlAsync(command.target, command.enabled).let { com.niko.assistant.skills.LeoSkillResult(it.spokenMessage, it.success) }
        AssistantCommand.OpenSmartHomeSettings -> executor.openSmartHomeSettings().skillResult()
        AssistantCommand.OpenAiSettings -> executor.openAiSettings().skillResult()
        is AssistantCommand.Unknown -> null
    }

    private fun localResult(message: String) = com.niko.assistant.skills.LeoSkillResult(message, true, verified = true)
    private fun com.niko.assistant.actions.ActionResult.skillResult() =
        com.niko.assistant.skills.LeoSkillResult(spokenMessage, success)

    private suspend fun researchReply(query: String, forceWeb: Boolean = true, openBrowser: Boolean = false): NikoAiReply {
        agent.turns.checkpoint()
        val contextualQuery = agent.conversation.researchQuery(query)
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
            val reply = webClient.reply(contextualQuery, context, forceWeb, memory.historyForAi(current))
            agent.turns.checkpoint()
            if (reply != null) agent.conversation.researched(contextualQuery, reply)
            when {
                reply == null -> unavailable(webClient.lastError ?: "No pude consultar Internet. Volvé a intentarlo.")
                forceWeb && !reply.webUsed -> reply
                else -> reply
            }
        } finally { if (researchEpoch == turnEpoch) NikoRuntimeState.setSearching(applicationContext, false) }
    }

    private suspend fun speakResearchResponse(question: String, reply: NikoAiReply) {
        agent.turns.checkpoint()
        LeoVoiceDiagnostics.recordResponseText()
        agent.conversation.reply(reply)
        val finalText = reply.text
        val evidenceNote = if (reply.webUsed) AutonomousResearch.evidenceNote(reply.sources.map { it.url }) else ""
        val displayed = if (evidenceNote.isBlank()) finalText else "$finalText\n\n$evidenceNote"
        NikoRuntimeState.setAiResponse(applicationContext, displayed, reply.webUsed, reply.sources)
        val spokenText = finalText
            .replace(Regex("\\s*\\[\\d+\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        speakOnly(spokenText.ifBlank { finalText })
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(finalText) }
    }

    private suspend fun speakResponse(text: String) {
        agent.turns.checkpoint()
        LeoVoiceDiagnostics.recordResponseText()
        agent.conversation.reply(NikoAiReply(text, false, emptyList()))
        NikoRuntimeState.setResponse(applicationContext, text)
        speakOnly(text)
        withContext(Dispatchers.IO) { memory.rememberAssistantTurn(text) }
    }
    private fun speakOnly(text: String, continueCommand: Boolean = false) {
        if (text.isBlank() || destroyed) return
        conversationWindowJob?.cancel()
        continueAfterSpeech = continueCommand || NikoAiSettings.companionInitiative(applicationContext)

        // Keep the primary recognizer listening for guarded interruptions during TTS.
        if (localVoiceActive) setActiveVoiceSpeaking(true, continueAfterSpeech)

        val systemReady = runCatching { platformTts.isReady }.getOrDefault(false)
        if (!systemReady) {
            isSpeaking = false
            NikoRuntimeState.setVoiceReady(applicationContext, false)
            NikoRuntimeState.setVoiceStatus(applicationContext, "Respuesta mostrada en pantalla · preparando voz del teléfono")
            if (localVoiceActive) setActiveVoiceSpeaking(false, continueAfterSpeech)
            if (continueAfterSpeech) {
                scheduleConversationWindow()
                isListening = localVoiceActive && !activeVoiceMicrophoneSilenced()
            } else if (!isThinking) {
                finishTurn()
            }
            updateVisualState()
            return
        }

        isSpeaking = true
        updateVisualState()
        speechTimeout?.cancel()
        speechTimeout = serviceScope.launch {
            delay((text.length * 110L + 5_000L).coerceIn(12_000L, 360_000L))
            if (!destroyed && isSpeaking) {
                platformTts.stop()
                onSpeakingChanged(false)
            }
        }

        NikoRuntimeState.setVoiceStatus(applicationContext, platformTts.voiceDescription)
        val queued = runCatching { platformTts.speak(text, replyProsody) }.getOrDefault(false)
        if (queued) {
            NikoRuntimeState.setVoiceReady(applicationContext, true)
        } else {
            NikoRuntimeState.setVoiceReady(applicationContext, false)
            NikoRuntimeState.setVoiceStatus(applicationContext, "Respuesta mostrada en pantalla · voz del teléfono no disponible")
            onSpeakingChanged(false)
        }
    }

    private fun playNextProgressivePhrase(): Boolean {
        val queue = progressiveSpeech ?: return false
        val phrase = queue.poll()
        if (phrase != null) { speakOnly(phrase); return true }
        if (queue.isDrained) progressiveSpeech = null
        return false
    }

    private fun onSpeakingChanged(speaking: Boolean) {
        serviceScope.launch {
            if (destroyed || (!speaking && !isSpeaking)) return@launch
            isSpeaking = speaking
            if (!speaking && playNextProgressivePhrase()) return@launch
            if (localVoiceActive) setActiveVoiceSpeaking(speaking, continueAfterSpeech)
            if (!speaking) {
                speechTimeout?.cancel()
                speechTimeout = null
                if (continueAfterSpeech) {
                    scheduleConversationWindow()
                    isListening = localVoiceActive && !activeVoiceMicrophoneSilenced()
                } else if (!isThinking) finishTurn()
            }
            updateVisualState()
        }
    }
    private fun scheduleConversationWindow() {
        conversationWindowJob?.cancel()
        if (!isThinking) setActiveVoiceBusy(false)
        conversationWindowJob = serviceScope.launch {
            delay(12_000L)
            if (!destroyed && !isSpeaking && !isThinking && !isTranscribing && commandJob?.isActive != true) {
                cancelActiveVoiceConversation()
                finishTurn()
                updateVisualState()
            }
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
        val params = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = bubblePrefs.getInt(KEY_BUBBLE_X, dp(DEFAULT_BUBBLE_X_DP)); y = bubblePrefs.getInt(KEY_BUBBLE_Y, dp(DEFAULT_BUBBLE_Y_DP)) }
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

    private fun resetBubblePosition() {
        bubblePrefs.edit().remove(KEY_BUBBLE_X).remove(KEY_BUBBLE_Y).apply()
        val params = bubbleParams ?: return
        params.x = dp(DEFAULT_BUBBLE_X_DP)
        params.y = dp(DEFAULT_BUBBLE_Y_DP)
        val view = bubbleView ?: return
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun saveBubblePosition(params: WindowManager.LayoutParams) { bubblePrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun acquireCpuWakeLock() { if (cpuWakeLock?.isHeld == true) return; cpuWakeLock = (getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NIKO:AlwaysOnWake")?.apply { setReferenceCounted(false); acquire() }) }
    private fun releaseCpuWakeLock() { cpuWakeLock?.let { if (it.isHeld) runCatching { it.release() } }; cpuWakeLock = null }

    companion object {
        private const val COMMAND_EXECUTION_TIMEOUT_MS = 25_000L
        private const val CHANNEL_ID = UpgradeIdentity.assistantChannel
        private const val WAKE_CHANNEL_ID = UpgradeIdentity.wakeChannel
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_NOTIFICATION_ID = 2002
        private const val WAKE_REQUEST_CODE = 2102
        private const val BUBBLE_PREFS = UpgradeIdentity.bubblePreferences
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val DEFAULT_BUBBLE_X_DP = 18
        private const val DEFAULT_BUBBLE_Y_DP = 220
        const val ACTION_START = UpgradeIdentity.ACTION_START
        const val ACTION_STOP = UpgradeIdentity.ACTION_STOP
        const val ACTION_SHOW_BUBBLE = UpgradeIdentity.ACTION_SHOW_BUBBLE
        const val ACTION_HIDE_BUBBLE = UpgradeIdentity.ACTION_HIDE_BUBBLE
        const val ACTION_REFRESH_BUBBLE = UpgradeIdentity.ACTION_REFRESH_BUBBLE
        const val ACTION_RESET_BUBBLE = UpgradeIdentity.ACTION_RESET_BUBBLE
    }
}
